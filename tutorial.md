# 快速入门

快来通过几个例子来掌握`yqlang`的简单语法吧！如果想尝试这些代码的话，向`yqbot`所在的群聊或者私聊发送`/yqlang add <代码>`即可！之后可以通过`/yqlang remove <id>`删除。请关注[`yqlang`开源项目](https://github.com/Yongqi-Zhuo/yqlang)！

## 根据事件来作出回应

作为群聊机器人，最重要的就是对给定的输入作出回复。比如`text`就表示一条新消息，它的值就是消息文本。通过`say`关键字来发送消息。
```
// 逻辑表达式支持短路
if text && text == "bot爬！" say "呜呜呜" else say "收到！"
// 没有任何缩进要求，甚至可以全部写在一行
```

## 存储状态来作出更丰富的回应

可以通过变量来储存信息。如果`bot`被戳一戳，那么`nudged`就是戳了`bot`的人的QQ号。消息的发送者的QQ号被存储在`sender`中。
```
init loved = [] // 用 init 来初始化变量。否则，每次新事件发生时都会执行 loved = [] 清空数组。
if nudged { // 也是这么检测事件哦！
loved += nudged // 存储
say "mua, " + getNickname(nudged) // 使用 getNickname 函数来获取群名片
}
if text == "yqbot喜欢我吗" {
if sender in loved {
    say "嗯！"
    nudge sender
} else say "当然不啦"
}
```

## 使用控制流来实现更复杂的功能

当情况变得复杂，就需要用`for`和`while`循环啦~
```
if text && text[:2] == "乘法" { // 用切片来截取字符串的部分
    numbers = text[2:].split() // 根据空白符把字符串切割成字符串数组
    initial = 1
    for num in numbers initial *= number(num) // 把字符串转化成数字
    say "答案是：" + initial
}
```

可以适当用高阶函数和`lambda`表达式来简化这一过程。
```
if text && text[:2] == "乘法" {
    numbers = text[2:].split()
    say "答案是：" + numbers.reduce(1, { $0 * number($1) })
}
```

## 发送图片

图片是有些复杂的呢~你可以尝试`/yqlang add <下面这段代码>`来尝试一下。具体的讲解请参考[语言手册](manual.md)~
```
init arigatou = images[0]
init picsave arigatou
if text && text == "bot快说谢谢" picsend arigatou
#
[图片]
```