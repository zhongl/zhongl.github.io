---
layout: post
title:  "使用 Scala Macro Annotation 实现配置项绑定（上）"
date:   2015-01-17 20:38:00 +0800
tags: [scala, macro, annotation, configuration]
excerpt_separator: <!--more-->
---


## 故事是这么开始的

在用 [Scala Macro][scmcr] Annotation 实现之前, 我是根据 [Akka][akka] 官方文档建议的 [扩展][extension] 机制来绑定配置:

[extension]: http://doc.akka.io/docs/akka/2.3.8/java/extending-akka.html

```scala
class SettingsImpl(config: Config) extends Extension {
  import config._
  val BrokerHost = getString("kafka_consumer.broker.host")
  val BrokerPort = getInt("kafka_consumer.broker.port")
}

object Settings extends ExtensionId[SettingsImpl] with ExtensionIdProvider {
  def createExtension(system: ExtendedActorSystem) = new SettingsImpl(system.settings.config)
  def lookup() = Settings
}

class KafkaConsumer extends Actor {
  val settings = Settings(context.system)  
  val brokerHost = settings.BrokerHost
  val brokerPort = settings.BrokerPort

  def receive = ???
}
```
`application.conf` 除了`akka` 外, 加入扩展的内容:

```scala
akka { ... }

kafka_consumer.broker {
  host:10.0.0.1
  port:9092
}
```
<!--more-->

随着配置项个数增加一个量级, 这类 `getXxx(...)` 写得也是让我 **醉了**, 更不要谈重构的时候...[不忍直视]

## 活不能再这么糙下去

我开始寻思着能不能这样:

```scala
class KafkaConsumer extends Actor {
  @conf val brokerHost = ""
  @conf val brokerPort = 0
}
```

然后让编译器 **智能** 的帮我 **挡酒** , 她酒量可比我好太多了.

## 踏上去往[天堂][paradise]的路

下面就是我以 [sbt-example-paradise][paradise] 为基础实现的步骤:

### Say hello to hell

修改 [Test.scala][test] 为:

[test]: https://github.com/scalamacros/sbt-example-paradise/blob/master/core/src/main/scala/Test.scala

```scala
object Test extends App {
  @hello val i = 0
  println(i)
}
```

执行 `sbt clean run`, 不出意料, 报错了:

```shell
[error] scala.MatchError: List(val i = 0) (of class scala.collection.immutable.$colon$colon)
[error] 	at helloMacro$.impl(Macros.scala:10)
[error]   @hello val i = 0
[error]
```

### 穿越森林

显然 [Macros.scala][macros] 中 `match case` 没有考虑 `@hello` 在 `val` 上的情况,  那不如先来看看它是啥:

```scala
annottees.map(_.tree).toList match {
  case t :: Nil => println(t.getClass); t
}
```

其实前面的错误信息已经 暗示了 `t`  的内容是 `val i = 0`, 因此`println(t)` 已经没有意义了, 但弄清它的类型, 有助于替换 **`=`右边的部分** .

`sbt clean run` :

```shell
class scala.reflect.internal.Trees$ValDef
[info] Running Test
0
```

去查看 `ValDef` 源码, 你会发现:

```scala
case class ValDef(mods: Modifiers, name: TermName, tpt: Tree, rhs: Tree) ...
```
> 这一步已经涉及抽象语法树的范畴, 有兴趣的请阅读 [reflection](http://http://docs.scala-lang.org/overviews/reflection/symbols-trees-types.html) 中的 `Tree` 的部分

啊哈, 这也就意味着可以这样写:

```scala
annottees.map(_.tree).toList match {
  case (t @ ValDef(mods, name, tpt, rhs)) :: Nil => println(rhs); t
}
```

直觉告诉我 `rhs` 就是 `0`, `sbt clean run` :

```shell
0
[info] Running Test
0
```

### 天堂之门

现在, 只要弄清楚怎么构造我想要的 `rhs` 就可以达到目的了. 怎么做呢, 看看 [Macros.scala][macros] 的示范, 不难想到:

```scala
annottees.map(_.tree).toList match {
  case ValDef(mods, name, tpt, rhs) :: Nil => ValDef(mods, name, tpt, q"10")
}
```
`sbt clean run` :

```shell
[info] Running Test
10
```

> q"..." 是一种叫 [quasiquotes](http://docs.scala-lang.org/overviews/quasiquotes/setup.html) 的特性, 它使得构造语法树过程的变得异常的简单

### 如果说在地狱是受虐, 那在天堂其实是自虐

请不要天真的以为将 `q"0"` 改成 `q"""config.getInt("test.i")"""` 就大功告成, 后面还有很多问题:

- `config` 对象引用从哪里来?
- `@conf` 修饰的值类型怎么判断?
- 为什么不用`case q"..." =>` 来替代 `case ValDef(...) =>` ?
- 如何兼容 `2.10` 到 `2.11` 版本之间的差异?

这些问题的留个大家一起思考,  也可以关注我的开源项目 [config-annotation][conf-anno] 与我一起探讨.

更为复杂的案例请见[json-annotation][kifi].

[macros]: https://github.com/scalamacros/sbt-example-paradise/blob/master/macros/src/main/scala/Macros.scala
[paradise]: https://github.com/scalamacros/sbt-example-paradise
[kifi]: http://eng.kifi.com/scala-macro-annotations-real-world-example/
[scmcr]: http://docs.scala-lang.org/overviews/macros/overview.html
[akka]: http://akka.io
[ts]: http://typesafe.com
[conf]: https://github.com/typesafehub/config
[conf-anno]: https://github.com/wacai/config-annotation/tree/v0.1.1
