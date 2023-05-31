---
layout: post
title: '复用命令行的小技巧'
date: 2015-02-06 12:00:00 +0800
tags: [shell, bash, sed, awk, function]
last_modified_at: 2016-08-25 15:20:00 +0800
---

重复执行同一个命令行不在此文讨论(需要讨论吗?).

## 背景

一个简单的场景, `kill` 一个名为`Main`的`Java` 进程, 比较土的办法是:

```shell
> jps
2817 Jps
11917 Main
2584 NettyServer
> kill 11917
```

<!--more-->

每次执行都要肉眼识别`Main`进程的`PID`, 效率低, 容易错. 简单的改进是这样的:

```shell
> jps | awk '$2 ~ /Main/ {print $1}' | xargs kill
```

看上去不错! 问题来了, 若是复用上面的命令行去`kill` 另一个进程呢, 比如 `NettyServer` ?

## 方案

在`~/.bashrc`中定义一个函数`jstop` :

```bash
#.bashrc

jstop() {
 jps | awk  "\$2 ~ /$1/ {print \$1}" | xargs kill
}
```

在`source ~/.bashrc`后, 就可以`jstop NettyServer`了.

## 还没完

如果只是这样, 就 **兔养兔新破** 了.

类似这样一行命令的函数, 随着积累的过程中, 频繁编辑`~/.bashrc`挺烦的.

这儿提供一劳永逸的方案: **定义一个定义函数的函数**, 如下:

### For Linux
```
sed -i '2 i func() {\n  sed -i "2 i $1() {\\n $2 \\n}\\n" ~/.bashrc\n source ~/.bashrc\n}' ~/.bashrc;source ~/.bashrc
```

### For Mac OSX

```
sed -i '' '2 i\
func() {\
  sed -i "" "2 i\\\\\
  $1() {\\\\\
    $2\\\\\
  }\\\\\
  \\\\\
  " ~/.bash_profile; source ~/.bash_profile\
}\
\
' ~/.bashrc_profile; source ~/.bash_profile
```

在终端执行上面的命令行, 就可以这样定义`jstop`了:

```
func jstop 'jps | awk "\\$2 ~ /$1/ {print \\$1}" | xargs kill'
```

## 参考

- [sed简明教程](http://coolshell.cn/articles/9104.html)
- [awk简明教程](http://coolshell.cn/articles/9070.html)
- [Calling functions with xargs within a bash script](http://stackoverflow.com/questions/11003418/calling-functions-with-xargs-within-a-bash-script)
