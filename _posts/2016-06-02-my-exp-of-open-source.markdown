---
layout: post
title:  "我的开源经历"
date:   2016-06-02 14:10:00 +0800
last_modified_at: 2016-08-25 15:20:00 +0800
tags: [open source, github, hs4j, housemd, iPage, config-annotation]
---

开源并不是多么牛逼事情，至少在我今天看来是这样的。

为什么还值得拿出来一说，是因为没有参与其中的人都只看到了开源的结果，而完全不了解产生的过程。 这其中发生哪些有趣事情，又是怎样的机缘巧合，一切的细节是值得耐人寻味的。

<!--more-->

# [HS4J](https://github.com/zhongl/hs4j)

2010年有个名为[HandlerSocket-Plugin-for-MySQL](https://github.com/DeNA/HandlerSocket-Plugin-for-MySQL) 的开源项目， 它号称可以将 MySQL 打造成一个高性能的 NoSQL 服务， 提供超过 `750,000 qps` 访问能力， 这一下触及到了不少热衷于性能的技术人的`G`点。

> [Using MySQL as a NoSQL- A story for exceeding 750,000qps on a commodity server](http://yoshinorimatsunobu.blogspot.com/2010/10/using-mysql-as-nosql-story-for.html)

在随后非常短的时间里，这些人跃跃欲试于自己的工作环境中，并孵化出了不少衍生的开源项目。其中有一个就是 **hs4j** ，它提供了支持 **Java** 语言使用 **HandlerSocket** 的 **API** 。

> **hs4j** 它是 **HandlerSocket for Java** 的缩写，作者是*[killme2008](http://fnil.net/)*。

当时， 我的主要工作是在为 **HDFS** 的 NameNode HA 的方案， 而 **hs4j** 恰好能为这个方案提供一种高可靠且高性能存储的可能。在使用的过程， 我发现 **hs4j** 提供的接口用起来就像 JDBC 的一样繁复啰嗦，由此萌发一个思路：**用类似 ORM 方式简化编码实现**。然后我就写了[**hs4j-kit**](https://github.com/zhongl/hs4j/tree/master/contributes/hs4j-kit)，并联系原作者表达了希望贡献给**hs4j**的意愿，他看过代码后非常乐意接收，只是提了一个小小的要求：**提供一份 README**。

----

# [iPage](https://github.com/zhongl/iPage)

2011年我从淘宝数据平台转岗到淘宝中间件，由于我此前有开发消息中间件的经验，因此在新的岗位上，我有一个很重要的使命就是**优化**另一个历史悠久的消息中间件 **Notify**。


> [**TimeTunnel2**](http://code.taobao.org/p/TimeTunnel/wiki/index/) 是我在数据平台开发的消息中间件的第二代，应用场景和[Kafka](http://kafka.apache.org)一样。可惜的是，在我完成上线的几个月后 [Kafka](http://kafka.apache.org) 开源啦，囧rz
>
> **Notify** 是2007年淘宝在调研了业界消息中间件发现没有合适之后，自研的一套系统，它是当时整个淘宝架构体系中最核心的中间件。 更多内容参见[中间件团队官方博客](http://jm.taobao.org/about/)。


**Notify**的架构设计简单巧妙，即将一张存放消息的**表**作为**消息队列**的实现。这样的方案有两点好处：

1. 利用数据库可靠性和扩展性，大大降低中间件的开发难度
2. 消息是存储于数据库的，中间件几乎是无状态的，很容易水平扩展以支撑海量的吞吐请求

```
+-- Producer --+           +-- Notify --+           +-- Consumer --+  
|              | --------> |  [Queue]   | --------> |              |
+--------------+           +------------+           +--------------+  
                                 ||
                                 ||
                           +-- MySQL ---+
                           |  [Table]   |
                           +------------+
```

消息中间件最重要的使命是**缓解依赖系统间响应能力不匹配**的问题，因此，**面对消息堆积的容忍程度**是消息中间件最为关键的考量指标。**Notify**这一指标完全要仰仗于**MySQL**的表现，这样的表现力要支撑**11.11购物节**，需要上百台数据库实例。

> **11.11购物节** 期间，淘宝的交易核心系统是必须保证足够的响应能力的，但像积分，红包，推荐等非业务核心系统几乎都要降级掉，那么连接核心与非核心系统的**Notify**将可能出现十几亿甚至上百亿消息的堆积。

为什么不能像[Kafka](http://kafka.apache.org)使用文件系统实现队列呢？这样的话，不仅能够节省好多服务器，还能减少其中的网络开销。其实我不是第一个这样想的人，在我之前已经有人尝试实现并开源了，取名叫[**store4j**](https://code.google.com/archive/p/store4j/)，只是可靠性远不如数据库的实现，而仅限开发测试环境使用。

> **技术八卦**
>
> **store4j**的编写者是前淘宝首席架构师，蘑菇街联合创始人**岳旭强**（花名黄裳）
>
> **Notify**的最初编写者是前淘宝中间件负责人，现任蘑菇街技术副总裁 **曾宪杰** （花名华黎）

在看过[**store4j**](https://code.google.com/archive/p/store4j/)的源码后，发现内存索引需要在每次启动时扫描消息队列进行重建。这在大量消息堆积后系统意外重启时，索引重建的过程就会变得很漫长。尽管这类情况平时几乎不太可能出现，可大促活动期间出现的几率立马提升，而且是一旦出现对整个服务链路几乎是致命的。

> 任何系统都有它的容量极限，超出之后我们是无能为力的。比较妥当的解决办法是：
>
> 1. 根据业务场景评估容量规模，
> 2. 单机濒临容量极限时，应能自我保护，重定向部分请求至另外的节点，保持自身的最低服务能力。

因此，**改善索引重建速度**是我认为值得去做的一件事情。与**hs4j**不同的是，我并未基于[**store4j**](https://code.google.com/archive/p/store4j/)源码改进，而是另起炉灶实现了[ iPage](https://github.com/zhongl/iPage)，原因是改进索引重建速度的设计方案对原有的代码改动很大，不如重新实现来得容易。

> [**LevelDB**](https://github.com/zhongl/leveldb) 是同时期类似设计的一款优秀实现。它是 Google 出品用 C++， 尽管有好事者把它 Port 到了 Java 上，然后我在好奇心的驱使下，并未直接拿来用，而是在实现 **iPage** 时借鉴一些它设计理念。

**iPage**算是我第一个自己发起的开源项目，不仅提供简明扼要的[README](https://github.com/zhongl/iPage/blob/master/README.md)，还增加了：

- [设计文档](https://github.com/zhongl/iPage/blob/master/doc/DESIGN.md)
- [测试用例](https://github.com/zhongl/iPage/blob/master/src/test/java/com/github/zhongl/api/IPageTest.java)
- [开源协议声明](https://github.com/zhongl/iPage/blob/master/LICENSE.txt)

最终**iPage**凭借突出的性能数据取代了 **store4j** ，但遗憾的是没能取代 **MySQL** 成为生产环境的存储实现。

----

# [HouseMD](https://github.com/csug/housemd)

**iPage**虽未能*善终*，但来自 **github** 上其他程序员的关注和鼓励，让我感受到了做开源的价值。也就是在这段时间，我进入自己开源的一个高产期，其中最值得来出来说的就是**HouseMD**了。

> 关于它的来历，2012年我就写过，这里就不累述，请见[About HouseMD](https://github.com/CSUG/HouseMD/wiki/About)

与此前项目不同， **HouseMD**是个可运行的实用工具，[README](https://github.com/CSUG/HouseMD/wiki/readme) 尤其强调**安装**和**使用**的指引。

> 为了保证方便安装， 我与 [**jenv**](https://github.com/linux-china/jenv)作者一拍即合，将**HouseMD**纳入到**jenv**的工具库中。

有时*好酒也怕巷子深* 啊，刚刚 POST 到 **github**上是无人问津的，但我自信这样工具能够也应该帮助更多的程序员。 随后我试着法子为**HouseMD**创造一些曝光机会，先是去[开源中国](http://www.oschina.net/p/housemd)发布项目，而后上 Weibo 寻求转发。

> [ Weibo 大 V 转发的效果是杠杠的](http://weibo.com/1650016175/ynQRvvZjY?from=page_1005051650016175_profile&wvr=6&mod=weibotime&type=comment)，5天之后 **github** 上项目的被 Star 的数量[超过100](http://weibo.com/1650016175/yoUOkA8rb?from=page_1005051650016175_profile&wvr=6&mod=weibotime&type=comment#_rnd1464840277949)， 成为 Scala 语言类项目 Top 1。
>
> 当然技术网红**killme2008**的一篇[利用 HouseMD 诊断 Clojure](http://www.blogjava.net/killme2008/archive/2012/06/15/380822.html)，更是让我加粉不少。

在被惹来不少眼球后，部分友人也有参与贡献之意，为了方便他们我又专门写了[开发指南](https://github.com/CSUG/HouseMD/wiki/DevGuideCN)，涉及：

1. 代码获取
2. 编译构建
3. IDE 支持
4. 贡献建议
5. 参考示例

当然这里肯定少不了[测试用例](https://github.com/CSUG/HouseMD/tree/master/src/test/scala/com/github/zhongl/housemd/command)，这次我采用 Spec 风格实现，让测试代码写的更像是文档。

> 那时 Scala 还没有像 Spark 这样的杀手级应用，了解并掌握这门语言的国内程序员凤毛麟角，故真正有深度的贡献我最终没能等到。不过令我欣慰的是，一个名叫[**Grey anatomy**](https://github.com/oldmanpushcart/greys-anatomy)项目出现了，它用 Java 实现了**HouseMD** 大部分功能， 还额外增加了更多强大的能力。去年，在**HouseMD**发布`3`年后，我于 [README](https://github.com/CSUG/HouseMD)中声明**不再维护**，并推荐[**Grey anatomy**](https://github.com/oldmanpushcart/greys-anatomy)作为更好代替者， 也是希望它能比**HouseMD**走的更远。


**HouseMD**之后， 我只有零星一些实验性质的项目放在 **Github** ，活跃度不高。

>有心的朋友可能注意到了 **HouseMD** 项目是挂在在 **CSUG** 下面， 而不是**zhongl**下，这里有个小插曲。
>
>那段时间，因为 Scala 我先后结识了 @陨石 和 @错刀， 一见如故之下， 我们厚颜无耻在 **Github** 上创建了 **CSUG** （China Scala User Group）这个组织， 为了不让主页空荡荡的， 于是乎 我和 @陨石相继把自己项目贡献到了**CSUG** 下。

2014年的最后一天，我被领进了挖财在福地的办公区， 与刚刚开完会的 [李治国](http://weibo.com/mulolo) 寒暄了一句，就开始到工位上写代码了， 代码了， 码了...

----

# [config-annotation](https://github.com/wacai/config-annotation)

我在参与到信贷系统研发之后，孵化出了新的开源项目 **config-annotation** ，关于它的由来与实现细节这里就不累述了，请见2015年6月我在华东区 Scala 爱好者聚会上的分享：

<https://github.com/CSUG/csug/blob/master/shanghai-2015-06-06/configuration_meets_scala_macro.pdf>

为什么**config-annotation**值得拿来一说呢？尽管它的**Star**和 **Fork** 数量都不值得一提， 但我认为它比**HouseMD**更贴近我对开源文化的理解。

> **公开你的源代码只是开源的第一步，真正能够爆发无限可能的是与世界上其他的程序员自由协作，相互贡献。**

这里我做了几件事：

1. 向**typesafe**社区提交了 [Pull Request](**https**://github.com/typesafehub/config/pull/242)，并得到接纳；
2. 将**jar**持续的发布到 [Maven Central Repo](http://search.maven.org/#artifactdetails%7Ccom.wacai%7Cconfig-annotation_2.11%7C0.3.4%7Cjar);
3. 将**Release note** 发布到[ notes.implicit.ly](http://notes.implicit.ly/post/131209824519/config-annotation-034);
4. 借助 [travis-ci.org](https://travis-ci.org/wacai/config-annotation) 实现持续集成；
5. 借助 [codacy.com](https://www.codacy.com/app/zhonglunfu/config-annotation/dashboard) 反馈代码质量。

以上这些都是为了让**config-annotation**真正融入到开源的世界里，使得任何一个网络能够触达的程序员都能很容易的了解它， 使用它，改进它。最终我等来 [**Pull Request**](https://github.com/wacai/config-annotation/pulls?q=is%3Apr+is%3Aclosed)！

# 结尾

看到这里， 我相信你会意识到，原来让你高山仰止的**开源**，其实和你每天的工作没有太大的区别。事实上，我发现不少优秀的程序员**参与开源**已经成为他们工作的一部分，而并未有明确的界限。

回顾来看，参与开源的过程一直在潜移默化的改变我自己的工作方式，了解我的人应该都看在了眼里：

1. 我喜欢用 **issue** 安排团队的工作，跟踪进展，追溯变更；
2. 我喜欢用 **README** 帮助同事快速上手项目，降低沟通成本；
3. 我喜欢用 **Merge Request** 与同事协作开发，分享设计中心得，讨论 Bug 上的经验。

言以至此，对于**开源**是什么，我由衷地希望看到这里的你能够与我感同身受，若是你也能实践**开源**，那么也就不枉我这一番良苦用心啦。

The end.
