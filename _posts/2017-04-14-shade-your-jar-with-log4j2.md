---
layout:	post
date:	2017-04-14 12:30:00 +0800
title:	'maven-shade-plugin遭遇log4j2会出现的问题与解决办法'
tags:	[log4j2, jar, reflection]
---

# 引言

Java 应用开发时常会有一个 **隐隐作痛** 的问题， 就是部署的 jar 很臃肿。 大多数情况下，当传输和存储都还未成瓶颈时， 对此我们还是可以容忍的， 哪怕是它已经有几百兆。但在有些场景下， 例如 Android 开发， 我们则需要非常严肃地对待这个问题，将其 jar 的大小降至最低。

> Android 开发中使用的打包裁剪工具是 [ProGuard][pg]。相较 [maven-shade-plugin][sd] 而言，它裁剪粒度更精细，使得最终裁剪的大小可以做得更小， 只是相应的我们需要付出更多时间才能做到。

作为一个有轻度强迫症的程序员， 我在近期试图应对这个问题的时候， 花了不少时间踩坑， 觉得有必要记录一下。

# 当 maven-shade-plugin 遭遇 log4j2

我在自己的开发项目中使用 [maven-shade-plugin][sd] ，一方面实现最终只用交付一个 jar 以提升部署效率；另一方面则是使用它的一个容易被忽略的功能，[**类级别的无用代码裁剪**](https://maven.apache.org/components/plugins/maven-shade-plugin/shade-mojo.html#minimizeJar)， 来为最终的 jar 进行瘦身。

使用起来非常简单， 开启一个配置选项即可：

```xml
<plugin>
 <groupId>org.apache.maven.plugins</groupId>
 <artifactId>maven-shade-plugin</artifactId>
 <version>3.0.0</version>
 <configuration>
     <minimizeJar>true</minimizeJar>
 </configuration>
 <executions>
     <execution>
         <phase>package</phase>
         <goals>
             <goal>shade</goal>
         </goals>
     </execution>
 </executions>
</plugin>
```

构建打包下来效果 **出奇** 的好，实际运行则出现各种 [log4j2][l4] 相关 `ClassNotFoundExcepton` 或是 `NoClassDefFoundError`。 

究其原因，既不不出在 [log4j2][l4]， 也非 [maven-shade-plugin][sd]， 而是我忽略代码裁剪基本原理。 它是基于静态代码（或字节码）分析出来的依赖关系，来判断哪些代码是无用，这也就意味着那些借助 **反射机制** 实现运行时的依赖， 例如 `Class.forName` 或 `ClassLoader.loadClass`， [maven-shade-plugin][sd] 是无法知道的。

> 不光是 [log4j2][l4]， 类似的基于类加载器实现自动装配 _插件(Plugins)_ 或 _模块(Modules)_ 的开发库，都存在这样的问题， 请一定小心警惕！

# 解决方法

相信聪明的你已经想到了解决办法，很简单，那就是在代码中 **声明** 依赖， 例如：

```java
abstract class ResolvingLinkes {
    static {
        System.out.println(org.apache.logging.log4j.core.appender.AppenderSet.class);
        ...
    }
}
```

> 完整版本可见[ResolvingLinkes.java](https://gist.github.com/zhongl/0a1cdd746d142d0e3bd8e83f568a2599)

通过编写一个运行时并不会用到的`ResolvingLinks`， 在其静态初始化块里 **声明** 要用到的那些类， 这样一来 [maven-shade-plugin][sd] 可以帮我们保留它们以及它们的依赖。

> 为什么不使用 [maven-shade-plugin][sd] 提供的 [filter](https://maven.apache.org/components/plugins/maven-shade-plugin/shade-mojo.html#filters) 配置呢？这个问题挺有趣推荐你自己思考一下 😜

实践过程中，去找出哪些类需要声明，常常是**耗时且令人崩溃**的。 

这里分享一个小技巧，类似 [log4j2][l4] 这样需要自动装配的插件，还是会在某个 **隐藏** 角落进行声明， 不然它自己怎么知道要如何装配呢？好在这个 **隐藏** 的角落通常会在 `META-INF/` 下，例如 `META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat`，若是使用 [ServiceLoader](http://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html) 机制的会在 `META-INF/services/` 下。

## 声明文件的小坑

当出现依赖的多个 jar 中都有同名的声明文件， [maven-shade-plugin][sd] 在默认配置下是会相互覆盖的，这也就意味着哪怕是代码声明了依赖，还是会遇到实际运行的时候这些类并未被相应的库进行装配的尴尬😅。 解决的办法是使用 [transformers](https://maven.apache.org/components/plugins/maven-shade-plugin/examples/resource-transformers.html)， 参考的范例可见 [pom.xml](https://gist.github.com/zhongl/0a1cdd746d142d0e3bd8e83f568a2599#file-pom-xml)。
 
# 值不值得

会不会出现费那么大劲，结果发现 “瘦身” 效果很不明显。 理论上讲是 **有可能的**， 做与不做的预判是要靠经验的，这方面我还没有发现有什么捷径😊。

The End.

[pg]:https://www.guardsquare.com/en/proguard
[sd]:https://maven.apache.org/components/plugins/maven-shade-plugin/
[l4]:https://logging.apache.org/log4j/2.x/

