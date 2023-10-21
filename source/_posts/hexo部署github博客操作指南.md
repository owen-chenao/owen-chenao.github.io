---
title: hexo部署github博客操作指南
---



# 安装部署到 Github 上时所使用的插件

```
npm install hexo-deployer-git --save
```

# 配置 hexo 的 _config.yml 文件

```
deploy:
  type: git
  repo: git@xx/xx.github.io.git
  branch: gh-pages
  message: xx
```

# 执行 hexo g -d

配置完成后，随便书写点内容，执行 hexo g -d，再去 Github 上的仓库内看看 gh-pages 分支是不是已经变成静态内容了

# 原理
执行 hexo g -d 操作时会在本地生成 public 静态代码和 .deploy_git 文件夹。
.deploy_git 和 public 的内容几乎一致，但 .deploy_git 多了 GitHub 所需的仓库信息与提交信息。
全部解析完后 hexo 会把 .deploy_git 文件夹内的全部内容推送到 GitHub 仓库中，再由 Github Pages 服务完成静态网站的解析。