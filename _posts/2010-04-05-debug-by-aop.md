---
layout: post
date:  2010-04-05 14:50:00 +0800
title: '基于AOP的日志调试'
tags: java, log4j, aop, guice
excerpt-separator: <!--more--> 
---

# 断点 vs 日志

断点是我们日常开发最为常见和高效的调试手段, 相比较输入日志它给予更多的状态信息和灵活的观察角度, 但断点调试是有前提和局限的：

* 需要一个界面友好, 功能强大的IDE,
* 比较适合于在单机的开发环境中进行. 

企业应用开发中, 我们常常会遇到无法断点调试的窘境, 例如:

* 这个异常仅在生产环境出现, 开发环境里无法重现;
* 存在外部系统依赖, 开发环境无法模拟等.

这迫使我们不得不回到**日志调试**的老路子上来. 

<!--more--> 

# Print vs Logging

简单点的话, 我们用:

```java
System.out.println("debug infomation");  
```

就是因为过于简单, 当需要更多信息(如线程, 时间等), 或是定义输出模式和形式就需要编写更多代码, 于是我们有了[Log4j](http://logging.apache.org/log4j).

# 为什么要基于AOP

Log4j挺好用的, 只是与`System.out.print`一样, 在代码中随处可见, 但却没有业务价值. 

更令人头痛的是, 并非每次我们都有足够的经验告诉自己应该在哪里添加这些语句, 以致于调试中不断的因为调正它们的在代码中的位置, 而反复编译 - 打包 - 发布系统. 这种体力活, 太......

换而言之, 我会希望:

* 将Logging剥离于业务之外, 让代码更易于维护,
* 无需重新编译,甚至能够运行时, 可调整输出日志的位置.

[AOP][aop]完全可以帮助我们做到上述两点. 

这完全不是什么新鲜观点, 这在任何介绍[AOP][aop]文章中, 都会提到Logging是其最典型的应用场景.

所以这儿将基于[Guice][g], 讨论如何实现一个非侵入式的, 且能无需重新编译即可调正Logging位置的简单示例.

# 一个基于Guice的示例

我曾经用过一个叫[Log4E](http://log4e.jayefem.de/)的Eclipse插件, 它可根据我们预先的配置, 自动的为我们在编写的代码中插入 logging 的语句, 如方法调用的进口和出口:

```java
public int sum(int a, int b){  
  if (logger.isDebugEnabled()){  
  	logger.debug("sum - start : a is " + a + ", b is " + b);  
  }  

  int result = a + b;  

  if (logger.isDebugEnabled()){  
  	logger.debug("sum - end : return is " + result);  
  }  
}  
```

从上例不难发现, 我们在调试过程中, 往往会通过一个方法的进入或退出的状态(参数, 返回值或异常)来分析问题出在什么地方. 那么, 借助 **MethodInterceptor** 我们可以这样做:

## Logging拦截器

```java
public class LoggingInterceptor implements MethodInterceptor {  

  @Override  
  public Object invoke(MethodInvocation invocation) throws Throwable {  
    try {  
	  Object result = invocation.proceed();  
      // logging 方法, 参数与返回值  
      log(invocation.getMethod(), invocation.getArguments(), result);  
      return result;  
    } catch (Throwable throwable) {  
      // logging 方法, 参数与异常  
      error(invocation.getMethod(), invocation.getArguments(), throwable);  
      throw throwable;  
    }  
  }  
}  
```

接下来, 我们需要配置这个拦截器, 并向[Guice][g]声明它.

```java
public class LoggingModule extends AbstractModule {  
  @Override  
  public void configure() {  
  	bindInterceptor(Matchers.any(), Matchers.any(), new LoggingInterceptor());  
  }    
}  
  
public class Main {  
  public static void main(String[] args) {  
  	Injector injector = Guice.createInjector(new BusinessModule(), new LoggingModule());  
  }  
}  
```

很简单, 不是吗? 这样我们的业务模块的代码完全不用编写输出日志的代码, 只需要在创建`Injector`的时候加入 `LoggingModule` 就可以了.

等等, 好像忘了去实现如何配置日志输出的位置. 好吧, 这个其实很简单:

## 配置Logging位置

```java
bindInterceptor(Matchers.any(), Matchers.any(), new LoggingInterceptor());  
```
`bindInterceptor`方法的第一个参数定义了拦截器将匹配所有类, 第二个参数定义了拦截器将匹配一个类所有方法. 那么, 我们要做的仅仅是通过外部参数调整这两个参数就可以啦. 这儿就演示一个用正则表达式匹配要`Logging`的方法的例子:

```java
public class MethodRegexMatcher extends AbstractMatcher<Method> {  
  final Pattern pattern = Pattern.compile(System.getProperty("logging.method.regex", "*"));  
  
  @Override  
  public boolean matches(Method method) {  
    return pattern.matcher(method.getName()).matches();  
  }  
  
}  
```

可惜这种方法不能在运行时调整, 但这也是可以实现的.

## 运行时配置Logging位置

还是以用正则表达式匹配要Logging的方法为例:

```java
public class LoggingInterceptor implements MethodInterceptor {  
  
  private String regex = "*";  
  
  public void setMethodRegex(String regex){  
    this.regex = regex;  
  }  
  
  @Override  
  public Object invoke(MethodInvocation invocation) throws Throwable {  
    String methodName = invocation.getMethod().getName();  

    try {  
      Object result = invocation.proceed();  
	  if (methodName.matches(regex))  // logging 方法, 参数与返回值  
        log(invocation.getMethod(), invocation.getArguments(), result);  

      return result;  
	} catch (Throwable throwable) {  
      if (methodName.matches(regex))  // logging 方法, 参数与异常  
        error(invocation.getMethod(), invocation.getArguments(), throwable);  
      throw throwable;  
    }  
  }  
}  
```

而后可借助JMX动态调整regex的值, 来实现运行时的配置. 

小结

本文仅以[Guice][g]为例讨论如何改进我们日常开发中调试的问题, 其实这在Spring应用也同样能够实现的, 甚至其它应用[AOP][aop]的场景都是可行的.

拓展开来, 不仅是Logging, 说不定验证(测试)也是可行的呢! 

> 思想有多远, 我们就能走多远!

[aop]: http://en.wikipedia.org/wiki/Aspect-oriented_programming
[g]: http://code.google.com/p/google-guice/
