# `yqlang`, a script language

## `yqlang` 是什么？

`yqlang`是一种解释型脚本语言，使用`Kotlin`实现。它为`yqbot`增加了自定义编程功能。

## 基本语法

### 语句

构成`yqlang`程序的基本单元是语句（`stmt`）。不同的语句除非会产生歧义，否则不需要换行或分号空开。最基本的语句包括

* 动作（`action`）语句：使`yqbot`产生输出的语句。目前的动作语句包括：
  - `say <expr>`，其中`<expr>`表示一个表达式，作用是使bot说话。
  
    例子：`say "hello, world!"`

  - `nudge <expr>`，其中`<expr>`是QQ号，作用是戳一戳某人。注意，每次最多只能戳一个人。

    例子：`nudge 10086`

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

* 子过程声明语句：`func <id>(<id>, <id>, ...) <stmt>`。语句的值会作为返回值，但是也可以通过`return <expr>`返回值。注意不允许空返回值，至少要写`return null`。
  
  例子：
    ```
    func addOne() {
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

  实参会按照顺序传递给每一个形参。如果是以函数方式调用子过程（`funcName(args)`），那么不会设置`this`变量，它将保持之前的值；如果是以方法方式调用子过程（`object.funcName(args)`），那么在函数作用域中`this`会指代调用者。

* 初始化语句：`init <stmt>`。当且仅当程序被第一次运行时执行，可以用来初始化变量。例子：

  ```
  init counter = 0
  if text {
    if text == "水多少啦" say "已经水了" + counter + "条啦"
    counter += 1
  }
  ```

### 数据类型和表达式

`yqlang`目前允许用户使用的数据类型包括：`String`，`List`，`Number`，`Boolean`，`Procedure`，`Object`。

要获取`String`类型的值，可以通过两种方法：
- 字面量。字符串字面量的内容通过**英文**双引号（`"`）或**英文**单引号（`'`）括起来，如果内容中包含引号或者不可显示字符，那么需要用反斜杠`\ `转义。比如`"\"hello\"\n'world'"`。
- 通过函数调用获取。常见的有`join(list)`，`string(num)`等。

注意，`yqlang`是没有字符类型的，单字符也是`String`类型。但是，由于经过了一些特殊处理，你可以使用`rangeInclusive('a', 'z')`这样的调用来获取一个字符范围，并且可以用`ord('a')`这样的调用来获取ASCII码（如果是Unicode字符，会获得UTF-16编码），并且通过`char(90)`转换回字符。

要获取`List`类型的值，可以通过两种方法：
- 字面量。`List`字面量通过方括号括起来，比如`[1, "a"]`。
- 函数调用。比如`text.split()`。

`List`类型支持许多函数式特性的调用，比如`[1, 2, 3, 4, 5].filter({ $0 % 2 == 0 })`会得到`[2, 4]`。

`String`和`List`支持下标和切片（`slice`）访问，比如：`"hello"[1] == "e"`，`[1, "a", true][1:3] == ["a", true]`。切片访问后，这个表达式仍然是左值，语义是片段替换，比如`a="apple"; a[3:4]="rov"; a=="approve"`。

`Number`目前不支持浮点数，是64位整数。

`Boolean`字面量包括`true`和`false`。`Boolean`在运算时如果遇到`Number`会被自动提升成`Number`，比如
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
另外，匿名函数也被支持。比如
```
[1, 2, 3].map({ $0 * $0 }) // [1, 4, 9]
[1, 4, 2, 3].sorted({ a, b -> a < b }) // [4, 3, 2, 1]
```

`Object`可以当作一个字典。声明一个对象可以用字面量的形式，如
```
obj = { content: 1, show: func() say this.content }
obj.content = [114, 514, 1919, 810]
obj.show() // [114, 514, 1919, 810]
```
以下两种访问对象属性的方法是完全相同的：
```
obj.attr
obj["attr"]
```
但是用字符串作为key来访问可以避开标识符不能以数字开头的限制，所以可以通过`obj[string(233)]`这样的语法来实现任意类型作为key。

当一个`Object`的一个属性被赋值为`Procedure`后，那么`Procedure`中的`this`会被自动绑定到这个`Object`。

## `yqbot`对`yqlang`的集成

### 指令

目前`yqbot`包括的`yqlang`指令都以`/yqlang`开头。列表如下
- `/yqlang run <code>` 表示运行一次`yqlang`代码。
- `/yqlang add <code>` 表示将代码保存，每次有新的事件时都执行一次。如同之前提到过的，事件目前包含新消息和时钟，例如消息文字会保存在顶级作用域的`text`变量中。
- `/yqlang list` 查看现有的已保存的程序的列表。另外，还能看到每个程序的全局变量，这算是一个debug feature。
- `/yqlang remove <index>` 删除某一个编号的程序。
- `/yqlang help` 查看内置函数的列表。
- `/yqlang help <builtin>` 查询某一内置函数的使用方法。

### 事件

当`yqlang`程序被激活，那么一定是因为有事件发生。请通过`if <event>`来检测事件。
- 消息。消息被转换成文字，并存储于`text`变量中；信息发送者的QQ号保存在`sender`变量中；每次有新消息时都会调用一次。

  例子：
  ```
  if text && text[0:4] == "/msg" {
    say "没水过！"
    nudge sender
  }
  ```

- 时钟。为了实现定时功能，yqbot内置每一分钟触发一次的计时器，所有程序都会运行一次。如果程序被时钟触发，那么当前Unix时间戳（以毫秒为单位）就会保存在`clock`变量中。

  例子：
  ```
  if clock say "每分钟都要记得喝水哦！" + clock
  ```
  
- 被戳一戳。当bot被戳一戳，那么戳bot的用户QQ号就会存储在`nudged`变量中。

  例子：
  ```
  init loved = []
  if nudged {
    loved += nudged
    say "mua, " + getNickname(nudged) // mua, 爱可超我
  }
  if text == "yqbot喜欢我吗" {
    if sender in loved say "嗯！" else say "当然不啦"
  }
  ```

## 高级语法

以下是关于语言的一些高级技巧。

### 作用域

变量保存在作用域当中。在`yqlang`中，一层大括号就是一层作用域，另外进入子过程也会产生一层作用域。进入大括号内部，就添加一层作用域；离开大括号后，这层作用域就消失了。作用域的规则如下。
- 设置变量：当对一个标识符赋值时，如果这一标识符在之前赋过值，那么就把新的值覆盖之前保存标识符的位置。否则，在当前作用域下保存值。
- 获取变量：当使用一个标识符（`id`）时，会先从本作用域搜索。如果搜索不到，那就会去父级作用域搜索，如此递归进行。如果找到了结果，那么就会返回这一标识符所对应的值；如果找不到，那么就会返回空值（`Null`）。
- 变量的声明周期：如果保存标识符的作用域被删除（也就是离开大括号），那么变量就被删除。
- 底层作用域：底层作用域中的变量是全局变量。通过`/yqlang add`添加的程序会存储这一作用域，所以它们并不会因为程序运行结束而消失。在每次运行时，都能访问这些变量，所以可以在这里存储状态。

### 子过程声明和调用

所有作用域都可以声明子过程，但是子过程只在该作用域内有效。

实参会按照顺序传递给每一个形参。如果是以函数方式调用子过程（`funcName(args)`），那么不会设置`this`变量，它将保持之前的值；如果是以方法方式调用子过程（`object.funcName(args)`），那么在函数作用域中`this`会指代调用者。

### 匿名函数

匿名函数有两种声明方式，第一种和一般的函数声明方式一样，只是省略了函数名。第二种有这些写法：
```
{ $0 + $1 }
{ x, y -> x + y }
```
以上两种写法完全等价。在`{ $0 + $1 }`中，`$0`表示第一个参数，`$1`表示第二个参数。其实，还有个变量`$`表示所有参数构成的列表，可以通过这个来实现变长参数。值得一提的是，`$`变量不仅在匿名函数被支持，在一般的函数中也可以用。

### 下标和切片访问

对字符串（`String`）和列表（`List`），可以使用下标和切片访问。下标和切片访问既是右值又是左值。比如，你可以写出这样的脑溢血赋值：
```
[a, b] = [[1, [2, 3, 4]], ["ahaha", "wowow"]]
[a[1][1:3], b[1:2][0][1:4]] = [[5, 6], "www"]
say a // [1, [2, 5, 6]]
say b // ["ahaha", "wwwww"]
```