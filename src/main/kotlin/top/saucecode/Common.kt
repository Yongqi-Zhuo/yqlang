package top.saucecode

import kotlinx.serialization.Serializable
import top.saucecode.Node.AccessViewNode
import top.saucecode.Node.IdentifierNode
import top.saucecode.Node.ListNode
import top.saucecode.Node.SubscriptNode
import top.saucecode.NodeValue.*
import java.util.regex.Pattern
import kotlin.math.absoluteValue
import kotlin.math.pow

@Serializable
enum class TokenType {
    BRACE_OPEN, BRACE_CLOSE, PAREN_OPEN, PAREN_CLOSE, BRACKET_OPEN, BRACKET_CLOSE, // Braces and parentheses
    NEWLINE, SEMICOLON, COLON, ASSIGN, DOT, COMMA, INIT, // Statements
    IF, ELSE, FUNC, RETURN, WHILE, CONTINUE, BREAK, FOR, IN, // Control flow
    ARROW, // Lambda arrow

    // Operators begin
    NOT, // Unary NOT, unary MINUS cannot be discriminated by tokenizer
    MULT, DIV, MOD, // MULT_OP
    PLUS, // ADD_OP
    MINUS, // ADD_OP, but can be unary op
    GREATER, LESS, GREATER_EQ, LESS_EQ, // COMP_OP
    EQUAL, NOT_EQUAL, // EQ_OP
    LOGIC_AND, // LOGIC_OP
    LOGIC_OR, // LOGIC_OP

    // Operators end
    ACTION, IDENTIFIER, NUMBER, STRING, EOF;

    fun toHumanReadable(): String {
        return when (this) {
            BRACE_OPEN -> "{"
            BRACE_CLOSE -> "}"
            PAREN_OPEN -> "("
            PAREN_CLOSE -> ")"
            BRACKET_OPEN -> "["
            BRACKET_CLOSE -> "]"
            NEWLINE -> "\\n"
            SEMICOLON -> ";"
            COLON -> ":"
            ASSIGN -> "="
            DOT -> "."
            COMMA -> ","
            INIT -> "init"
            IF -> "if"
            ELSE -> "else"
            FUNC -> "func"
            RETURN -> "return"
            WHILE -> "while"
            CONTINUE -> "continue"
            BREAK -> "break"
            FOR -> "for"
            IN -> "in"
            ARROW -> "->"
            NOT -> "!"
            MULT -> "*"
            DIV -> "/"
            MOD -> "%"
            PLUS -> "+"
            MINUS -> "-"
            GREATER -> ">"
            LESS -> "<"
            GREATER_EQ -> ">="
            LESS_EQ -> "<="
            EQUAL -> "=="
            NOT_EQUAL -> "!="
            LOGIC_AND -> "&&"
            LOGIC_OR -> "||"
            ACTION -> "action"
            IDENTIFIER -> "identifier"
            NUMBER -> "number"
            STRING -> "string"
            EOF -> "EOF"
        }
    }
}

fun IntRange.safeSubscript(index: Int): Int? {
    val size = last - first + 1
    val i = if (index < 0) size + index else index
    if (i < 0 || i >= size) return null
    return i + first
}

fun IntRange.safeSlice(begin: Int, end: Int?): IntRange? {
    val size = last - first + 1
    val b = if (begin < 0) size + begin else begin
    val e = if (end == null) size else if (end < 0) size + end else if (end > size) size else end
    if (b >= e || b < 0) return null
    return first + b until first + e
}

@Serializable
data class Token(val type: TokenType, val value: String) {
    override fun toString(): String {
        return if (type == TokenType.STRING) "$type: \"$value\"" else "$type: $value"
    }
}

