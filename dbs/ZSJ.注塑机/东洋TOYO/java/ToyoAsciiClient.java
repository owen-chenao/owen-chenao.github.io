/**
 * 东洋注塑机 ASCII 协议客户端（ToyoMonitor 同款）。
 * 请求 {@code ":",35,C0\r}，应答 {@code ":",36,<csv>,校验\r}。
 * 点表地址 {@code B.N} → 字段下标 {@code 1+N}（36.xxx 已现场验证）。
 * 机台通常只允许 1 路 TCP，本类用锁串行化读数。
 */
public class ToyoAsciiClient {
    private static final byte[] POLL_REQUEST = new byte[]{
            0x22, 0x3A, 0x22, 0x2C, 0x33, 0x35, 0x2C, 0x43, 0x30, 0x0D
    };
    private String host;
    private int port;
    private int connectTimeoutMs;
    private int ioTimeoutMs;
    private long lockWaitMs;
    private String pointsResource;
    private volatile List<PointDef> points = Collections.emptyList();

    @PostConstruct
    public void init() {
        try {
            points = loadPoints(pointsResource);
            log.info("[ToyoAscii] 点表加载完成 count={} resource={}", points.size(), pointsResource);
        } catch (Exception e) {
            log.warn("[ToyoAscii] 点表加载失败 resource={} err={}", pointsResource, e.getMessage());
            points = Collections.emptyList();
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public List<PointDef> getPoints() {
        return points;
    }

    /**
     * 读一次全量点位。host/port 为空则用配置默认值。
     */
    public PollResult poll(String overrideHost, Integer overridePort) {
        String useHost = (overrideHost == null || overrideHost.trim().isEmpty()) ? host : overrideHost.trim();
        int usePort = (overridePort == null || overridePort <= 0) ? port : overridePort;

        boolean locked;
        try {
            locked = lock.tryLock(lockWaitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PollResult.fail(useHost, usePort, "获取读锁被中断");
        }
        if (!locked) {
            return PollResult.fail(useHost, usePort, "机台忙（读锁等待超时），请稍后重试；若 ToyoMonitor 正占用也会失败");
        }
        try {
            return doPoll(useHost, usePort);
        } finally {
            lock.unlock();
        }
    }

    private PollResult doPoll(String useHost, int usePort) {
        long t0 = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(useHost, usePort), connectTimeoutMs);
            socket.setSoTimeout(ioTimeoutMs);
            OutputStream os = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            os.write(POLL_REQUEST);
            os.flush();

            byte[] frame = readUntilCr(in);
            String text = new String(frame, Charset.forName("ISO-8859-1"));
            List<String> fields = splitCsv(text.trim());

            List<Map<String, Object>> pointValues = new ArrayList<>(points.size());
            for (PointDef p : points) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", p.name);
                row.put("alias", p.alias);
                row.put("addr", p.deviceAddr);
                row.put("dataType", p.dataType);
                Integer idx = resolveIndex(p);
                row.put("index", idx);
                if (idx == null) {
                    row.put("value", null);
                    row.put("ok", false);
                    row.put("error", "地址无法解析");
                } else if (idx < 0 || idx >= fields.size()) {
                    row.put("value", null);
                    row.put("ok", false);
                    row.put("error", "字段越界");
                } else {
                    row.put("value", fields.get(idx).trim());
                    row.put("ok", true);
                }
                pointValues.add(row);
            }

            PollResult result = new PollResult();
            result.success = true;
            result.host = useHost;
            result.port = usePort;
            result.costMs = System.currentTimeMillis() - t0;
            result.frameBytes = frame.length;
            result.fieldCount = fields.size();
            result.command = fields.size() > 1 ? fields.get(1).trim() : "";
            result.points = pointValues;
            result.rawFields = fields;
            return result;
        } catch (Exception e) {
            log.warn("[ToyoAscii] 读数失败 {}:{} {}", useHost, usePort, e.getMessage());
            return PollResult.fail(useHost, usePort, e.getMessage());
        }
    }

    static Integer resolveIndex(PointDef p) {
        if (p.block == null || p.offset == null) {
            return null;
        }
        return 1 + p.offset;
    }

    static byte[] readUntilCr(InputStream in) throws Exception {
        ByteArrayOutputStream ms = new ByteArrayOutputStream(2048);
        while (true) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            ms.write(b);
            if (b == '\r') {
                break;
            }
        }
        return ms.toByteArray();
    }

    static List<String> splitCsv(String body) {
        String s = body;
        if (s.endsWith("\r") || s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
        }
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                cur.append(c);
            } else if (c == ',' && !inQuote) {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    List<PointDef> loadPoints(String location) throws Exception {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("点表不存在: " + location);
        }
        List<PointDef> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) {
                return list;
            }
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                List<String> cols = splitCsv(line);
                if (cols.size() < 6) {
                    continue;
                }
                String name = cols.get(0).trim();
                int alias = Integer.parseInt(cols.get(1).trim());
                String addr = cols.get(4).trim().replace("\"", "");
                String type = cols.get(5).trim();
                Integer block = null;
                Integer offset = null;
                int dot = addr.indexOf('.');
                if (dot > 0) {
                    try {
                        block = Integer.parseInt(addr.substring(0, dot));
                        offset = Integer.parseInt(addr.substring(dot + 1));
                    } catch (NumberFormatException ignore) {
                        // skip bad addr
                    }
                }
                list.add(new PointDef(name, alias, addr, type, block, offset));
            }
        }
        return list;
    }

    public static final class PointDef {
        public final String name;
        public final int alias;
        public final String deviceAddr;
        public final String dataType;
        public final Integer block;
        public final Integer offset;

        PointDef(String name, int alias, String deviceAddr, String dataType, Integer block, Integer offset) {
            this.name = name;
            this.alias = alias;
            this.deviceAddr = deviceAddr;
            this.dataType = dataType;
            this.block = block;
            this.offset = offset;
        }
    }

    public static final class PollResult {
        public boolean success;
        public String message = "";
        public String host;
        public int port;
        public long costMs;
        public int frameBytes;
        public int fieldCount;
        public String command = "";
        public List<Map<String, Object>> points = Collections.emptyList();
        public List<String> rawFields = Collections.emptyList();

        static PollResult fail(String host, int port, String message) {
            PollResult r = new PollResult();
            r.success = false;
            r.host = host;
            r.port = port;
            r.message = message == null ? "" : message;
            return r;
        }

        public Map<String, Object> toMap(boolean includeRaw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", success);
            m.put("message", message);
            m.put("host", host);
            m.put("port", port);
            m.put("costMs", costMs);
            m.put("frameBytes", frameBytes);
            m.put("fieldCount", fieldCount);
            m.put("command", command);
            m.put("pointCount", points == null ? 0 : points.size());
            m.put("points", points);
            if (includeRaw) {
                m.put("rawFields", rawFields);
            }
            return m;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "PollResult{success=%s host=%s:%d points=%d msg=%s}",
                    success, host, port, points == null ? 0 : points.size(), message);
        }
    }
}
