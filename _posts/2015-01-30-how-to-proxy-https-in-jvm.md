---
layout: post
Date: 2015-01-30 12:00:00 +0800
title: '使用 mitmproxy 代理 JAVA 应用 HTTPS 请求'
tags: [java, https, proxy]
last_modified_at: 2016-08-25 15:20:00 +0800
---

## 背景问题

设置 `-Dhttps.proxyHost=localhost -Dhttps.proxyPort=8080`  使 JAVA 应用所有 HTTPS 请求经过 [mitmproxy][mp] 代理发出. 结果得到下面的错误:

[mp]: http://mitmproxy.org

> Server access Error: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested ...

<!--more-->

### 环境

```
Mac OSX 10.10.2
java version "1.7.0_55"
Java(TM) SE Runtime Environment (build 1.7.0_55-b13)
Java HotSpot(TM) 64-Bit Server VM (build 24.55-b03, mixed mode)
```

## 解决方案

将 [mitmproxy][mp] 的证书导入到 JDK 默认的 keystore 中:

```
sudo keytool -importcert -alias mitmproxy  \
-keystore <keystore_path> \
-storepass <password> \
-trustcacerts \
-file ~/.mitmproxy/mitmproxy-ca-cert.pem
```
> password 默认是 changeit


### 查明 keystore 路径

```
scala -e 'import java.net._; new URL("https://www.wacai.com").openConnection.asInstanceOf[HttpURLConnection].disconnect' -Djavax.net.debug=SSL | grep "trustStore is"
```

The end.
