package top.saucecode.yqlang

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.Node.TypeMismatchRuntimeException
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.YqlangRuntimeException
import top.saucecode.yqlang.Runtime.load
import java.util.regex.Pattern
import kotlin.math.exp

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
    ACTION, IDENTIFIER, NUMBER_LITERAL, STRING_LITERAL, BOOLEAN_LITERAL, NULL, EOF;

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
            NUMBER_LITERAL -> "number_literal"
            STRING_LITERAL -> "string_literal"
            BOOLEAN_LITERAL -> "boolean_literal"
            NULL -> "null"
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
        return if (type == TokenType.STRING_LITERAL) "$type: \"$value\"" else "$type: $value"
    }
}

class BuiltinException : YqlangRuntimeException("Builtin function receives argument of mismatching type or number")
class NoSuchMethodException(what: Any, method: Any) : YqlangRuntimeException("$what has no such method as \"$method\"")

class Constants {
    companion object {
        private val whiteSpace = Pattern.compile("\\s+")
        private fun split(context: ExecutionContext, memory: Memory): NodeValue {
            val str = memory.caller.asString()?.value ?: throw BuiltinException()
            val arg = memory.argOrNull(0)
            if (arg == null) {
                return whiteSpace.split(str).filter { it.isNotEmpty() }.toStringListReference(memory)
            } else {
                if (arg.isStringReference())
                    return str.split(arg.asString()!!.value).toStringListReference(memory)
                else if (arg is RegExValue)
                    return arg.split(str).toStringListReference(memory)
            }
            throw NoSuchMethodException(memory.caller, "split")
        }
        private fun join(context: ExecutionContext, memory: Memory): NodeValue {
            val list = memory.caller
            return if (list is Iterable<*>) {
                if (memory.args.size == 0) {
                    list.joinToString(" ").toStringValueReference(memory)
                } else {
                    val sep = memory.arg(0).asString() ?: throw BuiltinException()
                    list.joinToString(sep.value).toStringValueReference(memory)
                }
            } else throw NoSuchMethodException(memory.caller, "join")
        }
        private fun find(context: ExecutionContext, memory: Memory): NodeValue {
            val expr = memory.caller
            val arg = memory.arg(0)
            if (expr.isStringReference()) {
                if (arg.isStringReference())
                    return expr.asString()!!.value.indexOf(arg.asString()!!.value).toIntegerValue()
                else if (arg is RegExValue)
                    return arg.find(expr.asString()!!.value)
            }
            else if (expr.isListReference())
                return expr.asList()!!.value.map { it.load(memory) }.indexOf(arg).toIntegerValue()
            throw NoSuchMethodException(memory.caller, "find")
        }
        private fun findAll(context: ExecutionContext, memory: Memory): NodeValue {
            val expr = memory.caller
            val arg = memory.arg(0)
            if (expr.isStringReference()) {
                val str = expr.asString()!!.value
                if (arg.isStringReference()) {
                    val sub = arg.asString()!!.value
                    return str.indices.filter { str.substring(it).startsWith(sub) }.toIntegerListReference(memory)
                }
                else if (arg is RegExValue) {
                    return arg.findAll(str).toListValueReference(memory)
                }
            }
            else if (expr.isListReference()) {
                val list = expr.asList()!!.value
                return list.indices.filter { list[it].load(memory) == arg }.toIntegerListReference(memory)
            }
            throw NoSuchMethodException(memory.caller, "findAll")
        }
        private fun contains(context: ExecutionContext, memory: Memory): NodeValue {
            val expr = memory.caller
            val arg = memory.arg(0)
            if (expr.isStringReference()) {
                val str = expr.asString()!!.value
                if (arg.isStringReference()) return str.contains(arg.asString()!!.value).toBooleanValue()
                else if (arg is RegExValue) return arg.contains(str)
            }
            else if (expr.isListReference()) return expr.asList()!!.contains(arg).toBooleanValue()
            else if (expr is RangeValue<*>) return expr.contains(arg).toBooleanValue()
            throw NoSuchMethodException(memory.caller, "contains")
        }
        private fun length(context: ExecutionContext, memory: Memory): NodeValue {
            val expr = memory.caller
            return when {
                expr.isStringReference() -> expr.asString()!!.value.length.toIntegerValue()
                expr.isListReference() -> expr.asList()!!.value.size.toIntegerValue()
                expr is RangeValue<*> -> expr.size.toIntegerValue()
                else -> throw NoSuchMethodException(memory.caller, "length")
            }
        }
//        private val Time = BuiltinProcedureValue("time", ListNode()) {
//            System.currentTimeMillis().toNodeValue()
//        }
        private fun random(context: ExecutionContext, memory: Memory): NodeValue {
            val collection = memory.caller
            return if (collection.toBoolean()) {
                when {
                    collection.isStringReference() -> collection.asString()!!.value.random().toString().toStringValueReference(memory)
                    collection.isListReference() -> collection.asList()!!.value.random().load(memory)
                    collection is RangeValue<*> -> collection.random()
                    else -> throw NoSuchMethodException(memory.caller, "random")
                }
            } else {
                val first = memory.arg(0).asInteger()!!
                val second = memory.arg(1).asInteger()!!
                (first until second).random().toIntegerValue()
            }
        }
        private fun range(context: ExecutionContext, memory: Memory): NodeValue {
            val begin = memory.arg(0)
            val end = memory.argOrNull(1)
            return when (begin) {
                is IntegerValue -> {
                    if (end == null) IntegerRangeValue(IntegerValue(0), begin, false) else IntegerRangeValue(
                        begin, end as IntegerValue, false
                    )
                }
//                is StringValue -> {
//                    CharRangeValue(begin, end!! as StringValue, false)
//                }
                else -> throw TypeMismatchRuntimeException(listOf(IntegerValue::class, StringValue::class), begin)
            }
        }
        private fun rangeInclusive(context: ExecutionContext, memory: Memory): NodeValue {
            val begin = memory.arg(0)
            val end = memory.argOrNull(1)
            return when (begin) {
                is IntegerValue -> {
                    if (end == null) IntegerRangeValue(IntegerValue(0), begin, false) else IntegerRangeValue(
                        begin, end as IntegerValue, true
                    )
                }
//                is StringValue -> {
//                    CharRangeValue(begin, end!! as StringValue, true)
//                }
                else -> throw TypeMismatchRuntimeException(listOf(IntegerValue::class, StringValue::class), begin)
            }
        }
//        private val Integer = BuiltinProcedureValue("integer", ListNode("what"), { context ->
//            val what = context.referenceEnvironment["what"] ?: return@BuiltinProcedureValue NullValue
//            when (what) {
//                is ArithmeticValue -> what.coercedTo(IntegerValue::class)
//                is StringValue -> IntegerValue(what.value.toLong())
//                else -> NullValue
//            }
//        }, null)
//        private val Float = BuiltinProcedureValue("float", ListNode("what"), { context ->
//            val what = context.referenceEnvironment["what"] ?: return@BuiltinProcedureValue NullValue
//            when (what) {
//                is ArithmeticValue -> what.coercedTo(FloatValue::class)
//                is StringValue -> FloatValue(what.value.toDouble())
//                else -> NullValue
//            }
//        }, null)
//        private val Number = BuiltinProcedureValue("number", ListNode("what"), { context ->
//            val what = context.referenceEnvironment["what"] ?: return@BuiltinProcedureValue NullValue
//            when (what) {
//                is BooleanValue -> what.coercedTo(IntegerValue::class)
//                is ArithmeticValue -> what
//                is StringValue -> {
//                    if (what.value.contains('.')) {
//                        FloatValue(what.value.toDouble())
//                    } else {
//                        IntegerValue(what.value.toLong())
//                    }
//                }
//                else -> NullValue
//            }
//        }, null)
//        private val String = BuiltinProcedureValue("string", ListNode("what"), { context ->
//            context.referenceEnvironment["what"]!!.printStr.toNodeValue()
//        }, null)
//        private val Object = BuiltinProcedureValue("object", ListNode("fields"), { context ->
//            val fields = context.referenceEnvironment["fields"]?.asList() ?: return@BuiltinProcedureValue ObjectValue()
//            val result = mutableMapOf<String, NodeValue>()
//            for (field in fields) {
//                val key = field.asList()!![0].asString()!!
//                val value = field.asList()!![1]
//                result[key] = value
//            }
//            return@BuiltinProcedureValue ObjectValue(result)
//        }, null)
//        private val Abs = BuiltinProcedureValue("abs", ListNode("num"), { context ->
//            val it = context.referenceEnvironment["num"]!!.asArithmetic()!!
//            val minusIt = it.unaryMinus()
//            return@BuiltinProcedureValue if (it > minusIt) it else minusIt
//        }, null)
//        private val Enumerated = BuiltinProcedureValue("enumerated", ListNode(), { context ->
//            val list = context.referenceEnvironment["this"]!!
//            return@BuiltinProcedureValue if (list is Iterable<*>) {
//                list.mapIndexed { index, value ->
//                    ListValue(
//                        mutableListOf(
//                            index.toNodeValue(), value as NodeValue
//                        )
//                    )
//                }.toNodeValue()
//            } else {
//                throw InterpretationRuntimeException("$list has no such method as \"enumerated\"")
//            }
//        }, null)
//        private val Ord = BuiltinProcedureValue("ord", ListNode("str"), { context ->
//            context.referenceEnvironment["str"]!!.asString()!!.first().code.toLong().toNodeValue()
//        }, null)
//        private val Chr = BuiltinProcedureValue("chr", ListNode("num"), { context ->
//            context.referenceEnvironment["num"]!!.asInteger()!!.toInt().toChar().toString().toNodeValue()
//        }, null)
//        private val Pow = BuiltinProcedureValue("pow", ListNode("num", "exp"), { context ->
//            val num = context.referenceEnvironment["num"]!!.asArithmetic()!!
//            val exp = context.referenceEnvironment["exp"]!!.asArithmetic()!!
//            num.coercedTo(FloatValue::class).value.pow(exp.coercedTo(FloatValue::class).value).toNodeValue()
//        }, null)
//        private val Sum = BuiltinProcedureValue("sum", ListNode(), { context ->
//            val list = context.referenceEnvironment["this"]!!
//            return@BuiltinProcedureValue if (list is Iterable<*>) {
//                var s: NodeValue? = null
//                for (item in list) {
//                    if (s == null) s = (item as NodeValue)
//                    else s = s.plus(item as NodeValue)
//                }
//                s ?: IntegerValue(0)
//            } else {
//                throw InterpretationRuntimeException("$list has no such method as \"sum\"")
//            }
//        }, null)
//        private val Boolean = BuiltinProcedureValue("boolean", ListNode("value"), { context ->
//            context.referenceEnvironment["value"]!!.toBoolean().toNodeValue()
//        }, null)
//        private fun filter(context: ExecutionContext, memory: Memory): NodeValue {
//            val list = memory.caller.asList() ?: throw BuiltinException()
//            val predicate = memory.arg(0).asClosure() ?: throw BuiltinException()
//            // TODO: support range, string, object
////            if (list !is Iterable<*>) throw InterpretationRuntimeException("$list has no such method as \"filter\"")
//            fun predicateCall(it: NodeValue) = predicate.call(context, 0, listOf(it))
//            val copies = list.map { predicateCall(it as NodeValue) to context.memory.createReference(it as NodeValue) }
//            return@BuiltinProcedureValue (ListValue(copies
//                .filter { it.first.toBoolean() }.mapTo(mutableListOf()) { it.second }, context.memory))
//        }

//                private val Reduce = BuiltinProcedureValue("reduce", ListNode("initial", "reducer"), { context ->
//            val reducer = context.referenceEnvironment["reducer"]!!.asProcedure()!!
//            fun reducerCall(acc: NodeValue, it: NodeValue) = reducer.call(context, listOf(acc, it).toNodeValue())
//            val list = context.referenceEnvironment["this"]!!
//            return@BuiltinProcedureValue if (list is Iterable<*>) {
//                var res = context.referenceEnvironment["initial"]!!
//                for (i in list) {
//                    res = reducerCall(res, i as NodeValue)
//                }
//                res
//            } else {
//                throw InterpretationRuntimeException("$list has no such method as \"reduce\"")
//            }
//        }, null)
//        private val Map = BuiltinProcedureValue("map", ListNode("mapper"), { context ->
//            val mapper = context.referenceEnvironment["mapper"]!!.asProcedure()!!
//            fun mapperCall(it: NodeValue) = mapper.call(context, listOf(it).toNodeValue())
//            val collection = context.referenceEnvironment["this"]!!
//            return@BuiltinProcedureValue if (collection is Iterable<*>) {
//                (collection.map { mapperCall(it as NodeValue) }).toNodeValue()
//            } else {
//                throw InterpretationRuntimeException("$collection has no such method as \"map\"")
//            }
//        }, null)
//        private val Max = BuiltinProcedureValue("max", ListNode("list"), { context ->
//            val list = (context.referenceEnvironment["this"] as? Iterable<*>) ?: (context.referenceEnvironment["list"]!! as Iterable<*>)
//            return@BuiltinProcedureValue list.maxByOrNull { it as NodeValue }!! as NodeValue
//        }, null)
//        private val Min = BuiltinProcedureValue("max", ListNode("list"), { context ->
//            val list = (context.referenceEnvironment["this"] as? Iterable<*>) ?: (context.referenceEnvironment["list"]!! as Iterable<*>)
//            return@BuiltinProcedureValue list.minByOrNull { it as NodeValue }!! as NodeValue
//        }, null)
//        private val Reversed = BuiltinProcedureValue("reversed", ListNode(), { context ->
//            return@BuiltinProcedureValue when (val list = context.referenceEnvironment["this"]!!) {
//                is ListValue -> list.value.reversed().toNodeValue()
//                is StringValue -> list.value.reversed().toNodeValue()
//                is Iterable<*> -> {
//                    @Suppress("UNCHECKED_CAST") (list.reversed() as List<NodeValue>).toNodeValue()
//                }
//                else -> throw InterpretationRuntimeException("$list has no such method as \"reversed\"")
//            }
//        }, null)
//        private val Sorted = BuiltinProcedureValue("sorted", ListNode("cmp"), { context ->
//            @Suppress("UNCHECKED_CAST") val list = context.referenceEnvironment["this"]!! as Iterable<NodeValue>
//            val cmp = context.referenceEnvironment["cmp"]?.asProcedure()
//            return@BuiltinProcedureValue if (cmp == null) {
//                list.sorted().toNodeValue()
//            } else {
//                list.sortedWith { a, b ->
//                    val res = cmp.call(context, ListValue(mutableListOf(a, b)))
//                    if (res.toBoolean()) {
//                        1
//                    } else {
//                        -1
//                    }
//                }.toNodeValue()
//            }
//        }, null)
//        private val GetNickname = BuiltinProcedureValue("getNickname", ListNode("id"), { context ->
//            val user = context.referenceEnvironment["id"]!!.asInteger()!!
//            return@BuiltinProcedureValue context.nickname(user).toNodeValue()
//        }, null)
//        private val Re = BuiltinProcedureValue("re", ListNode("pattern", "flags"), { context ->
//            val pattern = context.referenceEnvironment["pattern"]!!.asString()!!
//            val flags = context.referenceEnvironment["flags"]?.asString() ?: ""
//            return@BuiltinProcedureValue RegExValue(pattern, flags)
//        }, null)
//        private val Match = BuiltinProcedureValue("match", ListNode("re"), { context ->
//            val str = context.referenceEnvironment["this"]!! as StringValue
//            val re = context.referenceEnvironment["re"]!!.asRegEx()!!
//            return@BuiltinProcedureValue re.match(str)
//        }, null)
//        private val MatchAll = BuiltinProcedureValue("matchAll", ListNode("re"), { context ->
//            val str = context.referenceEnvironment["this"]!! as StringValue
//            val re = context.referenceEnvironment["re"]!!.asRegEx()!!
//            return@BuiltinProcedureValue re.matchAll(str)
//        }, null)
//        private val MatchEntire = BuiltinProcedureValue("matchEntire", ListNode("re"), { context ->
//            val str = context.referenceEnvironment["this"]!! as StringValue
//            val re = context.referenceEnvironment["re"]!!.asRegEx()!!
//            return@BuiltinProcedureValue re.matchEntire(str)
//        }, null)
//        private val Replace = BuiltinProcedureValue("replace", ListNode("re", "replacement"), { context ->
//            val str = context.referenceEnvironment["this"]!! as StringValue
//            val replacement = context.referenceEnvironment["replacement"]!! as StringValue
//            return@BuiltinProcedureValue when (val re = context.referenceEnvironment["re"]!!) {
//                is RegExValue -> re.replace(str, replacement)
//                is StringValue -> str.value.replace(re.value, replacement.value).toNodeValue()
//                else -> throw InterpretationRuntimeException("$re has no such method as \"replace\"")
//            }
//        }, null)
//        private val Sleep = BuiltinProcedureValue("sleep", ListNode("ms"), { context ->
//            val ms = context.referenceEnvironment["ms"]!!.asInteger()!!
//            context.sleep(ms)
//            return@BuiltinProcedureValue NullValue
//        }, null)
        fun builtinId(func: String): Int = builtinProceduresNames.indexOf(func)
        val builtinProcedures = mapOf<String, (ExecutionContext, Memory) -> NodeValue>(
            // functions
//            "time" to Time,
            "range" to ::range,
            "rangeInclusive" to ::rangeInclusive,
//            "number" to Number,
//            "num" to Number,
//            "integer" to Integer,
//            "float" to Float,
//            "string" to String,
//            "str" to String,
//            "object" to Object,
//            "abs" to Abs,
//            "ord" to Ord,
//            "chr" to Chr,
//            "char" to Chr,
//            "pow" to Pow,
//            "boolean" to Boolean,
//            "bool" to Boolean,
//            "getNickname" to GetNickname,
//            "re" to Re,
//            "sleep" to Sleep,
            // methods
            "split" to ::split,
            "join" to ::join,
            "find" to ::find,
            "findAll" to ::findAll,
            "contains" to ::contains,
            "length" to ::length,
//            "len" to Length,
            "random" to ::random,
//            "rand" to Random,
//            "enumerated" to Enumerated,
//            "sum" to Sum,
//            "filter" to Filter,
//            "reduce" to Reduce,
//            "map" to Map,
//            "max" to Max,
//            "min" to Min,
//            "reversed" to Reversed,
//            "sorted" to Sorted,
//            "match" to Match,
//            "matchAll" to MatchAll,
//            "matchEntire" to MatchEntire,
//            "replace" to Replace,
        )
        val builtinProceduresNames = builtinProcedures.toList().map { it.first }
        val builtinProceduresValues = builtinProcedures.toList().map { it.second }
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