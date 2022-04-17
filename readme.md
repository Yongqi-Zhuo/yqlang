# `yqlang`, a script language

## `yqlang` 是什么？

`yqlang`是一种解释型脚本语言，使用`Kotlin`实现。它为`yqbot`增加了自定义编程功能。

## 基本语法

### 语句

构成`yqlang`程序的基本单元是语句（`stmt`）。不同的语句除非会产生歧义，否则不需要换行或分号空开。最基本的语句包括

* 动作（`action`）语句：使`yqbot`产生输出的语句。目前的动作语句只有`say <expr>`，其中`<expr>`表示一个表达式，作用是使bot说话。
  
  例子：`say "hello, world!"`

* 赋值（`assign`）语句：对变量赋值的语句，格式是`<id> <assign> <expr>`。包括
  - `<id> = <expr>`，
  - `<id> += <expr>`，
  - ......
  
  变量的赋值无需声明。

  例子：`array = text.split()`，`[true, false] = [false, true]`。你可能会好奇`text`是什么，事实上对于通过`/yqlang add`添加的程序，在每次有新消息的时候，消息的内容都会被存入`text`变量（数据类型为`String`）。调用`split`且不加参数，就可以自动根据空白符将字符串切割为数组（数据类型为`List`）。

* 条件语句：`if <expr> <stmt>`和`if <expr> <stmt> else <stmt>`。
  
  例子：`if text && text[0:6] == "yqbot爬" say "呜呜呜" else if text[0:6] == "yqbot好" say "好耶！"`

* 循环语句。注意，每个程序最多只有`800ms`的运行时间，超时会被干掉。循环包括
  - `while <expr> <stmt>`
  - `for <id> in <expr>`

  与之配套的有`break`和`continue`语句。

  例子：
    ```
    for i in range(20) {
        j = i
        while j < 20 {
            j += 1
            if i * j == 221 {
                say "221 = " + i + " * " + j
                break
            }
        }
    }
    // 221 = 13 * 17
    ```
  值得注意的是，可以用大括号`{ }`包含多个语句，这样它们就会被看作一个语句。

* 子过程声明语句：`func <id>(<id>, <id>, ...) <stmt>`。
  
  例子：
    ```
    func addOne(this) {
        say "Adding one!"
        this + 1
    }
    2.addOne() // 输出 "Adding one!"，返回 3
    func xor(a, b) {
        if (a && !b) || (!a && b) return true
        else return false
    }
    xor(true, false) // true
    ```

  如果是以函数方式调用子过程（`funcName(args)`），那么会把实参按照顺序传给每一个形参；如果是以方法方式调用子过程（`object.funcName(args)`），那么会把`object`作为第一个参数传入。

### 数据类型

`yqlang`目前允许用户使用的数据类型包括：`String`，`List`，`Number`，`Boolean`，`Procedure`。

`String`和`List`支持下标和切片（`slice`）访问，比如：`"hello"[1] == "e"`，`[1, "a", true][1:3] == ["a", true]`。值得注意的是，没有字符类型，所以经过下标访问后的字符串也是字符串。但是有一个比较好玩的赋值特性，支持片段替换`a="apple"; a[3:4]="rov"; a=="approve"`。

`Number`目前不支持浮点数，是64位整数。

`Boolean`在运算时如果遇到`Number`会被自动提升成`Number`，比如
```
hasYqbot = text.contains("yqbot")
love = 2 * hasYqbot + 1
say "爱你" * love // 对"yqbot爬"的回复是"爱你爱你爱你"
```

`Procedure`可以作为参数传递。比如
```
func add(x, y) x + y
[1,2,3,4,5].reduce(0, add) // 15
```

## `yqbot`对`yqlang`的集成

目前`yqbot`包括的`yqlang`指令都以`/yqlang`开头。列表如下
- `/yqlang run <code>` 表示运行一次`yqlang`代码。
- `/yqlang add <code>` 表示将代码保存，每次有新的事件时都执行一次。如同之前提到过的，事件目前只有新消息，消息文字会保存在顶级作用域的`text`变量中。
- `/yqlang list` 查看现有的已保存的程序的列表。
- `/yqlang remove <index>` 删除某一个编号的程序。
- `/yqlang help` 查看内置函数的列表。
- `/yqlang help <builtin>` 查询某一内置函数的使用方法。

## 高级语法

以下是关于语言的一些高级技巧。

### 作用域

变量保存在作用域当中。在`yqlang`中，一层大括号就是一层作用域。进入大括号内部，就添加一层作用域；离开大括号后，这层作用域就消失了。作用域的规则如下。
- 设置变量：当对一个标识符赋值时，如果这一标识符在之前赋过值，那么就把新的值覆盖之前保存标识符的位置。否则，在当前作用域下保存值。
- 获取变量：当使用一个标识符（`id`）时，会先从本作用域搜索。如果搜索不到，那就会去父级作用域搜索，如此递归进行。如果找到了结果，那么就会返回这一标识符所对应的值；如果找不到，那么就会返回空值（`Null`）。
- 变量的声明周期：如果保存标识符的作用域被删除（也就是离开大括号），那么变量就被删除。
- 底层作用域：底层作用域中的变量是全局变量。通过`/yqlang add`添加的程序会存储这一作用域，所以它们并不会因为程序运行结束而消失。在每次运行时，都能访问这些变量，所以可以在这里存储状态。

### 子过程声明和调用

在程序的编译阶段，会将所有子过程声明单独存储在一个符号表中，所以所有作用域都可以声明子过程，但是所有子过程都对全局有效。

在调用时，如果是以函数方式调用子过程（`funcName(args)`），那么会把实参按照顺序传给每一个形参；如果是以方法方式调用子过程（`object.funcName(args)`），那么会把`object`作为第一个参数传入。

### 下标和切片访问

对字符串（`String`）和列表（`List`），可以使用下标和切片访问。下标和切片访问既是右值又是左值。比如，你可以写出这样的脑溢血赋值：
```
[a, b] = [[1, [2, 3, 4]], ["ahaha", "wowow"]]
[a[1][1:3], b[1:2][0][1:4]] = [[5, 6], "www"]
say a // [1, [2, 5, 6]]
say b // ["ahaha", "wwwww"]
```