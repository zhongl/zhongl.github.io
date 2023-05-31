---
layout:	post
date: 	2018-12-06 20:21:00 +0800
title: '用Wireshark诊断Java远程断点调试的诡异问题'
tags:	[jdb, wireshark]
---

> **特别强调**: 远程断点调试我是非常不推荐的做法, 原因有二:
> 
> 1. 调试机与远程服务之间的网络是否有稳定的低延迟表现决定了调试的效率;
> 1. 一旦成功断点, 则直接影响远程服务的行为表现, 无论是测试环境还是生产环境都会造成不可预料的后果.

说回正题.

在给远程服务添加好开启远程调试的启动参数重启之后, 我便启动 **IntellJ IDEA** 连接远程服务进行断点, 等待十多秒后 **IDEA** 提示我: `Error running 'remote': Unable to open debugger port (jdwp_server:5005): java.io.IOException 
`. 初步网络诊断后结果是, 远程服务(`jdwp_server`) 能够 **PING** 通, **Telnet** 表明**TCP** 连接创建无碍.

嗯, 问题诡异了.

<!--more-->

> **启动参数**
> `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005`

**Google** 了一阵子未果, 只好从网络通讯的细节中探究竟, 于是祭出神器 **Wireshark**. 首先是在本机抓包, 得到下图:

![Client PCap]({{ site.url }}/public/media/client_pcap.jpg)

图中报文细节也证实了 **TCP** 连接正常, 并完成了协议的握手过程(图中`len=14`的一个来回包交互). 只是接下来 `len=11` 的命令(**Command**)一直因为没有收到**ACK**, 而尝试重传(**TCP Retransmission**), 最后**IDEA**(`client`)主动放弃了.

> [**J**ava **D**ebug **W**ire **P**rotocol Handshake](https://docs.oracle.com/javase/1.5.0/docs/guide/jpda/jdwp-spec.html) 
> 
> After the transport connection is established and before any packets are sent, a handshake occurs between the two sides of the connection:

> The handshake process has the following steps:

> The debugger side sends 14 bytes to the VM side, consisting of the 14 ASCII characters of the string "JDWP-Handshake".
The VM side replies with the same 14 bytes: JDWP-Handshake

看来还需要另一边的报文来配合分析. 通过`tcpdump -i eth1 -w wire.pcap tcp port 5005`, 我拿到了服务端的报文, 如下图:

![JDWP Server Pcap]({{ site.url }}/public/media/server_pcap.jpg)

同样是 **TCP** 正常连接后, 协议握手成功, 但是并没有收到 `len=11` 的命令, 至此可以确定这个**命令包在传输链路上被丢弃了**. 再三尝试, 发现每次都一样, 前面正常直到 `len=11` 命令丢失. 如果是偶发性的丢包, 也不应该偏偏如此巧就只丢同一个包啊? 

为了进一步排除远程服务端复杂网络环境的问题, 我在服务端同网段的另一台服务器上执行了`jdb -attach jdwp_server:5005`, 结果是一切正常. 那么只有可能是本机所在网络环境的出口(路由器)有重大嫌疑.

> **远程服务端的复杂网络环境**
> 
>  ![ingress network]({{ site.url }}/public/media/ingress_network.jpg)  
> 
> `jdwp_server`是处于一个 **Docker Swarm Cluster** 中的一个 **Service**, 通过 `-p 5005:5005` 将端口暴露在 **Ingress Network** 中, 因此来自 **IDEA** 的数据包的链路还有些许复杂的.

果然, 查看路由器配置后发现可疑的拦截规则, 去掉这些规则后丢包问题不复存在. 嗯, 问题解决了!