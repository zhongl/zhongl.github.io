---
layout:	post
date:	2017-02-17 18:00:00 +0800
title:	'零侵入式启动javaagent在stagemonitor中的实现'
tags:	[javaagent, bytebuddy]
---

# 启动 Javaagent

**以下摘自 [Oracle Javase 8 Doc](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/package-summary.html)** ：

> Command-Line Interface
>
> An implementation is not required to provide a way to start agents from the command-line interface. On implementations that do provide a way to start agents from the command-line interface, an agent is started by adding this option to the command-line:
>
> `-javaagent:jarpath[=options]`

通过**命令行参数**的方式启动 Javaagent 是最常用的一种方式，但却**不是唯一的方式**。正如文档中提到的， 启动 Javaagent 其实是有两个入口方法：


```java
public static void premain(...);        // [1]

public static void agentmain(...);      // [2]
```

1. 命令行参数的方式启动的是 `premain`，而另一种
2. `agentmain` 则是通过 [Attach API](http://docs.oracle.com/javase/8/docs/jdk/api/attach/spec/index.html) 启动的，如下：

```java
String pid = ...
VirtualMachine vm = VirtualMachine.attach(pid);
vm.loadAgent("/path/to/agent");
vm.detach();
```


# 零侵入式随JVM运行而启动 Javaagent

所谓零侵入， 是指对目标JVM应用不做任何修改，有且仅将 Javaagent 置于特定的位置就能实现对其启动呢？ 

只是基于前文的知识，几乎是不可能做到的。幸运的是，我曾和一位资深专家聊到过这个话题，他启发我从 [stagemonitor][sm] 中找到了答案。

<!--more-->

其实，要想启动 Javaagent 就不可能存在有别于前文的第三种途径，毕竟 JVM 的实现只提供了这两种。

二选一，问题比原先想象的要简单。我们可以排除**命令行参数**的方式，因为它必须要改变 JVM 应用原有的启动命令。那么剩下 **Attach API** 的方式是唯一的选择， 接下来要搞明白的事情是 **由谁来执行预先准备好的 Attach 过程**。

看似就剩 “最后一公里”，实在不然。揣摩之前，必须破除两个思维的限制：

1. 对 **Attach API** 的调用只能是在另个进程中执行；
2. 对 **Instrumentation API** 的调用只能在 `agentmain` 方法中执行。

[stagemonitor][sm] 恰恰是因为没有上述两堵思维的墙，因而实现得非常巧妙。

# ServiceLoader

Tomcat 作为 Servlet 容器的标准实现，借助 [ServiceLoader][sl] 回调所有能够 **找到** 的`ServletContainerInitializer` 接口的实现者。

[stagemonitor][sm] 中 [WebPlugin][wp] 实现了 `ServletContainerInitializer` 接口， 并在类初始化阶段完成 **Attach** 的[过程][init]。


```java
public class WebPlugin extends StagemonitorPlugin 
  implements ServletContainerInitializer {

	static  {
		Stagemonitor.init();
	}
	
	...
}	
```

> 这里打破了第一堵墙，在同一进程内执行 **Attach**。

# ByteBuddy

看过源码之后， 你可能会疑惑，[stagemonitor][sm] 并没有直接使用 **Attach API**， 而是在[AgentAttacher][aa] 中采用 [ByteBuddy](http://bytebuddy.net/#/) 提供的 `ByteBuddyAgent.install` 来获得 **Instrumentation** 的实例，进而完成修改字节码的逻辑。

> 这里打破了第二堵墙，`ByteBuddyAgent.install` 在运行时将 [Installer][inst] 变成 [Javaagent][ap] 交由 JVM 进行回调 `agentmain`， 仅仅只为获得 **Instrumentation** 的实例引用。至于如何使用 **Instrumentation** 完全可以不在 `agentmain` 中执行。

# 零侵入的代价

看似用户体验友好的完美方案，其实是有“代价”的：

1. 这是一个针对 Tomcat 应用场景的特定方案，并不适用于所有的 JVM 应用，例如 [Embedded Servlet Containers](https://github.com/stagemonitor/stagemonitor/wiki/Step-1%3A-In-Browser-Widget#embedded-servlet-containers)，[Spring Boot](https://github.com/stagemonitor/stagemonitor/wiki/Step-1%3A-In-Browser-Widget#spring-boot)，[Jetty](https://github.com/stagemonitor/stagemonitor/wiki/Step-1%3A-In-Browser-Widget#jetty)
2. **Attach API** 并非由标准库提供，这意味着没有安装 JDK （仅有 JRE）的环境是不支持的，这点在 stagemonitor 的 wiki 中也有[声明](https://github.com/stagemonitor/stagemonitor/wiki/Installation)
3. **Attach API** 启动 Javaagent （回调 `agentmain`）的时机是在 `main` 方法开始之后的，这意味着在启动之前已经完成装载字节码，哪怕是属于修改范畴，在启动后的修改逻辑中也不会生效，除非特别指定要 `Instrumentation.retransformClasses(Class<?>... classes)`

零侵入带来的友好体验，相对于通过修改 **命令行参数** 而言并不见得有很大的优势。 同样是 Tomcat 的场景， 实现 **命令行参数** 的[方式][ta]:

```
CATALINA_OPTS="$CATALINA_OPTS -javaagent:/path/to/agent"
```

# 后记

[stagemonitor][sm] 的零侵入思路是可以借鉴并举一反三的，但其代码的实现方式未必是最简洁优雅的，至少我在看的时候有种吃翔的感觉 😂 。


[sm]: https://github.com/hexdecteam/stagemonitor/tree/easestack-0.25.0
[sl]: http://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html
[wp]: https://github.com/hexdecteam/stagemonitor/blob/easestack-0.25.0/stagemonitor-web/src/main/java/org/stagemonitor/web/WebPlugin.java#L47
[init]: https://github.com/hexdecteam/stagemonitor/blob/easestack-0.25.0/stagemonitor-web/src/main/java/org/stagemonitor/web/WebPlugin.java#L56
[inst]: https://github.com/raphw/byte-buddy/blob/master/byte-buddy-agent/src/main/java/net/bytebuddy/agent/Installer.java#L69
[ap]: https://github.com/raphw/byte-buddy/blob/master/byte-buddy-agent/src/main/java/net/bytebuddy/agent/ByteBuddyAgent.java#L796
[aa]: https://github.com/hexdecteam/stagemonitor/blob/easestack-0.25.0/stagemonitor-core/src/main/java/org/stagemonitor/core/instrument/AgentAttacher.java#L94
[ta]: http://stackoverflow.com/questions/6697063/adding-javaagent-to-tomcat-6-server-where-do-i-put-it-and-in-what-format

