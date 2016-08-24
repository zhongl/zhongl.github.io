---
layout: post
date: 2015-01-09 12:00:00 +0800
title: '加速 SBT 下载依赖库的速度'
tags: [sbt, scala, proxy]
excerpt_separator: <!--more-->
---

根据 [SBT][sbt] 的官网文档中 [Proxy Repositories][proxy_repos] 部分的描述, 可以通过改变 `repositories` 的 `url` 列表来优化.

<!--more-->

### 配置国内代理库

感谢 [OSChina][osc] 提供了 [Maven Center][mvn] 的镜像, 配置添加它有助于提升下载速度.

```
[repositories]
  local
  oschina:http://maven.oschina.net/content/groups/public/
```

> 若你知道其他更快的镜像库, 同上配置.
> 一般互联网企业部署了供内部使用的镜像库(如 nexus ), 也可以配置于此.

### 兼容 `Ivy` 路径布局

大多数中心仓库(repository)是 [Maven][mvn] 的路径布局, 这就导致 [SBT][sbt] 的插件和部分 [Ivy][ivy] 依赖无法从其下载.

```
[repositories]
  local
  oschina:http://maven.oschina.net/content/groups/public/
  oschina-ivy:http://maven.oschina.net/content/groups/public/, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
```

###  精简 `url` 列表

远程仓库越多越全, 可以基本避免下载不到的问题. 但是, 也可能让下载的时间更长, 让你不愿在等待而放弃下载.

因为, 下载过程中 [SBT][sbt] 会串行的 "询问" 列表中所有的远程仓库, 无论是否找得到.

当依赖树越大, 整个下载的过程就更漫长. 若再遇到响应慢的仓库, 情况恶化的令人发指.

推荐列表策略是:

- 本地仓库
- 国内(或内网)镜像仓库
- 国外官方仓库, 通常 `#` 注释掉, 待上面不管用时, 去掉 `#` 再做尝试


## 上面办法不管用

建议使用你熟悉的网络嗅探手段查清具体原因, 对症下药了.

### 一个可行的方案

1. 下载 HTTP 代理工具 [mitmproxy][mproxy], 并运行它
2. 启动 [SBT][sbt]时, 附加参数 `-Dhttp.proxyHost=loalhost -Dhttp.proxyPort=8080`, 这会将 [SBT][sbt] 所有的 HTTP 请求经由 [mitmproxy][mproxy]转发
3. 通过 [mitmproxy][mproxy] 来分析 HTTP 请求失败的具体原因


[sbt]: http://www.scala-sbt.org/
[scala]: http://www.scala-lang.org/
[mproxy]: https://mitmproxy.org/
[proxy_repos]: http://www.scala-sbt.org/0.13/docs/Proxy-Repositories.html
[osc]: http://www.oschina.net
[mvn]: http://search.maven.org
[ivy]: http://ant.apache.org/ivy/


### 此外你还可以参考

http://afoo.me/posts/2014-11-05-how-make-sbt-jump-over-GFW.html

The end.
