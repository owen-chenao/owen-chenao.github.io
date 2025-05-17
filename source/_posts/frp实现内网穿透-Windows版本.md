---
title: frp实现内网穿透(Windows版本)
date: 2025-05-17 12:11:44
tags:
---

# [frp](https://github.com/fatedier/frp/blob/master/README_zh.md)

 frp 是一个专注于内网穿透的高性能的反向代理应用，支持 TCP、UDP、HTTP、HTTPS 等多种协议，且支持 P2P 通信。可以将内网服务以安全、便捷的方式通过具有公网 IP 节点的中转暴露到公网。 



github 下载地址：github.com/fatedier/frp/releases

公网、内网服务器都下载一份。




找到第一条，然后下载 frp_windwos_amd64.zip 这个 (amd64 是 64 系统，386 是 32 位系统，现在电脑基本 64 位了)，比如图片上的 frp_0.33.0_windows_amd64.zip

2、将 frp_0.33.0_windows_amd64.zip 解压至任意目录

3、进入解压目录这里所有 frpc 开头的文件都是客户端文件，所以全部删了，我们服务器只需要 frps 开头的文件

4、配置服务端（公网服务器）
我们打开 frps.ini（我用的 notepad++ 编辑器，记事本也可以）
注意配置文件不支持注释，请不要把汉字复制进去

```
[common]
bind_port = 7000           #与客户端绑定的进行通信的端口
vhost_http_port = 6081     #访问客户端web服务自定义的端口号
```


保存然后打开 cmd 进入当前目录（cmd 不会用自行百度），输入：frps.exe -c ./frps.ini，此时会提示网络防火墙安全警告，点允许

提示 start frps success 则服务启动成功

警告：云服务器一定要开放端口，不会开放自行百度，图中的 7000 是 frp 绑定的默认端口要打开，另外一个是 http（用不到可以删了）

至此服务端配置完成！
5、配置客户端（内网服务器），首先删掉 frps 开头文件文件，然后再进行配置，编辑 frpc.ini
注意配置文件不支持注释，请不要把汉字复制进去

```
[common]
server_addr = xx.xx.xx.xx   #公网服务器ip
server_port = 7000            #与服务端bind_port一致

[yclj]
type = tcp
local_ip = 127.0.0.1
local_port = 3389 # 3389是windows的远程连接端口
remote_port = 24567 # 远程服务器端口（自定义）
```



保存然后执行
windows 使用 cmd 或者 powershell 进入当前目录，执行

frpc.exe -c frpc.ini
6、访问方式
(1) 远程连接 windows bind_post 要绑定 3389，其他的不行
ip：xx.xx.xx.xx:24567 （这个是服务器开放的端口，映射本地的 3389）

三、结尾

1. 客户端连接服务端提示访问被拒绝，则需要服务端开放防火墙端口（学习的话全开就行了，生产环境除外）。




# 示例代码

## frpc.ini(客户端)`frpc.exe -c frpc.ini`

```
[common]
server_addr = 43.165.63.142
server_port = 7000

[yclj]
type = tcp
local_ip = 127.0.0.1
local_port = 80
remote_port = 80
```

## frps.ini(服务端)`frps.exe -c frps.ini`

```
[common]
bind_port = 7000
vhost_http_port = 6081
```

