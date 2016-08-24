---
layout: post
date:  2011-03-05 11:26:00 +0800
title: '支持配额的共享线程池'
tags: java, thread, concurrency
excerpt-separator: <!--more-->
---

受 [@放翁_文初](http://weibo.com/fangweng) 的 [逻辑划分线程池](http://www.blogjava.net/cenwenchu/archive/2011/03/01/345387.html) 一文的启发, 用了几个小时动手实现了一个简陋支持配额的共享线程池. 基本思路与放翁相同, 区别在于引入了两种线程分配策略:

<!--more-->

## 悲观策略

简单的共享一个线程池, 最容易出现的问题就是不同类型任务(或事件)在随机争抢线程资源时, 可能出现**饿死**现象(即抢不到线程).

因此, 悲观策略的宗旨是**绝对的保证每种任务都会被分配到预留的(reserve)配额**, 这种做法本质上和多个线程池的做法一样.

> 如总共100个线程, A任务可用50个线程, B任务可用30个线程, C任务可用20个, 三者互不占用.
>
>  一旦, 任意谁的任务实例超过配额, 将被迫等待直至先前的任务实例结束释放了线程.

统一到一个共享的池中:

* 好处自然是归一化管理, 容易从全局上比较不同任务的优先级, 做出合理的资源分配;
* 坏处可能就是需要去实现这样一个支持配额的共享线程池. 当然, 若不觉得多个线程池有什么不好, 悲观策略其实意义不大:(.

## 乐观策略

无论是使用悲观策略的共享线程池, 还是精心规划多个线程池, 由于都是预定义, 难免在环境变化过程中出现线程资源不足或闲置的情况.

 要是可以这样, 某个时段当A任务较少时,  它所闲置的线程能协调给负载较高的B任务, 那就完美了!

故,  共享线程池的乐观策略就是在保证每种任务预留最低资源的情况下, 允许任务依据一个弹性(elastic)配额去争抢线程资源, 达到线程利用率的最大化.

> 如有100个线程的池, A任务大部分的时候负载较高, 则给予50个的预留配额, 30个的弹性配额; 而B任务是偶尔某个时段复杂较高, 则给予20个线程的预留配额, 30个的弹性配额, 这样留了一个30个线程的资源空间, 让AB去合理竞争.

----

很多实现的细节, 还请参见源代码:

* [CentralExecutorTest.java](https://github.com/zhongl/jtoolkit/blob/master/common/src/test/java/com/github/zhongl/jtoolkit/CentralExecutorTest.java)
* [CentralExecutor.java](https://github.com/zhongl/jtoolkit/blob/master/common/src/main/java/com/github/zhongl/jtoolkit/CentralExecutor.java)

The end.
