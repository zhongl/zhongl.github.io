---
layout: post
title:  "使用 Scala Macro Annotation 实现配置项绑定（下）"
date:   2015-01-23 21:30:00 +0800
categories: jekyll update
excerpt_separator: <!--more-->
---


一个话题值得写三篇, 可见我是多么认(gui)真(mao)的一个人.

[中篇][2] 相较 [上篇][1] 在定义上虽减少的噪音, 但其核心问题并没有得到完美的解决, 即:

**一旦真要对配置重构个命名什么的, 必然是要改代码, 再改配置**

写了这么些年的强类型语言, 每次阴沟里翻船都是在 "重构配置" 之后, 这让本是类型检查的优势成为了笑话.

<!--more-->

[中篇][2] 的最后, 我留下了一个 `sbt-plugin` 任务待完成,.

在细想过后, 发现基于上个版本的实现, 还有不少细节上的难点. 

这迫使我从 **生成配置文件** 的角度出发, 重新设计声明配置的代码风格.

(此处省略内心斗争过程的上万字独白)

也正是因此催生了这近乎完美的[第三个版本][3].

## Config-style 

拜 `scala` 灵活语法特性所赐, 代码可以写非常接近最后的配置, 我把这种这写法命名为 `config-style` :

```scala
trait kafka {
  val server = new {
    val host = "wacai.com"
    val port = 12306
  }

  val socket = new {
    val timeout = 3 seconds
    val buffer = 1024 * 64L
  }

  val client = "wacai"
}
```

对比一下真实的配置:

```scala
kafka {
  server {
    host = wacai.com
    port = 12306
  }

  socket {
    timeout = 3s
    buffer = 64K
  }

  client = wacai
}
```

基本上后者就是前者去掉关键字 `trait`, `val`, `new` 之后的内容.

不可思议吗 ?  不可思议吧! (2333333)

## 对 IDE 友好

之前实现版本, 对 IDE 都不友好, 以至于我要在 `README` 特别注明, IDE 的错误提示, 其原因是它还没能完美支持宏特性.

现在, 再也不用在为编辑区里哪些碍眼的红色波浪号而揪心了.

## 对重构友好

在上面的风格的帮助下, 这让配置生成的工作简单了很多, 甚至都用不着写 `sbt-plugin` , 即在编译完成的同时生成对应的配置内容.

这意味着, 我们终于可以愉快地重构配置, 妈妈再也不用担心...

默认情况下, 会在 `src/main/resources` 目录里产生一个与 `trait` 同名的 `.conf` 文件.

为什么不直接写入 `application.conf` ? 

理由很简单, 当有多个 `config-style` 的声明时, 最后合并更新 `application.conf` 的过程会比较烧脑.

更何况有更简单的方法, 为什么不用呢?

[config](https://github.com/typesafehub/config#features-of-hocon) 有个强大的特性, 就是支持 `include "xxx.conf"`.

也就是说, 在 `application.conf` 引入所有其它生成的配置文件情况下, 怎么修改配置的代码都不影响最终合并出来的内容.

更多帮助请见项目[README][3], 另还有一个完整可以运行的[范例项目][example]供参考.

嗯, 正文的部分已经结束, 我向毛爷爷保证这是本话题的最后一篇.

终. (碎觉)

[example]:https://github.com/wacai/config-annotation-example
[3]:https://github.com/wacai/config-annotation
[2]:{% post_url 2015-01-20-implement-configuration-binding-with-scala-macro-2 %}
[1]:{% post_url 2015-01-17-implement-configuration-binding-with-scala-macro-1 %}