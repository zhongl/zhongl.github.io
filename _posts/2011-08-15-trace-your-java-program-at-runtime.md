---
layout: post
Date:  2011-08-15 14:50：00 +0800
title: '动态实时跟踪你的java程序'
tags: java, btrace, aop, aspectj, cglib
excerpt-separator: <!--more--> 
---

之前有写 [基于AOP的日志调试]({% post_url 2010-04-05-debug-by-aop %}) 讨论一种跟踪Java程序的方法, 但不是很完美.后来发现了 [Btrace](https://github.com/btraceio/btrace) , 由于它借助动态字节码注入技术 , 实现优雅且功能强大. 

只不过, 用起来总是磕磕绊绊的, 时常为了跟踪某个问题, 却花了大把的时间调试Btrace的脚本. 为此, 我尝试将几种跟踪模式固化成脚本模板, 待用的时候去调整一下正则表达式之类的. 

跟踪过程往往是假设与验证的螺旋迭代过程, 反复的用BTrace跟踪目标进程, 总有那么几次莫名其妙的不可用, 最后不得不重启目标进程. 若真是线上不能停的服务, 我想这种方式还是不靠谱啊.

为此, 据决定自己的搞个用起来简单, 又能良好支持反复跟踪而不用重启目标进程的工具.

# AOP

AOP 是 Btrace, jip[^1]等众多监测工具的核心思想, 用一段代码最容易说明:

```java
public void say(String words){
  Trace.enter();
  System.out.println(words);
  Trace.exit();
}
```

如上,  `Trace.enter()` 和  `Trace.exit()` 将 `say(words)` 内的代码环抱起来, 对方法进出的进行切面的处理, 便可获取运行时的上下文, 如:

* 调用栈
* 当前线程
* 时间消耗
* 参数与返回值
* 当前实例状态

# 实现的选择

实现切面的方式, 我知道的有以下几种:

## 代理(装饰器)模式

设计模式中装饰器模式和代理模式, 尽管解决的问题域不同, 代码实现是非常相似, 均可以实现切面处理, 这里视为等价. 依旧用代码说明:

```java
interface Person {
  void say(String words);
}

class Officer implements Person {
  public void say(String words) { lie(words); }
  private void lie(String words) {...}
}

class Proxy implements Person {
  private final Officer officer;
  public Proxy(Officer officer) { this.officer = officer; }
  public void say(String words) {
    enter();
    officer.say(words);
    exit();
  }
  private void enter() { ... }
  private void exit() { ... }
}

Person p = new Proxy(new Officer());
```

很明显, 上述 `enter()`  和 `exit()` 是实现切面的地方, 通过获取 `Officer` 的 `Proxy` 实例, 便可对 `Officer` 实例的行为进行跟踪. 这种方式实现起来最简单, 也最直接.

## Java Proxy

Java Proxy[^2] 是 JDK 内置的代理 API, 借助反射机制实现. 用它来是完成切面则会是:

```java
class ProxyInvocationHandler implements InvocationHandler {
  private final Object target;
  public ProxyInvocationHandler(Object target) { this.target = target;}
  public Object handle(Object proxy, Method method, Object[] args) {
    enter();
    method.invoke(target, args);
    exit();
  }
  private void enter() { ... }
  private void exit() { ... }
}
ClassLoader loader = ...
Class<?>[]  interfaces = {Person.class};
Person p = (Person)Proxy.newInstance(loader, interfaces, new ProxyInvocationHandler(new Officer()));
```

相比较上一中方法, 这种不太易读, 但更为通用, 对具体实现依赖很少.

## AspectJ

AspectJ[^3] 是基于字节码操作的 AOP 实现, 相比较 Java proxy, 它会显得对调用更"透明", 编写更简明(类似DSL), 性能更好. 如下代码:

```java
pointcut say(): execute(* say(..))
before(): say() { ... }
after() : say() { ... }
```

AspectJ 实现切面的时机有两种: 

* 静态编译
* 类加载期编织(load-time weaving)

并且它对IDE的支持很丰富.

## CGlib

与 AspectJ 一样 CGlib[^4] 也是操作字节码来实现 AOP 的, 使用上与 Java Proxy 非常相似, 只是不像 Java Proxy 对接口有依赖, 我们熟知的 Spring, Guice 之类的 IoC 容器实现 AOP 都是使用它来完成的.

```java
class Callback implements MethodInterceptor {
  public Object intercept(Object obj, java.lang.reflect.Method method, Object[] args, MethodProxy proxy) throws Throwable {
    enter();
    proxy.invokeSuper(obj, args);
    exit();
  }
  private void enter() { ... }
  private void exit() { ... }
}
Enhancer e = new Enhancer();
e.setSuperclass(Officer.class);
e.setCallback(new Callback());
Person p = e.create();
```

## 字节码操纵

上面四种方法各有适用的场景, 但唯独对运行着的Java进程进行动态的跟踪支持不了, 当然也许是我了解的不够深入, 若有基于上述方案的办法还请不吝赐教. 

还是回到Btrace[^5]的思路上来, 在理解了它借助 `java.lang.instrument` 进行字节码注入的实现原理[^6]后, 实现动态变化跟踪方式或目标应该没有问题.

借下来的问题, 如何操作(注入)字节码实现切面的处理. 可喜的是, 构建自己的监测工具[^7] 一文给我提供了一个很好的切入点. 在此基础上, 经过一些对 ASM[^8] 的深入研究, 可以实现:

* 方法调用进入时, 获取当前实例 (`this`)  和 参数值列表;
* 方法调用出去时, 获取返回值;
* 方法异常抛出时, 触发回调并获取异常实例.

其切面实现的核心代码如下:

```java
 private static class ProbeMethodAdapter extends AdviceAdapter {

    protected ProbeMethodAdapter(MethodVisitor mv, int access, String name, String desc, String className) {
      super(mv, access, name, desc);
      start = new Label();
      end = new Label();
      methodName = name;
      this.className = className;
    }
    
    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
      mark(end);
      catchException(start, end, Type.getType(Throwable.class));
      dup();
      push(className);
      push(methodName);
      push(methodDesc);
      loadThis();
      invokeStatic(Probe.TYPE, Probe.EXIT);
      visitInsn(ATHROW);
      super.visitMaxs(maxStack, maxLocals);
    }
    
    @Override
    protected void onMethodEnter() {
      push(className);
      push(methodName);
      push(methodDesc);
      loadThis();
      loadArgArray();
      invokeStatic(Probe.TYPE, Probe.ENTRY);
      mark(start);
    }
    
    @Override
    protected void onMethodExit(int opcode) {
      if (opcode == ATHROW) return; // do nothing, @see visitMax
      prepareResultBy(opcode);
      push(className);
      push(methodName);
      push(methodDesc);
      loadThis();
      invokeStatic(Probe.TYPE, Probe.EXIT);
    }
    
    private void prepareResultBy(int opcode) {
      if (opcode == RETURN) { // void
        push((Type) null);
      } else if (opcode == ARETURN) { // object
        dup();
      } else {
        if (opcode == LRETURN || opcode == DRETURN) { // long or double
          dup2();
        } else {
          dup();
        }
        box(Type.getReturnType(methodDesc));
      }
    }
    
    private final String className;
    private final String methodName;
    private final Label start;
    private final Label end;
}
```

更多参考请见这里的 [Demo](https://github.com/zhongl/atMonitor), 它是 javaagent , 在伴随宿主进程启动后, 提供 MBean 可用 jconsole 进行动态跟踪的管理.

# 后续的方向

* 提供基于Web的远程交互界面;
* 提供基于Shell的本地命令行接口;
* 提供Profile统计和趋势输出;
* 提供跟踪日志定位与分析.

# 参考

* [常用 Java Profiling 工具的分析与比较](http://www.ibm.com/developerworks/cn/java/j-lo-profiling/index.html?ca=drs-)
* [AOP@Work: Performance monitoring with AspectJ](https://www.ibm.com/developerworks/java/library/j-aopwork10/?S_TACT=105AGX52&S_CMP=cn-a-j)
* [The JavaTM Virtual Machine Specification (2nd)](http://java.sun.com/docs/books/jvms/second_edition/html/VMSpecTOC.doc.html)
* [来自rednaxelafx的JVM分享](http://www.slideshare.net/cafusic/jvm20101228)
* [BCEL](http://commons.apache.org/bcel/manual.html)

[^1]: http://jiprof.sourceforge.net/
[^2]: http://download.oracle.com/javase/6/docs/api/java/lang/reflect/Proxy.html
[^3]: http://www.eclipse.org/aspectj/
[^4]: http://cglib.sourceforge.net/
[^5]: http://kenai.com/projects/btrace/pages/UserGuide
[^6]: http://kenwublog.com/btrace-theory-analysis
[^7]: http://www.ibm.com/developerworks/cn/java/j-jip/index.html?ca=drs
[^8]: http://download.forge.objectweb.org/asm/asm-guide.pdf

