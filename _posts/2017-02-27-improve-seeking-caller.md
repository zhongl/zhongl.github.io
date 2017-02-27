---
layout:	post
date:	2017-02-27 19:30:00 +0800
title:	'Stagemonitor中获取执行SQL的调用方法的实现与改进'
tags:	[javaagent, apm]
---

[Stagemonitor][sm] 有个特性是， 可以展示出所有执行 SQL 调用的方法测量数据， 比如请求量， 耗时分布。实现计量数据的收集并不难（原理可见[《动态实时跟踪你的java程序》](https://zhongl.github.io/2011/08/15/trace-your-java-program-at-runtime/)），但要想**获取执行SQL的调用方法**是什么，则是需要一点“小聪明”。

相信你已经想到了！通过`Thread.currentThread().getStackTrace()`就可以拿到当前方法的调用栈`StackTraceElement[]`，遍历它便可确定调用执行 SQL 的方法究竟有哪些。具体我们看看 [Stagemonitor][sm] 的[实现代码][cu]吧:

<!--more-->

```java
private static String getCallerSignatureGetStackTrace() {
  String executedBy = null;
  for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
	if (StagemonitorClassNameMatcher.isIncluded(e.getClassName())) {
      executedBy = SignatureUtils.getSignature(e.getClassName(), e.getMethodName());
      break;
    }
  }
  return executedBy;
}
```

调用栈方法那么多，怎么找到我们关心的那个调用方法呢？如上，[Stagemonitor][sm]通过配置以便让`StagemonitorClassNameMatcher.isIncluded()`可以对类名进行过滤， 找到第一个满足条件的就是我们关心的方法啦。举个例子，通常执行 SQL 的调用方法都存在 DAO 类中， 而这类类名的命名都有一定的特征， 比如叫`XxxDAO`。 然后，便可以利用这个特征来匹配类名啦。

## 注意，不要忽视性能问题！

尤其对于[Stagemonitor][sm]来说， 作为一个观测工具，应该[尽可能减少对被观测对象的影响](https://zh.wikipedia.org/wiki/%E8%A7%82%E6%B5%8B%E8%80%85%E6%95%88%E5%BA%94)。 `Thread.currentThread().getStackTrace()`是一个相对耗时的调用，且随着调用栈的深度增加，而调用耗时更多。具体到执行SQL的场景， 一个典型的 Spring + MyBatis 的 Web 应用， 它的调用栈就有可能好几十层。

其实，[Stagemonitor][sm]的作者已经意识到了这个问题， 并在[实现代码][cu]提供了另一种优化：

```java
private static String getCallerSignatureSharedSecrets() {
  String executedBy = null;
  Exception exception = new Exception();
  JavaLangAccess javaLangAccess = ...;
  for (int i = 2; i < javaLangAccess.getStackTraceDepth(exception); i++) {
    final StackTraceElement e = javaLangAccess.getStackTraceElement(exception, i);
    if (StagemonitorClassNameMatcher.isIncluded(e.getClassName())) {
      executedBy = SignatureUtils.getSignature(e.getClassName(), e.getMethodName());
      break;
    }
  }
  return executedBy;
}
```

借助`sun.misc.JavaLangAccess`这个 JDK 的内部 API 来改善调用栈的访问效率。

## 其实， 还有更好的做法

首先，类似采集计量数据的办法，我们动态修改可能执行 SQL 的所有调用方法的字节码， 在一进入它们时， 就往当前线程的`ThreadLocal`写入自身的方法签名，如下面代码中注解的那样：

```java

class SignatureHolder {
  public static final ThreadLocal<String> CALLER = new ThreadLocal<>();
}

class FooDAO {

    public Foo getById(long id) {
        // SignatureHolder.CALLER.set("FooDAO#getById")
        return ...
    }
}
```

剩下的就简单了， 在 SQL 执行的上下文中调用 `SignatureHolder.CALLER.get()` ，便可获得执行 SQL的调用方法签名了。

## 效果如何呢？

为了说明效果， 我将上述三种方案简化为如下可观测的代码：

{% gist zhongl/249ef6881daf8705d93c63f7965decd3 Main.java %}

我的 MacBookPro 跑出的数据如下：

> 当然，这只是一个参考值，你完全可以于自己的环境中运行上面代码获得更有参考价值的数据。

### 基线性能

```
[RSF]: Avg elapse 86 ns in depth[50] with doGet [false].
```

一个有 50 层深度的调用栈，不做任何获取调用方法的尝试， 平均每次调用消耗 `86` 纳秒。

### 不同方案下的性能影响

```
[TST]: Avg elapse 33332 ns in depth[50] with doGet [true].
```

通过 `Thread.currentThread().getStackTrace()` 实现的平均每次调用消耗变成了 `33332` 纳秒。

```
[JLA]: Avg elapse 9964 ns in depth[50] with doGet [true].
```

通过 `sun.misc.JavaLangAccess` 实现表现好些， 平均每次调用消耗变成了 `9964` 纳秒。

```
[RSF]: Avg elapse 116 ns in depth[50] with doGet [true].
```

最后， 通过 `ThreadLocal` 实现，其影响最微小， 平均每次调用消耗变成了 `116` 纳秒。

## 最后

方案的选择，大多不会是由一个因素来决定的， 它是平衡的结果。

本文只是抛出我的个人看法， 至于你怎么选择， 留给你来决定。

[sm]:https://github.com/stagemonitor/stagemonitor
[cu]:https://github.com/stagemonitor/stagemonitor/blob/master/stagemonitor-core/src/main/java/org/stagemonitor/core/instrument/CallerUtil.java