class Constants {
    companion object {
        val builtinSymbols = mapOf("true" to true.toNodeValue(), "false" to false.toNodeValue(), "null" to NullValue)
        private val Slice = { self: NodeValue ->
            NodeProcedureValue(
                AccessViewNode(
                    IdentifierNode(Token(TokenType.IDENTIFIER, "this")), SubscriptNode(
                        IdentifierNode(Token(TokenType.IDENTIFIER, "begin")),
                        true,
                        IdentifierNode(Token(TokenType.IDENTIFIER, "end"))
                    )
                ), ListNode("begin", "end"), self
            )
        }
        private val whiteSpace = Pattern.compile("\\s+")
        private val Split = { self: NodeValue ->
            BuiltinProcedureValue("split", ListNode("separator"), { context ->
                val str = context.stack["this"]!!.asString()!!
                val arg = context.stack["separator"]?.asString()
                if (arg == null) {
                    whiteSpace.split(str).filter { it.isNotEmpty() }.map { it.toNodeValue() }.toList().toNodeValue()
                } else {
                    str.split(arg).map { it.toNodeValue() }.toList().toNodeValue()
                }
            }, self)
        }
        private val Join = { self: NodeValue ->
            BuiltinProcedureValue("join", ListNode("separator"), { context ->
                val list = context.stack["this"]!!
                return@BuiltinProcedureValue if (list is Iterable<*>) {
                    val arg = context.stack["separator"]?.asString()
                    if (arg == null) {
                        list.joinToString(" ").toNodeValue()
                    } else {
                        list.joinToString(arg).toNodeValue()
                    }
                } else throw RuntimeException("$list has no such method as \"join\"")
            }, self)
        }
        private val Find = { self: NodeValue ->
            BuiltinProcedureValue("find", ListNode("what"), { context ->
                val expr = context.stack["this"]!!
                val arg = context.stack["what"]!!
                when (expr) {
                    is StringValue -> expr.value.indexOf(arg.asString()!!).toNodeValue()
                    is ListValue -> expr.value.indexOf(arg).toNodeValue()
                    else -> throw RuntimeException("$expr has no such method as \"find\"")
                }
            }, self)
        }
        private val Contains = { self: NodeValue ->
            BuiltinProcedureValue("contains", ListNode("what"), { context ->
                val expr = context.stack["this"]!!
                val arg = context.stack["what"]!!
                when (expr) {
                    is StringValue -> expr.value.contains(arg.asString()!!).toNodeValue()
                    is ListValue -> expr.value.contains(arg).toNodeValue()
                    is RangeValue<*> -> expr.contains(arg).toNodeValue()
                    else -> throw RuntimeException("$expr has no such method as \"contains\"")
                }
            }, self)
        }
        private val Length = { self: NodeValue ->
            BuiltinProcedureValue("length", ListNode(), { context ->
                when (val expr = context.stack["this"]!!) {
                    is StringValue -> expr.value.length.toNodeValue()
                    is ListValue -> expr.value.size.toNodeValue()
                    is RangeValue<*> -> expr.size.toNodeValue()
                    else -> throw RuntimeException("$expr has no such method as \"length\"")
                }
            }, self)
        }
        private val Time = BuiltinProcedureValue("time", ListNode(), {
            System.currentTimeMillis().toNodeValue()
        }, null)
        private val Random = { self: NodeValue ->
            BuiltinProcedureValue("random", ListNode("first", "second"), { context ->
                val collection = context.stack["this"]
                return@BuiltinProcedureValue if (collection?.toBoolean() == true) {
                    when (collection) {
                        is StringValue -> collection.value.random().toString().toNodeValue()
                        is ListValue -> collection.value.random()
                        is RangeValue<*> -> collection.random()
                        else -> throw RuntimeException("$collection has no such method as \"random\"")
                    }
                } else {
                    val first = context.stack["first"]!!.asNumber()!!
                    val second = context.stack["second"]!!.asNumber()!!
                    (first until second).random().toNodeValue()
                }
            }, self)
        }
        private val Range = BuiltinProcedureValue("range", ListNode("begin", "end"), { context ->
            val begin = context.stack["begin"]!!
            val end = context.stack["end"]
            return@BuiltinProcedureValue when (begin) {
                is NumberValue -> {
                    if (end == null) NumberRangeValue(NumberValue(0), begin, false) else NumberRangeValue(
                        begin, end as NumberValue, false
                    )
                }
                is StringValue -> {
                    CharRangeValue(begin, end!! as StringValue, false)
                }
                else -> throw RuntimeException("range: $begin must be a number or a string")
            }
        }, null)
        private val RangeInclusive = BuiltinProcedureValue("rangeInclusive", ListNode("begin", "end"), { context ->
            val begin = context.stack["begin"]!!
            val end = context.stack["end"]
            return@BuiltinProcedureValue when (begin) {
                is NumberValue -> {
                    if (end == null) NumberRangeValue(NumberValue(0), begin, true) else NumberRangeValue(
                        begin, end as NumberValue, true
                    )
                }
                is StringValue -> {
                    CharRangeValue(begin, end!! as StringValue, true)
                }
                else -> throw RuntimeException("range: $begin must be a number or a string")
            }
        }, null)
        private val Number = BuiltinProcedureValue("number", ListNode("str"), { context ->
            context.stack["str"]!!.asString()!!.toLong().toNodeValue()
        }, null)
        private val String = BuiltinProcedureValue("string", ListNode("num"), { context ->
            context.stack["num"]!!.asNumber()!!.toString().toNodeValue()
        }, null)
        private val Object = BuiltinProcedureValue("object", ListNode("fields"), { context ->
            val fields = context.stack["fields"]?.asList() ?: return@BuiltinProcedureValue ObjectValue()
            val result = mutableMapOf<String, NodeValue>()
            for (field in fields) {
                val key = field.asList()!![0].asString()!!
                val value = field.asList()!![1]
                result[key] = value
            }
            return@BuiltinProcedureValue ObjectValue(result)
        }, null)
        private val Abs = BuiltinProcedureValue("abs", ListNode("num"), { context ->
            context.stack["num"]!!.asNumber()!!.absoluteValue.toNodeValue()
        }, null)
        private val Enumerated = { self: NodeValue ->
            BuiltinProcedureValue("enumerated", ListNode(), { context ->
                val list = context.stack["this"]!!
                return@BuiltinProcedureValue if (list is Iterable<*>) {
                    list.mapIndexed { index, value ->
                        ListValue(
                            mutableListOf(
                                index.toNodeValue(), value as NodeValue
                            )
                        )
                    }.toNodeValue()
                } else {
                    throw RuntimeException("$list has no such method as \"enumerated\"")
                }
            }, self)
        }
        private val Ord = BuiltinProcedureValue("ord", ListNode("str"), { context ->
            context.stack["str"]!!.asString()!!.first().code.toLong().toNodeValue()
        }, null)
        private val Chr = BuiltinProcedureValue("chr", ListNode("num"), { context ->
            context.stack["num"]!!.asNumber()!!.toInt().toChar().toString().toNodeValue()
        }, null)
        private val Pow = BuiltinProcedureValue("pow", ListNode("num", "exp"), { context ->
            val num = context.stack["num"]!!.asNumber()!!
            val exp = context.stack["exp"]!!.asNumber()!!
            num.toDouble().pow(exp.toDouble()).toLong().toNodeValue()
        }, null)
        private val Sum = { self: NodeValue ->
            BuiltinProcedureValue("sum", ListNode(), { context ->
                val list = context.stack["this"]!!
                return@BuiltinProcedureValue if (list is Iterable<*>) {
                    list.sumOf { (it as NodeValue).asNumber()!! }.toNodeValue()
                } else {
                    throw RuntimeException("$list has no such method as \"sum\"")
                }
            }, self)
        }
        private val Boolean = BuiltinProcedureValue("boolean", ListNode("value"), { context ->
            context.stack["value"]!!.toBoolean().toNodeValue()
        }, null)
        private val Filter = { self: NodeValue ->
            BuiltinProcedureValue("filter", ListNode("predicate"), { context ->
                val predicate = context.stack["predicate"]!!.asProcedure()!!
                fun predicateCall(it: NodeValue) = predicate.call(context, ListValue(mutableListOf(it)))
                val list = context.stack["this"]!!
                return@BuiltinProcedureValue if (list is Iterable<*>) {
                    @Suppress("UNCHECKED_CAST") (list.filter { predicateCall(it as NodeValue).toBoolean() } as List<NodeValue>).toNodeValue()
                } else {
                    throw RuntimeException("$list has no such method as \"filter\"")
                }
            }, self)
        }
        private val Reduce = { self: NodeValue ->
            BuiltinProcedureValue("reduce", ListNode("initial", "reducer"), { context ->
                val reducer = context.stack["reducer"]!!.asProcedure()!!
                fun reducerCall(acc: NodeValue, it: NodeValue) = reducer.call(context, listOf(acc, it).toNodeValue())
                val list = context.stack["this"]!!
                return@BuiltinProcedureValue if (list is Iterable<*>) {
                    var res = context.stack["initial"]!!
                    for (i in list) {
                        res = reducerCall(res, i as NodeValue)
                    }
                    res
                } else {
                    throw RuntimeException("$list has no such method as \"reduce\"")
                }
            }, self)
        }
        private val Map = { self: NodeValue ->
            BuiltinProcedureValue("map", ListNode("mapper"), { context ->
                val mapper = context.stack["mapper"]!!.asProcedure()!!
                fun mapperCall(it: NodeValue) = mapper.call(context, listOf(it).toNodeValue())
                val collection = context.stack["this"]!!
                return@BuiltinProcedureValue if (collection is Iterable<*>) {
                    (collection.map { mapperCall(it as NodeValue) }).toNodeValue()
                } else {
                    throw RuntimeException("$collection has no such method as \"map\"")
                }
            }, self)
        }
        private val Max = { self: NodeValue ->
            BuiltinProcedureValue("max", ListNode("list"), { context ->
                val list = (context.stack["this"] as? Iterable<*>) ?: (context.stack["list"]!! as Iterable<*>)
                return@BuiltinProcedureValue list.maxByOrNull { it as NumberValue }!! as NodeValue
            }, self)
        }
        private val Min = { self: NodeValue ->
            BuiltinProcedureValue("max", ListNode("list"), { context ->
                val list = (context.stack["this"] as? Iterable<*>) ?: (context.stack["list"]!! as Iterable<*>)
                return@BuiltinProcedureValue list.minByOrNull { it as NumberValue }!! as NodeValue
            }, self)
        }
        private val Reversed = { self: NodeValue ->
            BuiltinProcedureValue("reversed", ListNode(), { context ->
                return@BuiltinProcedureValue when (val list = context.stack["this"]!!) {
                    is ListValue -> list.value.reversed().toNodeValue()
                    is StringValue -> list.value.reversed().toNodeValue()
                    is Iterable<*> -> {
                        @Suppress("UNCHECKED_CAST") (list.reversed() as List<NodeValue>).toNodeValue()
                    }
                    else -> throw RuntimeException("$list has no such method as \"reversed\"")
                }
            }, self)
        }
        private val Sorted = { self: NodeValue ->
            BuiltinProcedureValue("sorted", ListNode("cmp"), { context ->
                @Suppress("UNCHECKED_CAST") val list = context.stack["this"]!! as Iterable<NodeValue>
                val cmp = context.stack["cmp"]?.asProcedure()
                return@BuiltinProcedureValue if (cmp == null) {
                    list.sorted().toNodeValue()
                } else {
                    list.sortedWith { a, b ->
                        val res = cmp.call(context, ListValue(mutableListOf(a, b)))
                        if (res.toBoolean()) {
                            1
                        } else {
                            -1
                        }
                    }.toNodeValue()
                }
            }, self)
        }
        private val GetNickname = BuiltinProcedureValue("getNickname", ListNode("id"), { context ->
            val user = context.stack["id"]!!.asNumber()!!
            return@BuiltinProcedureValue context.nickname(user).toNodeValue()
        }, null)
        val builtinFunctions = mapOf(
            "time" to Time,
            "range" to Range,
            "rangeInclusive" to RangeInclusive,
            "number" to Number,
            "num" to Number,
            "string" to String,
            "str" to String,
            "object" to Object,
            "abs" to Abs,
            "ord" to Ord,
            "chr" to Chr,
            "char" to Chr,
            "pow" to Pow,
            "boolean" to Boolean,
            "bool" to Boolean,
            "getNickname" to GetNickname,
        )
        val builtinMethods = mapOf(
            "slice" to Slice,
            "split" to Split,
            "join" to Join,
            "find" to Find,
            "contains" to Contains,
            "length" to Length,
            "len" to Length,
            "random" to Random,
            "rand" to Random,
            "enumerated" to Enumerated,
            "enumerate" to Enumerated,
            "sum" to Sum,
            "filter" to Filter,
            "reduce" to Reduce,
            "map" to Map,
            "max" to Max,
            "min" to Min,
            "reversed" to Reversed,
            "sorted" to Sorted
        )
        val builtinProceduresHelps = mapOf(
            "slice" to """
                |Retrieve a slice of a list or a string. 
                |Usage: list.slice(begin, end)
                |Example: [1, 2, 3, 4, 5].slice(1, 3) // [2, 3]
                |Note that list.slice(begin, end) == list[begin : end].
                |""".trimMargin(),
            "split" to """
                |Split a string into a list of substrings. If the argument is omitted, the string is split on whitespace.
                |Usage: str.split(sep)
                |Example: "hello world".split() // ["hello", "world"]
                |Example: "ah_ha_ha".split("_") // ["ah", "ha", "ha"]
                |""".trimMargin(),
            "join" to """
                |Join a list of strings into a string. If the argument is omitted, the elements are joined with a space.
                |Usage: list.join(sep)
                |Example: ["hello", "world"].join() // "hello world"
                |Example: ["hello", "world"].join("-") // "hello-world
                |""".trimMargin(),
            "find" to """
                |Find the index of an element in a list or the index of a substring in a string. If it is not found, return -1.
                |Usage: list.find(element)
                |Example: [1, 2, 3, 4, 5].find(3) // 2
                |Example: "hello world".find("o w") // 4
                |Example: [1, 2, 3, 4, 5].find(6) // -1
                |""".trimMargin(),
            "contains" to """
                |Check if an element is in a list or a substring is in a string.
                |Usage: list.contains(element)
                |Example: [1, 2, 3, 4, 5].contains(3) // true
                |Example: "hello world".contains("o w") // true
                |Example: [1, 2, 3, 4, 5].contains(6) // false
                |""".trimMargin(),
            "length" to """
                |Get the length of a list or a string.
                |Usage: list.length()
                |Example: [1, 2, 3, 4, 5].length() // 5
                |Example: "hello world".length() // 11
                |""".trimMargin(),
            "time" to """
                |Get the current time in milliseconds.
                |Usage: time()
                |Example: time() // 1650181306679
                |""".trimMargin(),
            "random" to """
                |Get a random item in a string or a list. It can also be called with two numbers or two characters to get a random integer or character in the range [min, max).
                |Usage: list.random()
                |Usage: random(min, max)
                |Example: [1, 2, 3, 4, 5, 6].random() // 1
                |Example: "hello world".random() // "e"
                |Example: random(3, 5) // 4
                |Example: random('a', 'z') // "d"
                |Example: range(10).random() // 5
                |""".trimMargin(),
            "range" to """
                |Create a range expression from min to max (exclusive) that can be used in for statements.
                |Usage: range(min, max)
                |Example: for i in range(1, 5) say i // 1 2 3 4
                |Example: for [i, letter] in range('a', 'f').enumerated() say string(i) + ": " + letter // 0: a 1: b 2: c 3: d 4: e
                |""".trimMargin(),
            "rangeInclusive" to """
                |Create a range expression from min to max (inclusive) that can be used in for statements.
                |Usage: rangeInclusive(min, max)
                |Example: for i in rangeInclusive(1, 5) say i // 1 2 3 4 5
                |Example: range(range(4,7).random()).map({ rangeInclusive("a", "z").random() }).join("") // ojbkk
                |""".trimMargin(),
            "number" to """
                |Convert a string to a number.
                |Usage: number(str)
                |Example: number("123") // 123
                |""".trimMargin(),
            "string" to """
                |Convert a number to a string.
                |Usage: string(num)
                |Example: string(123) // "123"
                |""".trimMargin(),
            "object" to """
                |Convert a key-value list to an object.
                |Usage: object(list)
                |Example: object([["a", 1], ["b", 2]]) // {a: 1, b: 2}
                |""".trimMargin(),
            "abs" to """
                |Get the absolute value of a number.
                |Usage: abs(num)
                |Example: abs(-1) // 1
                |""".trimMargin(),
            "enumerated" to """
                |Create an enumeration of a list.
                |Usage: list.enumerated()
                |Example: [1, 2, 3].enumerated() // [(0, 1), (1, 2), (2, 3)]
                |""".trimMargin(),
            "ord" to """
                |Get the Unicode code point of a character.
                |Usage: ord(char)
                |Example: ord("a") // 97
                |""".trimMargin(),
            "chr" to """
                |Get the character of a Unicode code point.
                |Usage: chr(codePoint)
                |Example: chr(97) // "a"
                |""".trimMargin(),
            "pow" to """
                |Get the power of a number.
                |Usage: pow(num, exponent)
                |Example: pow(2, 3) // 8
                |""".trimMargin(),
            "sum" to """
                |Get the sum of a list.
                |Usage: list.sum()
                |Example: [1, 2, 3].sum() // 6
                |""".trimMargin(),
            "boolean" to """
                |Convert a number to a boolean.
                |Usage: boolean(num)
                |Example: boolean(1) // true
                |Example: boolean(null) // false
                |""".trimMargin(),
            "filter" to """
                |Filter a list using a predicate.
                |Usage: list.filter(predicate)
                |Example: [1, 2, 3, 4, 5].filter({ $0 % 2 == 0 }) // [2, 4]
                |""".trimMargin(),
            "reduce" to """
                |Reduce a list using a function.
                |Usage: list.reduce(initial, function)
                |Example: [1, 2, 3].reduce(0, { $0 + $1 }) // 6
                |""".trimMargin(),
            "map" to """
                |Map a list using a function.
                |Usage: list.map(function)
                |Example: [1, 2, 3].map({ n -> n * n }) // [1, 4, 9]
                |""".trimMargin(),
            "max" to """
                |Get the maximum value of a list.
                |Usage: list.max()
                |Example: [1, 2, 3].max() // 3
                |""".trimMargin(),
            "min" to """
                |Get the minimum value of a list.
                |Usage: list.min()
                |Example: [1, 2, 3].min() // 1
                |""".trimMargin(),
            "reversed" to """
                |Get the reversed list.
                |Usage: list.reversed()
                |Example: [1, 2, 3].reversed() // [3, 2, 1]
                |""".trimMargin(),
            "sorted" to """
                |Get the sorted list.
                |Usage: list.sorted()
                |Usage: list.sorted(comparator)
                |Example: [3, 2, 1].sorted() // [1, 2, 3]
                |Example: [1, 2, 3].sorted({ a, b -> a < b}) // [3, 2, 1]
                |""".trimMargin(),
            "getNickname" to """
                |Get the nickname of a user by QQ ID.
                |Usage: getNickname(user)
                |Example: getNickname(10086) // "中国移动"
                |""".trimMargin(),
        )
    }
}