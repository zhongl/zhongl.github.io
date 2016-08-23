---
layout: post
title: "如何用 Gitlab 做 Code Review"
date: 2016-05-17 15:00:59 +0800
tags: gitlab, code review, branch, merge request
excerpt_separator: <!--more-->
---

## 基于分支的代码 Review

1. 新建 Issue (无论是 bug 还是 feature), 描述背景或问题,
2. 本地创建分支 `issue/123` (`123`是 issue 的 ID), 围绕关联 issue 进行 `program -> commit -> push`,
3. 新建 Merge Request 从 `issue/123` 到 `master`, 并指派给项目 Owner (或合适 Reviewer) ,
4. 被指派人完成代码审查后， 执行代码合并, 同时删除分支 `issue/123` .

<!--more-->

## 多人 Review

1. 提交 Merge Request 后， 被指派人可通过 `@someone` 邀请一个或多个额外的 Reviewer （它们会收到邮件通知）
2. 被邀请的 Reviewer 看过代码后， 可回复`:thumbsup:`或`+1`表示通过， 反之给出修改建议。

## Protect Branch

为了避免意外的代码提交或合并， 项目 Owner 或 Master 可以在项目 `Settings -> Protected branches` 添加受保护的分支，进而引导代码提交是基于 Merge Request 的形式经过 review 之后才合并到目标分支上。
