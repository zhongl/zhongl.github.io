---
layout: post
title:  "使用 Scala Macro Annotation 实现配置项绑定（中）"
date:   2015-01-20 20:38:00 +0800
tags: scala
excerpt_separator: <!--more-->
---


## v0.1.2

续 [上篇][last], 因 [@连城404 ][404] 的转发[原文][wbref]:

>  @连城404:很有趣，有可能把 @conf val port = 0 中的 "= 0" 去掉换成现实的类型说明吗？即 @conf port: Int 这个赋值在此处完全没有实际意义，更像是实现限制而引入的噪声。

```scala
class Server {
  @conf val port = 0
  //  上面的赋值闲得多余, 可以写成:
  // @conf val port: Int
}
```

确实没有必要, 但意义还是有的:  **IDE 不会报错**. (至少我用的IDEA 14 的 scala plugin 还不支持 Scala Macro)

那 `= 0` 可以去掉吗?

答案是可以, 只是在被 [@连城404][404] 问到的时候我还没有想到办法.

<!--more-->

解决办法方法其实不难, 前提是要对 `Scala AST` 很熟悉.

经查:

```scala
case class ValDef(mods: Modifiers, name: TermName, tpt: Tree, rhs: Tree)
```

`Modifiers` 中有个 `flags` 属性, 其中标记了`val port` 的各种特征.

那么 `val port: Int` 和 `val port = 0` 之间的区别则可以从 `flags` 中体现出来.

```scala
class ModifierFlags {
  final val DEFERRED      = 1 << 4        // was `abstract' for members | trait is virtual
  final val DEFAULTINIT   = 1L << 41      // symbol is initialized to the default value: used by -Xcheckinit

  ...
}
```
`ModifierFlags` 里定义了所有的 `flags` 值, 这里摘录其中两个枚举则对应上面两种不同的写法.

具体实现的代码的提交, 请见[0475d92c][0475d92c].

> 如何发现这些细节 ?

> - `print(x)`, 探查未知对象的展现信息, 多半能够提供重要线索;
> - `print(x.getClass)`, 上面的办法不管用, 则需要查明它的类型, 然后去翻源码了.

## v0.2.0

上面 `v0.1.x` 是一种基于`ValDef` 的声明风格, 实际使用的时候会发现:

**编写简单的同时, 代价是类名定义要与配置路径要严格一致, 这并非适合所有场景**

比如写个 `Kafka` 的消费客户端, 要为它设计其访问服务的配置项

```scala
kafka_broker {
  host = 10.0.0.1
  port = 12306
}
```

此时, 对应的类名就要是`KafkaBroker`, 这显然违背了设计意图,  像 `KafkaConsumer` 才是最为自然的选择.

与其大量时间纠结于命名上, 不如换个更好的声明方式.

*让 `@conf` 带参数可以更改默认配置项路径* :

```scala
class KafkaConsumer {
  @conf("kafka_broker.host") val host: String
}
```

嗯, 不错, 能够解决问题. 只是觉得像绕了一圈又回去了的感觉:

```scala
class KafkaConsumer {
  val host = conf.getString("kafka_broker.host")
}
```

对比这两种写法, 前者似乎没什么优势了.

回顾用 `Macro Annotation` 解决配置项绑定的初衷, 是希望 **将代码的元信息与配置项建立映射关系, 减少因中间字符串的 Hard Code 为重构带来的额外成本**.

为此, 另一种写法诞生了, 它是受这篇 [Adding Reflection to Scala Macros][reflect] 的启发.

其中的细节请见 `v0.2.0` 的 [README][readme], 就不累述于此了.

这种新写法还有个好处, 就是能够方便通过从代码来生成配置文件, 转义的代价很小.

嗯...再写个 `sbt plugin` 吧 :)

[0475d92c]:https://github.com/wacai/config-annotation/commit/0475d92c793b129c704c259cf1d509b025fca699
[wbref]:http://www.weibo.com/1650016175/C0aW9dp3T?ref=atme&type=comment
[404]:http://weibo.com/lianchengzju?from=profile&wvr=6
[last]:{% post_url 2015-01-17-implement-configuration-binding-with-scala-macro-1 %}
[reflect]:http://imranrashid.com/posts/scala-reflection/
[readme]:https://github.com/wacai/config-annotation/tree/v0.2.0
