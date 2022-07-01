# `yqlang`, a scripting language

`yqlang`是一个动态类型的、静态作用域的解释型语言，通过编译到字节码解释执行，具有垃圾回收功能。`yqlang`使用`Kotlin`实现，其主要用途是为[`yqbot`](https://github.com/Yongqi-Zhuo/yqbot)增加自定义编程功能。为了方便在网络群聊中使用，其语法格式非常自由，使用到的特殊符号较少。

## 想要写一个`yqlang`程序教`yqbot`做事？

请前往[快速入门](tutorial.md)。如果想要部署`yqbot`，请前往[`yqbot`的主页](https://github.com/Yongqi-Zhuo/yqbot)获取源代码。详尽的语言特性请参阅[语言手册](manual.md)。

## 特性速览

### 基本操作

```
a = 1 // 第一次赋值视作声明
say a + 5
```

### 闭包和高阶函数

```
func f(a) return func() { say a }
f(1)() // 1
l = range(10).map({ f($0) })
for x in l x() // 0 1 2 ... 9
```

### 模式匹配

```
// text == "sudo rm -rf /"
[a, [b, c]] = [3, text.split()] // [a, b, c] == [3, "sudo", "rm"]
for [k, v] in { x: 5, y: 6 } say k + "->" + v // x->5 y->6
func f([m, n], [k, l])
    m * n + k * l
// f([2, "bot"], ["谢谢你", 1]) == "botbot谢谢你"
```

### 切片访问

```
str = "01234"
say str[2:4][-1] // 3
str[2:4][-1] = "ahaha"
say str // "012ahaha4"
```

## 许可证

`yqlang`以`MIT License`发布。

```text
Copyright 2022, Yongqi Zhuo.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```