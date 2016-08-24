---
layout: post
title: "一个简单易行的Gitlab项目分支管理办法"
date: 2016-06-03 18:00:59 +0800
tags: [gitlab, merge request, code review]
excerpt_separator: <!--more-->
---


```
master
   + -----> release/1.0                                     (1)
   |            + ------------> issue/1                     (2)
   |            |                  |
   |            | <----- MR ------ +                        (3)
   |            |                                           (4)
   + <--- MR ---+                                           (5)
   |  <tag:v1.0>                                            (6)
```

<!--more-->

1. 项目 **Owner** 负责创建当前待发布的分支，如`master> git checkout -b release/1.0`，

2. 项目 **Developer** 根据已提交的 **issue** 创建对应的开发分支，如 `release/1.0> git checkout -b issue/1`，

3. 项目 **Developer** 完成开发后，发起由`issue/1`到`release/1.0`的**Merge Request**给项目 **Owner**，

   * 可并行开发 **issue**， 同样执行步骤 **2** ~ **3**

4. 项目 **Owner** 审查过代码后，合并代码才可**提交测试**， 若出现 **Bug**，则执行执行步骤 **2** ~**3**，

5. 项目 **Tester** 测试全部通过后，发起由`release/1.0`到`master`的**Merge Request**给项目 **Owner**，

6. 项目 **Owner** 合并代码并打标签`v1.0`，如`master> git tag v1.0`，而后才可发布上线。



> **无测试参与的项目**
>
> 比如开发公共库或中间服务的项目，这种情况下，可以**不拉**` release`分支，而直接在` master`上拉` issue`分支，但**Merge Request**的步骤**不能缺少**。



> **注意**
>
> 合并后的分支应删除掉。


## 分支说明

* **master**
  * 最新的提交版本应该与线上版本保持一致
  * 必须是 **Protected**， 仅限项目 Owner 提交或合并
* **release/{version}**
  * 当前开发分支，`version`是版本号
  * 最新提交版本应该与测试环境保持一致
* **issue/{id}**
  * 对应 issue 的开发分支
