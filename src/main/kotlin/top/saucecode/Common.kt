package top.saucecode

import kotlinx.serialization.Serializable
import top.saucecode.Node.ListNode
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

data class Token(val type: TokenType, val value: String) {
    override fun toString(): String {
        return if (type == TokenType.STRING) "$type: \"$value\"" else "$type: $value"
    }
}

class Constants {
    companion object {
        val builtinSymbols = mapOf("true" to true.toNodeValue(), "false" to false.toNodeValue(), "null" to NullValue)
        private val whiteSpace = Pattern.compile("\\s+")
        private val Split = BuiltinProcedureValue("split", ListNode("separator"), { context ->
            val str = context.stack["this"]!! as StringValue
            val arg = context.stack["separator"]
            if (arg == null) {
                return@BuiltinProcedureValue whiteSpace.split(str.value).filter { it.isNotEmpty() }.map { it.toNodeValue() }.toList().toNodeValue()
            } else {
                if (arg is StringValue) return@BuiltinProcedureValue str.value.split(arg.value).map { it.toNodeValue() }.toList().toNodeValue()
                else if (arg is RegExValue) return@BuiltinProcedureValue arg.split(str)
            }
            throw RuntimeException("$str has no such method as \"split\"")
        }, null)
        private val Join = BuiltinProcedureValue("join", ListNode("separator"), { context ->
            val list = context.stack["this"]!!
            return@BuiltinProcedureValue if (list is Iterable<*>) {
                val arg = context.stack["separator"]?.asString()
                if (arg == null) {
                    list.joinToString(" ").toNodeValue()
                } else {
                    list.joinToString(arg).toNodeValue()
                }
            } else throw RuntimeException("$list has no such method as \"join\"")
        }, null)
        private val Find = BuiltinProcedureValue("find", ListNode("what"), { context ->
            val expr = context.stack["this"]!!
            val arg = context.stack["what"]!!
            if (expr is StringValue) {
                if (arg is StringValue) return@BuiltinProcedureValue expr.value.indexOf(arg.asString()!!).toNodeValue()
                else if (arg is RegExValue) return@BuiltinProcedureValue arg.find(expr)
            }
            else if (expr is ListValue) return@BuiltinProcedureValue expr.value.indexOf(arg).toNodeValue()
            throw RuntimeException("$expr has no such method as \"find\"")
        }, null)
        private val FindAll = BuiltinProcedureValue("findAll", ListNode("what"), { context ->
            val expr = context.stack["this"]!!
            val arg = context.stack["what"]!!
            if (expr is StringValue) {
                if (arg is StringValue) return@BuiltinProcedureValue expr.value.indices.filter { expr.value.substring(it).startsWith(arg.asString()!!) }.map { it.toNodeValue() }.toNodeValue()
                else if (arg is RegExValue) return@BuiltinProcedureValue arg.findAll(expr)
            }
            else if (expr is ListValue) return@BuiltinProcedureValue expr.value.indices.filter { expr.value[it] == arg }.map { it.toNodeValue() }.toNodeValue()
            throw RuntimeException("$expr has no such method as \"findAll\"")
        }, null)
        private val Contains = BuiltinProcedureValue("contains", ListNode("what"), { context ->
            val expr = context.stack["this"]!!
            val arg = context.stack["what"]!!
            if (expr is StringValue) {
                if (arg is StringValue) return@BuiltinProcedureValue expr.value.contains(arg.asString()!!).toNodeValue()
                else if (arg is RegExValue) return@BuiltinProcedureValue arg.contains(expr)
            }
            else if (expr is ListValue) return@BuiltinProcedureValue expr.value.contains(arg).toNodeValue()
            else if (expr is RangeValue<*>) return@BuiltinProcedureValue expr.contains(arg).toNodeValue()
            throw RuntimeException("$expr has no such method as \"contains\"")
        }, null)
        private val Length = BuiltinProcedureValue("length", ListNode(), { context ->
            val expr = context.stack["this"]!!
            return@BuiltinProcedureValue when (expr) {
                is StringValue -> expr.value.length.toNodeValue()
                is ListValue -> expr.value.size.toNodeValue()
                is RangeValue<*> -> expr.size.toNodeValue()
                else -> throw RuntimeException("$expr has no such method as \"length\"")
            }
        }, null)
        private val Time = BuiltinProcedureValue("time", ListNode(), {
            System.currentTimeMillis().toNodeValue()
        }, null)
        private val Random = BuiltinProcedureValue("random", ListNode("first", "second"), { context ->
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
        }, null)
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
        private val Enumerated = BuiltinProcedureValue("enumerated", ListNode(), { context ->
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
        }, null)
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
        private val Sum = BuiltinProcedureValue("sum", ListNode(), { context ->
            val list = context.stack["this"]!!
            return@BuiltinProcedureValue if (list is Iterable<*>) {
                list.sumOf { (it as NodeValue).asNumber()!! }.toNodeValue()
            } else {
                throw RuntimeException("$list has no such method as \"sum\"")
            }
        }, null)
        private val Boolean = BuiltinProcedureValue("boolean", ListNode("value"), { context ->
            context.stack["value"]!!.toBoolean().toNodeValue()
        }, null)
        private val Filter = BuiltinProcedureValue("filter", ListNode("predicate"), { context ->
            val predicate = context.stack["predicate"]!!.asProcedure()!!
            fun predicateCall(it: NodeValue) = predicate.call(context, ListValue(mutableListOf(it)))
            val list = context.stack["this"]!!
            return@BuiltinProcedureValue if (list is Iterable<*>) {
                @Suppress("UNCHECKED_CAST") (list.filter { predicateCall(it as NodeValue).toBoolean() } as List<NodeValue>).toNodeValue()
            } else {
                throw RuntimeException("$list has no such method as \"filter\"")
            }
        }, null)
        private val Reduce = BuiltinProcedureValue("reduce", ListNode("initial", "reducer"), { context ->
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
        }, null)
        private val Map = BuiltinProcedureValue("map", ListNode("mapper"), { context ->
            val mapper = context.stack["mapper"]!!.asProcedure()!!
            fun mapperCall(it: NodeValue) = mapper.call(context, listOf(it).toNodeValue())
            val collection = context.stack["this"]!!
            return@BuiltinProcedureValue if (collection is Iterable<*>) {
                (collection.map { mapperCall(it as NodeValue) }).toNodeValue()
            } else {
                throw RuntimeException("$collection has no such method as \"map\"")
            }
        }, null)
        private val Max = BuiltinProcedureValue("max", ListNode("list"), { context ->
            val list = (context.stack["this"] as? Iterable<*>) ?: (context.stack["list"]!! as Iterable<*>)
            return@BuiltinProcedureValue list.maxByOrNull { it as NumberValue }!! as NodeValue
        }, null)
        private val Min = BuiltinProcedureValue("max", ListNode("list"), { context ->
            val list = (context.stack["this"] as? Iterable<*>) ?: (context.stack["list"]!! as Iterable<*>)
            return@BuiltinProcedureValue list.minByOrNull { it as NumberValue }!! as NodeValue
        }, null)
        private val Reversed = BuiltinProcedureValue("reversed", ListNode(), { context ->
            return@BuiltinProcedureValue when (val list = context.stack["this"]!!) {
                is ListValue -> list.value.reversed().toNodeValue()
                is StringValue -> list.value.reversed().toNodeValue()
                is Iterable<*> -> {
                    @Suppress("UNCHECKED_CAST") (list.reversed() as List<NodeValue>).toNodeValue()
                }
                else -> throw RuntimeException("$list has no such method as \"reversed\"")
            }
        }, null)
        private val Sorted = BuiltinProcedureValue("sorted", ListNode("cmp"), { context ->
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
        }, null)
        private val GetNickname = BuiltinProcedureValue("getNickname", ListNode("id"), { context ->
            val user = context.stack["id"]!!.asNumber()!!
            return@BuiltinProcedureValue context.nickname(user).toNodeValue()
        }, null)
        private val Re = BuiltinProcedureValue("re", ListNode("pattern", "flags"), { context ->
            val pattern = context.stack["pattern"]!!.asString()!!
            val flags = context.stack["flags"]?.asString() ?: ""
            return@BuiltinProcedureValue RegExValue(pattern, flags)
        }, null)
        private val Match = BuiltinProcedureValue("match", ListNode("re"), { context ->
            val str = context.stack["this"]!! as StringValue
            val re = context.stack["re"]!!.asRegEx()!!
            return@BuiltinProcedureValue re.match(str)
        }, null)
        private val MatchAll = BuiltinProcedureValue("matchAll", ListNode("re"), { context ->
            val str = context.stack["this"]!! as StringValue
            val re = context.stack["re"]!!.asRegEx()!!
            return@BuiltinProcedureValue re.matchAll(str)
        }, null)
        private val MatchEntire = BuiltinProcedureValue("matchEntire", ListNode("re"), { context ->
            val str = context.stack["this"]!! as StringValue
            val re = context.stack["re"]!!.asRegEx()!!
            return@BuiltinProcedureValue re.matchEntire(str)
        }, null)
        private val Replace = BuiltinProcedureValue("replace", ListNode("re", "replacement"), { context ->
            val str = context.stack["this"]!! as StringValue
            val replacement = context.stack["replacement"]!! as StringValue
            return@BuiltinProcedureValue when (val re = context.stack["re"]!!) {
                is RegExValue -> re.replace(str, replacement)
                is StringValue -> str.value.replace(re.value, replacement.value).toNodeValue()
                else -> throw RuntimeException("$re has no such method as \"replace\"")
            }
        }, null)
        private val Sleep = BuiltinProcedureValue("sleep", ListNode("ms"), { context ->
            val ms = context.stack["ms"]!!.asNumber()!!
            context.sleep(ms)
            return@BuiltinProcedureValue NullValue
        }, null)
        val builtinProcedures = mapOf(
            // functions
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
            "re" to Re,
            "sleep" to Sleep,
            // methods
            "split" to Split,
            "join" to Join,
            "find" to Find,
            "findAll" to FindAll,
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
            "sorted" to Sorted,
            "match" to Match,
            "matchAll" to MatchAll,
            "matchEntire" to MatchEntire,
            "replace" to Replace,
        )
        val builtinProceduresHelps = mapOf(
            "split" to """
                |Split a string into a list of substrings. If the argument is omitted, the string is split on whitespace. Regular expressions are supported.
                |Usage: str.split(sep)
                |Example: "hello world".split() // ["hello", "world"]
                |Example: "ah_ha_ha".split("_") // ["ah", "ha", "ha"]
                |Example: "so,,,  so , many  separators".split(re(r"[\s,]*")) // ["so", "so", "many", "separators"]
                |""".trimMargin(),
            "join" to """
                |Join a list of strings into a string. If the argument is omitted, the elements are joined with a space.
                |Usage: list.join(sep)
                |Example: ["hello", "world"].join() // "hello world"
                |Example: ["hello", "world"].join("-") // "hello-world
                |""".trimMargin(),
            "find" to """
                |Find the index of an element in a list or the index of a substring in a string. If it is not found, return -1. Regular expressions are supported.
                |Usage: list.find(element)
                |Example: [1, 2, 3, 4, 5].find(3) // 2
                |Example: "hello world".find("o w") // 4
                |Example: [1, 2, 3, 4, 5].find(6) // -1
                |Example: "Email: pony@qq.com, thank you.".find(re(r"\w+@[\w.]+")) // 7
                |""".trimMargin(),
            "findAll" to """
                |Find all the indices of an element in a list or the indices of a substring in a string.
                |Usage: list.findAll(element)
                |Example: [3, 2, 3, 4, 3].findAll(3) // [0, 2, 4]
                |Example: "wowowow".findAll("ow") // [1, 3, 5]
                |Example: [1, 2, 3, 4, 5].findAll(6) // []
                |Example: "2022-01-01, 2022-01-02 and 2022-01-03 are available.".findAll(re(r"\d{4}-\d{2}-\d{2}")) // [0, 12, 27]
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
            "re" to """
                |Create a regular expression. Available flags are: "g" for global, "i" for ignore case, "m" for multi-line, "s" for dot matches all.
                |Usage: re(pattern)
                |Usage: re(pattern, flags)
                |Example: re("[a-z]") // /[a-z]/
                |Example: re("[a-z]", "gi") // /[a-z]/i
                |""".trimMargin(),
            "match" to """
                |Search for the first match in a string with a regular expression. Groups are along returned. If there is no match, null is returned.
                |Note that if the "g" flag is set, groups will not be returned.
                |Usage: str.match(regex)
                |Example: "hello world".match(re(r"(\w+) (\w+)")) // ["hello world", "hello", "world"]
                |Example: "Good morning, madam!".match(re(r"[a-z]+", "ig")) // ["Good", "morning", "madam"]
                |""".trimMargin(),
            "matchAll" to """
                |Search for all matches in a string with a regular expression. Groups are along returned. If there is no match, an empty list is returned.
                |Usage: str.matchAll(regex)
                |Example: "hello to this world".matchAll(re(r"(\w+) (\w+)")) // [[hello to, hello, to], [this world, this, world]]
                |""".trimMargin(),
            "matchEntire" to """
                |Test whether a string matches a regular expression.
                |Usage: str.matchEntire(regex)
                |Example: "hello world".matchEntire(re(r"(\w+) (\w+)")) // true
                |""".trimMargin(),
            "replace" to """
                |Replace all matches in a string with a regular expression or a regular string.
                |Usage: str.replace(regex, replacement)
                |Example: "hello world".replace(re(r"(\w+) (\w+)"), "$2 $1") // "world hello"
                |""".trimMargin(),
            "sleep" to """
                |Sleep for a given time.
                |Usage: sleep(milliseconds)
                |Example: sleep(1000) // Sleep for 1 second
                |""".trimMargin(),
        )
    }
}