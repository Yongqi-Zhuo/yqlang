package top.saucecode

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import kotlin.math.min
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.pow

@Serializable
enum class TokenType {
    BRACE_OPEN, BRACE_CLOSE, PAREN_OPEN, PAREN_CLOSE, BRACKET_OPEN, BRACKET_CLOSE, // Braces and parentheses
    NEWLINE, SEMICOLON, COLON, ASSIGN, DOT, COMMA, INIT, // Statements
    IF, ELSE, FUNC, RETURN, WHILE, CONTINUE, BREAK, FOR, IN, // Control flow
    MULT_OP, // MULTIPLY, DIVIDE, MODULO
    ADD_OP, // PLUS, MINUS,
    COMP_OP, // EQUAL, NOT_EQUAL, GREATER, LESS, GREATER_EQUAL, LESS_EQUAL
    LOGIC_OP, //AND, OR
    NOT, ACTION, IDENTIFIER, NUMBER, STRING, EOF
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

class Tokenizer(private val input: String) {
    private var index = 0
    private var currentChar = input[index]

    private fun advance() {
        index++
        if (index < input.length) {
            currentChar = input[index]
        }
    }

    fun scan(): List<Token> {
        val tokens = mutableListOf<Token>()
        fun pushAndAdvance(token: Token) {
            tokens.add(token)
            advance()
        }

        fun handleTwoCharOp(type: TokenType, value: String, single: TokenType? = null) {
            if (index < input.length - 1 && input[index + 1] == value[1]) {
                tokens.add((Token(type, value)))
                advance()
                advance()
            } else if (single != null) {
                pushAndAdvance(Token(single, value[0].toString()))
            } else {
                throw IllegalArgumentException("Unexpected character: $currentChar")
            }
        }
        while (index < input.length) {
            when {
                currentChar == '{' -> pushAndAdvance(Token(TokenType.BRACE_OPEN, "{"))
                currentChar == '}' -> pushAndAdvance(Token(TokenType.BRACE_CLOSE, "}"))
                currentChar == '(' -> pushAndAdvance(Token(TokenType.PAREN_OPEN, "("))
                currentChar == ')' -> pushAndAdvance(Token(TokenType.PAREN_CLOSE, ")"))
                currentChar == '[' -> pushAndAdvance(Token(TokenType.BRACKET_OPEN, "["))
                currentChar == ']' -> pushAndAdvance(Token(TokenType.BRACKET_CLOSE, "]"))
                currentChar == ';' -> pushAndAdvance(Token(TokenType.SEMICOLON, ";"))
                currentChar == '\n' -> pushAndAdvance(Token(TokenType.NEWLINE, "\n"))
                currentChar == ':' -> pushAndAdvance(Token(TokenType.COLON, ":"))
                currentChar == '.' -> pushAndAdvance(Token(TokenType.DOT, "."))
                currentChar == ',' -> pushAndAdvance(Token(TokenType.COMMA, ","))
                currentChar == '+' -> handleTwoCharOp(TokenType.ASSIGN, "+=", TokenType.ADD_OP)
                currentChar == '-' -> handleTwoCharOp(TokenType.ASSIGN, "-=", TokenType.ADD_OP)
                currentChar == '*' -> handleTwoCharOp(TokenType.ASSIGN, "*=", TokenType.MULT_OP)
                currentChar == '%' -> handleTwoCharOp(TokenType.ASSIGN, "%=", TokenType.MULT_OP)
                currentChar == '&' -> handleTwoCharOp(TokenType.LOGIC_OP, "&&")
                currentChar == '|' -> handleTwoCharOp(TokenType.LOGIC_OP, "||")
                currentChar == '=' -> handleTwoCharOp(TokenType.COMP_OP, "==", TokenType.ASSIGN)
                currentChar == '!' -> handleTwoCharOp(TokenType.COMP_OP, "!=", TokenType.NOT)
                currentChar == '>' -> handleTwoCharOp(TokenType.COMP_OP, ">=", TokenType.COMP_OP)
                currentChar == '<' -> handleTwoCharOp(TokenType.COMP_OP, "<=", TokenType.COMP_OP)
                currentChar == '/' -> {
                    if (index == input.length - 1) {
                        pushAndAdvance(Token(TokenType.MULT_OP, "/"))
                    } else {
                        if (input[index + 1] == '/') {
                            while (index < input.length && currentChar != '\n') {
                                advance()
                            }
                        } else {
                            handleTwoCharOp(TokenType.ASSIGN, "/=", TokenType.MULT_OP)
                        }
                    }
                }
                currentChar == '"' -> {
                    var str = ""
                    var escape = false
                    advance()
                    while (index < input.length) {
                        if (escape) {
                            escape = false
                            when (currentChar) {
                                'n' -> str += '\n'
                                'r' -> str += '\r'
                                't' -> str += '\t'
                                '\\' -> str += '\\'
                                '"' -> str += '"'
                                else -> str += "\\$currentChar"
                            }
                        } else if (currentChar == '\\') {
                            escape = true
                        } else if (currentChar == '"') {
                            break
                        } else {
                            str += currentChar
                        }
                        advance()
                    }
                    tokens.add(Token(TokenType.STRING, str))
                    advance()
                }
                currentChar.isDigit() -> {
                    val start = index
                    do {
                        advance()
                    } while (currentChar.isDigit() && index < input.length)
                    val value = input.substring(start, index)
                    tokens.add(Token(TokenType.NUMBER, value))
                }
                currentChar.isLetter() || currentChar == '_' -> {
                    val start = index
                    do {
                        advance()
                    } while ((currentChar.isLetterOrDigit() || currentChar == '_') && index < input.length)
                    when (val value = input.substring(start, index)) {
                        "if" -> tokens.add(Token(TokenType.IF, "if"))
                        "else" -> tokens.add(Token(TokenType.ELSE, "else"))
                        "func" -> tokens.add(Token(TokenType.FUNC, "func"))
                        "return" -> tokens.add(Token(TokenType.RETURN, "return"))
                        "while" -> tokens.add(Token(TokenType.WHILE, "while"))
                        "continue" -> tokens.add(Token(TokenType.CONTINUE, "continue"))
                        "break" -> tokens.add(Token(TokenType.BREAK, "break"))
                        "for" -> tokens.add(Token(TokenType.FOR, "for"))
                        "in" -> tokens.add(Token(TokenType.IN, "in"))
                        "init" -> tokens.add(Token(TokenType.INIT, "init"))
                        "say" -> tokens.add(Token(TokenType.ACTION, "say"))
                        // "text" -> tokens.add(Token(TokenType.IDENTIFIER, "text")) // events are special identifiers
                        else -> tokens.add(Token(TokenType.IDENTIFIER, value))
                    }
                }
                else -> {
                    advance()
                }
            }
        }
        tokens.add(Token(TokenType.EOF, "EOF"))
        return tokens
    }
}

@Serializable
sealed class NodeValue: Comparable<NodeValue> {
    abstract fun toBoolean(): Boolean
    fun asString() = (this as? StringValue)?.value
    fun asNumber() = (this as? NumberValue)?.value
    fun asList() = (this as? ListValue)?.value
    fun asProcedure() = (this as? ProcedureValue)
    fun toNode() = ValueNode(this)
    operator fun plus(that: NodeValue): NodeValue {
        return when (this) {
            is BooleanValue -> {
                when (that) {
                    is BooleanValue -> NumberValue(this.value.toLong() + that.value.toLong())
                    is NumberValue -> NumberValue(this.value.toLong() + that.value)
                    is StringValue -> StringValue(this.value.toString() + that.value)
                    is ListValue -> ListValue(listOf(this) + that.value)
                    else -> throw IllegalArgumentException("Invalid operation: $this + $that")
                }
            }
            is NumberValue -> {
                when (that) {
                    is BooleanValue -> NumberValue(this.value + that.value.toLong())
                    is NumberValue -> NumberValue(this.value + that.value)
                    is StringValue -> StringValue(this.value.toString() + that.value)
                    is ListValue -> ListValue(listOf(this) + that.value)
                    else -> throw IllegalArgumentException("Invalid operation: $this + $that")
                }
            }
            is StringValue -> {
                when (that) {
                    is BooleanValue -> StringValue(this.value + that.value.toString())
                    is NumberValue -> StringValue(this.value + that.value.toString())
                    is StringValue -> StringValue(this.value + that.value)
                    is ListValue -> ListValue(listOf(this) + that.value)
                    else -> throw IllegalArgumentException("Invalid operation: $this + $that")
                }
            }
            is ListValue -> {
                when (that) {
                    is BooleanValue -> ListValue(this.value + listOf(that))
                    is NumberValue -> ListValue(this.value + listOf(that))
                    is StringValue -> ListValue(this.value + listOf(that))
                    is ListValue -> ListValue(this.value + that.value)
                    else -> throw IllegalArgumentException("Invalid operation: $this + $that")
                }
            }
            else -> throw IllegalArgumentException("Invalid operation: $this + $that")
        }
    }

    operator fun minus(that: NodeValue): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        val other = if (that is BooleanValue) NumberValue(that.value.toLong()) else that
        if (expr is NumberValue && other is NumberValue) {
            return NumberValue(expr.value - other.value)
        } else {
            throw IllegalArgumentException("Invalid operation: $this - $that")
        }
    }

    operator fun times(that: NodeValue): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        val other = if (that is BooleanValue) NumberValue(that.value.toLong()) else that
        if (expr is NumberValue || other is NumberValue) {
            val (num, otherExpr) = if (expr is NumberValue) Pair(expr.asNumber()!!, other) else Pair(
                other.asNumber()!!, expr
            )
            return when (otherExpr) {
                is NumberValue -> NumberValue(num * otherExpr.value)
                is StringValue -> otherExpr.value.repeat(num.toInt()).toNodeValue()
                is ListValue -> {
                    val sz = otherExpr.value.size
                    val cnt = num.toInt()
                    val list = otherExpr.asList()!!
                    List(cnt * sz) { index -> list[index % sz] }.toNodeValue()
                }
                else -> throw IllegalArgumentException("Invalid operation: $this * $that")
            }
        } else {
            throw IllegalArgumentException("Invalid operation: $this * $that")
        }
    }

    operator fun div(that: NodeValue): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        val other = if (that is BooleanValue) NumberValue(that.value.toLong()) else that
        if (expr is NumberValue && other is NumberValue) {
            return NumberValue(expr.value / other.value)
        } else {
            throw IllegalArgumentException("Invalid operation: $this / $that")
        }
    }

    operator fun rem(that: NodeValue): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        val other = if (that is BooleanValue) NumberValue(that.value.toLong()) else that
        if (expr is NumberValue && other is NumberValue) {
            return NumberValue(expr.value % other.value)
        } else {
            throw IllegalArgumentException("Invalid operation: $this % $that")
        }
    }

    override fun equals(other: Any?): Boolean {
        return if (this is BooleanValue && other is BooleanValue) {
            this.value == other.value
        } else if (this is NumberValue && other is NumberValue) {
            this.value == other.value
        } else if (this is StringValue && other is StringValue) {
            this.value == other.value
        } else if (this is ListValue && other is ListValue) {
            this.value == other.value
        } else this is NullValue && other is NullValue
    }

    override fun hashCode(): Int {
        return when (this) {
            is BooleanValue -> value.hashCode()
            is NumberValue -> value.hashCode()
            is StringValue -> value.hashCode()
            is ListValue -> value.hashCode()
            is NullValue -> 0
            else -> throw IllegalArgumentException("Invalid operation: hashCode($this)")
        }
    }

    override operator fun compareTo(other: NodeValue): Int {
        return if (this is NumberValue && other is NumberValue) {
            this.value.compareTo(other.value)
        } else if (this is StringValue && other is StringValue) {
            this.value.compareTo(other.value)
        } else if (this is BooleanValue && other is BooleanValue) {
            this.value.compareTo(other.value)
        } else if (this is NullValue && other is NullValue) {
            0
        } else {
            throw IllegalArgumentException("Invalid operation: $this <=> $other")
        }
    }

    companion object {
        val builtinSymbols = mapOf("true" to true.toNodeValue(), "false" to false.toNodeValue(), "null" to NullValue())
    }

}

@Serializable
class StringValue(val value: String) : NodeValue(), Iterable<StringValue> {
    override fun toString() = value
    override fun toBoolean(): Boolean = value.isNotEmpty()
    override fun iterator(): Iterator<StringValue> {
        return object : Iterator<StringValue> {
            var index = 0
            override fun hasNext(): Boolean = index < value.length
            override fun next(): StringValue {
                val result = StringValue(value.substring(index, index + 1))
                index++
                return result
            }
        }
    }
}

fun String.toNodeValue() = StringValue(this)

@Serializable
class ListValue(val value: List<NodeValue>) : NodeValue(), Iterable<NodeValue> {
    override fun toString() = "[${value.joinToString(", ")}]"
    override fun toBoolean(): Boolean = value.isNotEmpty()
    val size: Int get() = value.size
    operator fun get(index: Int): NodeValue = value[index]
    override fun iterator(): Iterator<NodeValue> {
        return value.iterator()
    }
}

fun List<NodeValue>.toNodeValue() = ListValue(this)

@Serializable
class NumberValue(val value: Long) : NodeValue() {
    override fun toString() = value.toString()
    override fun toBoolean(): Boolean = value != 0L
}

fun Int.toNodeValue(): NodeValue = NumberValue(this.toLong())
fun Long.toNodeValue(): NodeValue = NumberValue(this)

@Serializable
class BooleanValue(val value: Boolean) : NodeValue() {
    override fun toString() = value.toString()
    override fun toBoolean(): Boolean = value
}

fun Boolean.toNodeValue() = BooleanValue(this)
fun Boolean.toLong() = if (this) 1L else 0L

@Serializable
class SubscriptValue(val begin: Int, val extended: Boolean, val end: Int? = null) : NodeValue() {
    override fun toString() = if (extended) "$begin:$end" else "$begin"
    override fun toBoolean(): Boolean = true
}

sealed class ProcedureValue(protected val params: List<String>) : NodeValue() {
    override fun toBoolean(): Boolean = true
    abstract fun execute(context: ExecutionContext): NodeValue

    companion object {
        private val Slice = NodeProcedureValue(
            SubscriptViewNode(
                IdentifierNode(Token(TokenType.IDENTIFIER, "this")), SubscriptNode(
                    IdentifierNode(Token(TokenType.IDENTIFIER, "begin")),
                    true,
                    IdentifierNode(Token(TokenType.IDENTIFIER, "end"))
                )
            ), listOf("this", "begin", "end")
        )
        private val whiteSpace = Pattern.compile("\\s+")
        private val Split = BuiltinProcedureValue("split", listOf("this", "separator")) { context ->
            val str = context.stack["this"]!!.asString()!!
            val arg = context.stack["separator"]?.asString()
            if (arg == null) {
                whiteSpace.split(str).filter { it.isNotEmpty() }.map { it.toNodeValue() }.toList().toNodeValue()
            } else {
                str.split(arg).map { it.toNodeValue() }.toList().toNodeValue()
            }
        }
        private val Join = BuiltinProcedureValue("join", listOf("this", "separator")) { context ->
            val list = context.stack["this"]!!.asList()!!
            val arg = context.stack["separator"]?.asString()
            if (arg == null) {
                list.joinToString(" ").toNodeValue()
            } else {
                list.joinToString(arg).toNodeValue()
            }
        }
        private val Find = BuiltinProcedureValue("find", listOf("this", "what")) { context ->
            val expr = context.stack["this"]!!
            val arg = context.stack["what"]!!
            when (expr) {
                is StringValue -> expr.value.indexOf(arg.asString()!!).toNodeValue()
                is ListValue -> expr.value.indexOf(arg).toNodeValue()
                else -> throw RuntimeException("$expr has no such method as \"find\"")
            }
        }
        private val Contains = BuiltinProcedureValue("contains", listOf("this", "what")) { context ->
            val expr = context.stack["this"]!!
            val arg = context.stack["what"]!!
            when (expr) {
                is StringValue -> expr.value.contains(arg.asString()!!).toNodeValue()
                is ListValue -> expr.value.contains(arg).toNodeValue()
                is RangeValue<*> -> expr.contains(arg).toNodeValue()
                else -> throw RuntimeException("$expr has no such method as \"contains\"")
            }
        }
        private val Length = BuiltinProcedureValue("length", listOf("this")) { context ->
            when (val expr = context.stack["this"]!!) {
                is StringValue -> expr.value.length.toNodeValue()
                is ListValue -> expr.value.size.toNodeValue()
                else -> throw RuntimeException("$expr has no such method as \"length\"")
            }
        }
        private val Time = BuiltinProcedureValue("time", listOf()) {
            System.currentTimeMillis().toNodeValue()
        }
        private val Random = BuiltinProcedureValue("random", listOf("first", "second")) { context ->
            return@BuiltinProcedureValue when (val firstValue = context.stack["first"]!!) {
                is StringValue -> { // str.random()
                    firstValue.value.random().toString().toNodeValue()
                }
                is ListValue -> { // list.random()
                    firstValue.value.random()
                }
                is NumberValue -> { // int.random()
                    val secondValue = context.stack["second"]!!.asNumber()!!
                    (firstValue.value until secondValue).random().toNodeValue()
                }
                else -> throw RuntimeException("$firstValue has no such method as \"random\"")
            }
        }
        private val Range = BuiltinProcedureValue("range", listOf("begin", "end")) { context ->
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
        }
        private val RangeInclusive = BuiltinProcedureValue("rangeInclusive", listOf("begin", "end")) { context ->
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
        }
        private val Number = BuiltinProcedureValue("number", listOf("str")) { context ->
            context.stack["str"]!!.asString()!!.toLong().toNodeValue()
        }
        private val String = BuiltinProcedureValue("string", listOf("num")) { context ->
            context.stack["num"]!!.asNumber()!!.toString().toNodeValue()
        }
        private val Abs = BuiltinProcedureValue("abs", listOf("num")) { context ->
            context.stack["num"]!!.asNumber()!!.absoluteValue.toNodeValue()
        }
        private val Enumerated = BuiltinProcedureValue("enumerated", listOf("list")) { context ->
            val list = context.stack["list"]!!
            return@BuiltinProcedureValue if (list is Iterable<*>) {
                list.mapIndexed { index, value -> ListValue(listOf(index.toNodeValue(), value as NodeValue)) }.toNodeValue()
            } else {
                throw RuntimeException("$list has no such method as \"enumerated\"")
            }
        }
        private val Ord = BuiltinProcedureValue("ord", listOf("str")) { context ->
            context.stack["str"]!!.asString()!!.first().code.toLong().toNodeValue()
        }
        private val Chr = BuiltinProcedureValue("chr", listOf("num")) { context ->
            context.stack["num"]!!.asNumber()!!.toInt().toChar().toString().toNodeValue()
        }
        private val Pow = BuiltinProcedureValue("pow", listOf("num", "exp")) { context ->
            val num = context.stack["num"]!!.asNumber()!!
            val exp = context.stack["exp"]!!.asNumber()!!
            num.toDouble().pow(exp.toDouble()).toLong().toNodeValue()
        }
        private val Sum = BuiltinProcedureValue("sum", listOf("list")) { context ->
            val list = context.stack["list"]!!
            return@BuiltinProcedureValue if (list is Iterable<*>) {
                list.sumOf { (it as NodeValue).asNumber()!! }.toNodeValue()
            } else {
                throw RuntimeException("$list has no such method as \"sum\"")
            }
        }
        private val Boolean = BuiltinProcedureValue("boolean", listOf("value")) { context ->
            context.stack["value"]!!.toBoolean().toNodeValue()
        }
        private val Filter = BuiltinProcedureValue("filter", listOf("list", "predicate")) { context ->
            fun predicateCall(it: NodeValue) = ProcedureNode.call(context, it, "predicate", ListValue(emptyList()))
            val list = context.stack["list"]!!
            return@BuiltinProcedureValue if (list is Iterable<*>) {
                @Suppress("UNCHECKED_CAST")
                (list.filter { predicateCall(it as NodeValue).toBoolean() } as List<NodeValue>).toNodeValue()
            } else {
                throw RuntimeException("$list has no such method as \"filter\"")
            }
        }
        private val Reduce = BuiltinProcedureValue("reduce", listOf("list", "initial", "reducer")) { context ->
            fun reducerCall(acc: NodeValue, it: NodeValue) = ProcedureNode.call(context, acc, "reducer", ListValue(listOf(it)))
            val list = context.stack["list"]!!
            return@BuiltinProcedureValue if (list is Iterable<*>) {
                var res = context.stack["initial"]!!
                for (i in list) {
                    res = reducerCall(res, i as NodeValue)
                }
                res
            } else {
                throw RuntimeException("$list has no such method as \"reduce\"")
            }
        }
        private val Map = BuiltinProcedureValue("map", listOf("list", "mapper")) { context ->
            fun mapperCall(it: NodeValue) = ProcedureNode.call(context, it, "mapper", ListValue(emptyList()))
            val collection = context.stack["list"]!!
            return@BuiltinProcedureValue if (collection is Iterable<*>) {
                (collection.map { mapperCall(it as NodeValue) }).toNodeValue()
            } else {
                throw RuntimeException("$collection has no such method as \"map\"")
            }
        }
        private val Max = BuiltinProcedureValue("max", listOf("list")) { context ->
            val list = context.stack["list"]!!.asList()!!
            return@BuiltinProcedureValue list.maxByOrNull { it.asNumber()!! }!!
        }
        private val Min = BuiltinProcedureValue("min", listOf("list")) { context ->
            val list = context.stack["list"]!!.asList()!!
            return@BuiltinProcedureValue list.minByOrNull { it.asNumber()!! }!!
        }
        private val Reversed = BuiltinProcedureValue("reversed", listOf("list")) { context ->
            return@BuiltinProcedureValue when(val list = context.stack["list"]!!) {
                is ListValue -> list.value.reversed().toNodeValue()
                is StringValue -> list.value.reversed().toNodeValue()
                else -> throw RuntimeException("$list has no such method as \"reversed\"")
            }
        }
        private val Sorted = BuiltinProcedureValue("sorted", listOf("list", "cmp")) { context ->
            val list = context.stack["list"]!!.asList()!!
            return@BuiltinProcedureValue if (context.stack["cmp"] == null) {
                list.sorted().toNodeValue()
            } else {
                list.sortedWith { a, b ->
                    val res = ProcedureNode.call(context, null, "cmp", ListValue(listOf(a, b)))
                    if (res.toBoolean()) {
                        1
                    } else {
                        -1
                    }
                }.toNodeValue()
            }
        }
        val builtinProcedures = mapOf(
            "slice" to Slice,
            "split" to Split,
            "join" to Join,
            "find" to Find,
            "contains" to Contains,
            "length" to Length,
            "len" to Length,
            "time" to Time,
            "random" to Random,
            "rand" to Random,
            "range" to Range,
            "rangeInclusive" to RangeInclusive,
            "number" to Number,
            "num" to Number,
            "string" to String,
            "str" to String,
            "abs" to Abs,
            "enumerated" to Enumerated,
            "enumerate" to Enumerated,
            "ord" to Ord,
            "chr" to Chr,
            "char" to Chr,
            "pow" to Pow,
            "sum" to Sum,
            "boolean" to Boolean,
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
                |Example: "hello world".find("o") // 4
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
                |""".trimMargin(),
            "range" to """
                |Create a range expression from min to max (exclusive) that can be used in for statements.
                |Usage: range(min, max)
                |Example: for i in range(1, 5) say i // 1 2 3 4
                |""".trimMargin(),
            "rangeInclusive" to """
                |Create a range expression from min to max (inclusive) that can be used in for statements.
                |Usage: rangeInclusive(min, max)
                |Example: for i in rangeInclusive(1, 5) say i // 1 2 3 4 5
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
            "abs" to """
                |Get the absolute value of a number.
                |Usage: abs(num) or num.abs()
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
                |""".trimMargin(),
            "filter" to """
                |Filter a list using a predicate.
                |Usage: list.filter(predicate)
                |Example: func even(this) this % 2 == 0; [1, 2, 3, 4, 5].filter(even) // [2, 4]
                |""".trimMargin(),
            "reduce" to """
                |Reduce a list using a function.
                |Usage: list.reduce(initial, function)
                |Example: func sum(this, that) this + that; [1, 2, 3].reduce(0, sum) // 6
                |""".trimMargin(),
            "map" to """
                |Map a list using a function.
                |Usage: list.map(function)
                |Example: func square(this) this * this; [1, 2, 3].map(square) // [1, 4, 9]
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
                |Example: func reverse(this, that) this < that; [1, 2, 3].sorted(reverse) // [3, 2, 1]
                |""".trimMargin(),
        )
    }
}

class BuiltinProcedureValue(
    private val name: String, params: List<String>, private val func: (context: ExecutionContext) -> NodeValue
) : ProcedureValue(params) {
    override fun toString(): String = "builtin($name)"
    override fun execute(context: ExecutionContext): NodeValue {
        context.stack.nameArgs(params)
        return func(context)
    }
}

class NodeProcedureValue(private val func: Node, params: List<String>) : ProcedureValue(params) {
    override fun toString() = "procedure($func)"
    override fun execute(context: ExecutionContext): NodeValue {
        context.stack.nameArgs(params)
        return func.exec(context)
    }
}

@Serializable
sealed class RangeValue<T : NodeValue>(
    protected val begin: T, protected val end: T, protected val inclusive: Boolean
) : NodeValue(), Iterable<T> {
    override fun toBoolean() = true
    override fun toString() = "range($begin, $end)"
    abstract fun contains(value: T): Boolean
}

@Serializable(with = NumberRangeValue.Serializer::class)
class NumberRangeValue(begin: NumberValue, end: NumberValue, inclusive: Boolean) :
    RangeValue<NumberValue>(begin, end, inclusive) {
    override fun iterator(): Iterator<NumberValue> {
        return object : Iterator<NumberValue> {
            var current = begin
            override fun hasNext(): Boolean {
                return if (inclusive) current <= end else current < end
            }

            override fun next(): NumberValue {
                val result = current
                current = NumberValue(current.value + 1)
                return result
            }
        }
    }
    override fun contains(value: NumberValue): Boolean {
        return if (inclusive) {
            value.value in (begin.value..end.value)
        } else {
            value.value in (begin.value until end.value)
        }
    }
    class Serializer : KSerializer<NumberRangeValue> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("top.saucecode.NumberRangeValue") {
            element<NumberValue>("begin")
            element<NumberValue>("end")
            element<Boolean>("inclusive")
        }
        override fun deserialize(decoder: Decoder): NumberRangeValue = decoder.decodeStructure(descriptor) {
            NumberRangeValue(
                begin = decodeSerializableElement(descriptor, 0, NumberValue.serializer()),
                end = decodeSerializableElement(descriptor, 1, NumberValue.serializer()),
                inclusive = decodeBooleanElement(descriptor, 2)
            )
        }
        override fun serialize(encoder: Encoder, value: NumberRangeValue) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, NumberValue.serializer(), value.begin)
            encodeSerializableElement(descriptor, 1, NumberValue.serializer(), value.end)
            encodeBooleanElement(descriptor, 2, value.inclusive)
        }
    }
}

@Serializable(with = CharRangeValue.Serializer::class)
class CharRangeValue(begin: StringValue, end: StringValue, inclusive: Boolean) :
    RangeValue<StringValue>(begin, end, inclusive) {
    override fun iterator(): Iterator<StringValue> {
        return object : Iterator<StringValue> {
            var current = begin.value[0]
            override fun hasNext(): Boolean {
                return if (inclusive) current <= end.value[0] else current < end.value[0]
            }

            override fun next(): StringValue {
                val result = current
                current++
                return StringValue(result.toString())
            }
        }
    }

    override fun contains(value: StringValue): Boolean {
        return if (inclusive) {
            value.value[0] in (begin.value[0]..end.value[0])
        } else {
            value.value[0] in (begin.value[0] until end.value[0])
        }
    }
    class Serializer : KSerializer<CharRangeValue> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("top.saucecode.CharRangeValue") {
            element<NumberValue>("begin")
            element<NumberValue>("end")
            element<Boolean>("inclusive")
        }
        override fun deserialize(decoder: Decoder): CharRangeValue = decoder.decodeStructure(descriptor) {
            CharRangeValue(
                begin = decodeSerializableElement(descriptor, 0, StringValue.serializer()),
                end = decodeSerializableElement(descriptor, 1, StringValue.serializer()),
                inclusive = decodeBooleanElement(descriptor, 2)
            )
        }
        override fun serialize(encoder: Encoder, value: CharRangeValue) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, StringValue.serializer(), value.begin)
            encodeSerializableElement(descriptor, 1, StringValue.serializer(), value.end)
            encodeBooleanElement(descriptor, 2, value.inclusive)
        }
    }
}

@Serializable
class NullValue : NodeValue() {
    override fun toString() = "null"
    override fun toBoolean(): Boolean = false
}

class Scope(private val symbols: MutableMap<String, NodeValue>, val args: ListValue = ListValue(emptyList())) {
    operator fun get(name: String): NodeValue? {
        return symbols[name]
    }
    operator fun set(name: String, value: NodeValue) {
        symbols[name] = value
    }
    fun remove(name: String) {
        symbols.remove(name)
    }
    override fun toString(): String {
        return "Scope(symbols=$symbols, args=$args)"
    }
    companion object {
        fun createRoot(defs: Map<String, NodeValue> = mapOf()): Scope {
            val builtins = defs.toMutableMap()
            return Scope(builtins)
        }
        fun deserialize(input: String): Scope {
            val dict = Json.decodeFromString<MutableMap<String, String>>(serializer(), input)
            val reconstructed = mutableMapOf<String, NodeValue>()
            try {
                dict.forEach { (key, value) ->
                    reconstructed[key] = Json.decodeFromString(NodeValue.serializer(), value)
                }
            } catch (_: Exception) {
            }
            return Scope(reconstructed)
        }
    }
    fun serialize(): String {
        val filteredSymbols = mutableMapOf<String, String>()
        for ((key, value) in symbols) {
            try {
                val jsonValue = Json.encodeToString(value)
                filteredSymbols[key] = jsonValue
            } catch (_: Exception) {

            }
        }
        return Json.encodeToString(filteredSymbols)
    }
}
typealias SymbolTable = Scope

class Stack(rootScope: Scope, private val declarations: MutableMap<String, NodeValue>) {
    private val scopes: MutableList<Scope>

    init {
        scopes = mutableListOf(rootScope)
    }

    // The first argument must be the value of "this"
    fun push(args: ListValue = emptyList<NodeValue>().toNodeValue()) {
        scopes.add(Scope(mutableMapOf(), args))
    }

    fun pop() {
        scopes.removeAt(scopes.lastIndex)
    }

    private val args: ListValue
        get() = scopes.last().args

    fun nameArgs(params: List<String>) {
        val argc = min(params.size, args.size)
        for (i in 0 until argc) {
            scopes.last()[params[i]] = args[i]
        }
        if (argc > params.size) {
            scopes.last()["__var_args__"] = args.value.slice(params.size until args.size).toList().toNodeValue()
        }
    }

    operator fun get(name: String): NodeValue? {
        for (scope in scopes.reversed()) {
            val value = scope[name]
            if (value != null) {
                return value
            }
        }
        return declarations[name] ?: NodeValue.builtinSymbols[name] ?: ProcedureValue.builtinProcedures[name]
    }

    operator fun set(name: String, value: NodeValue) {
        if (name[0] == '$') {
            scopes.last()[name] = value
        } else {
            for (scope in scopes.reversed()) {
                if (scope[name] != null) {
                    scope[name] = value
                    return
                }
            }
            scopes.last()[name] = value
        }
    }
}

abstract class Node {
    abstract fun exec(context: ExecutionContext): NodeValue
    open fun assign(context: ExecutionContext, value: NodeValue): Unit =
        throw IllegalArgumentException("Not assignable: ${this.javaClass.simpleName}")
}

class ValueNode(private val value: NodeValue) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        return value
    }

    override fun toString(): String {
        return value.toString()
    }
}

class IdentifierNode(token: Token) : Node() {
    val name: String

    init {
        if (token.type != TokenType.IDENTIFIER) {
            throw IllegalArgumentException("Expected IDENTIFIER, got ${token.type}")
        }
        name = token.value
    }

    override fun exec(context: ExecutionContext): NodeValue {
        return context.stack[name] ?: NullValue()
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        context.stack[name] = value
    }

    override fun toString(): String {
        return "id($name)"
    }

}

class NumberNode(token: Token) : Node() {
    private val value: Int

    init {
        if (token.type != TokenType.NUMBER) {
            throw IllegalArgumentException("Expected NUMBER, got ${token.type}")
        }
        value = token.value.toInt()
    }

    override fun exec(context: ExecutionContext): NodeValue {
        return value.toNodeValue()
    }

    override fun toString(): String {
        return "num($value)"
    }
}

class StringNode(token: Token) : Node() {
    private val value: String

    init {
        if (token.type != TokenType.STRING) {
            throw IllegalArgumentException("Expected STRING, got ${token.type}")
        }
        value = token.value
    }

    override fun exec(context: ExecutionContext): NodeValue {
        return value.toNodeValue()
    }

    override fun toString(): String {
        return "str(\"$value\")"
    }
}

class ListNode(private val items: List<Node>) : Node() {
    override fun exec(context: ExecutionContext): ListValue {
        return items.map { it.exec(context) }.toNodeValue()
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        val list = value.asList()
        if (list != null) {
            val cnt = min(items.size, list.size)
            for (i in 0 until cnt) {
                items[i].assign(context, list[i])
            }
        }
    }

    override fun toString(): String {
        return "[${items.joinToString(", ")}]"
    }
}

class SubscriptNode(private val begin: Node, private val extended: Boolean, private val end: Node? = null) : Node() {
    override fun exec(context: ExecutionContext): SubscriptValue {
        return SubscriptValue(
            begin.exec(context).asNumber()!!.toInt(), extended, end?.exec(context)?.asNumber()?.toInt()
        )
    }

    override fun toString(): String {
        return if (end != null) "subscript($begin, $end)" else "subscript($begin)"
    }
}

class SubscriptViewNode(private val list: Node, private val subscripts: List<SubscriptNode>) : Node() {
    constructor(
        existing: Node, subscript: SubscriptNode
    ) : this(
        if (existing is SubscriptViewNode) existing.list else existing,
        if (existing is SubscriptViewNode) existing.subscripts + subscript else listOf(subscript)
    )

    private class RangeHierarchy(list: NodeValue, subscripts: List<SubscriptValue>) {
        val isEmpty: Boolean
        val pointers: List<Int>
        val endsAtString: Boolean
        val lastSlice: IntRange?

        init {
            var currentList = list
            var isEmpty = false
            val pointers = mutableListOf<Int>()
            var endsAtString = false
            var lastRecursion = false
            var lastSlice: IntRange? = null

            for (subscript in subscripts) {
                if (lastRecursion) {
                    isEmpty = true
                    break
                }
                when (currentList) {
                    is ListValue -> {
                        val range = lastSlice ?: currentList.value.indices
                        if (subscript.extended) {
                            val slice = range.safeSlice(subscript.begin, subscript.end)
                            if (slice != null) {
                                lastSlice = slice
                            } else {
                                isEmpty = true
                                break
                            }
                        } else {
                            val index = range.safeSubscript(subscript.begin)
                            if (index != null) {
                                pointers.add(index)
                                lastSlice = null
                                currentList = currentList.value[index]
                            } else {
                                isEmpty = true
                                break
                            }
                        }
                    }
                    is StringValue -> {
                        endsAtString = true
                        val range = lastSlice ?: currentList.value.indices
                        if (subscript.extended) {
                            val slice = range.safeSlice(subscript.begin, subscript.end)
                            if (slice != null) {
                                lastSlice = slice
                            } else {
                                isEmpty = true
                                break
                            }
                        } else {
                            val index = range.safeSubscript(subscript.begin)
                            if (index != null) {
                                lastSlice = IntRange(index, index)
                                lastRecursion = true
                            } else {
                                isEmpty = true
                                break
                            }
                        }
                    }
                    else -> throw RuntimeException("Failed subscripting on $currentList[$subscript]")
                }
            }
            this.isEmpty = isEmpty
            this.pointers = pointers
            this.endsAtString = endsAtString
            this.lastSlice = lastSlice
        }

        fun containPath(path: List<Int>): Boolean {
            if (path.size != pointers.size) {
                return false
            }
            for (i in pointers.indices) {
                if (pointers[i] != path[i]) {
                    return false
                }
            }
            return true
        }
    }

    override fun exec(context: ExecutionContext): NodeValue {
        var list = list.exec(context)
        val subscripts = subscripts.map { it.exec(context) }
        val hierarchy = RangeHierarchy(list, subscripts)
        if (hierarchy.isEmpty) return NullValue()
        for (pointer in hierarchy.pointers) {
            list = list.asList()!![pointer]
        }
        return if (hierarchy.endsAtString) {
            val string = list.asString()!!
            StringValue(string.substring(hierarchy.lastSlice!!))
        } else {
            if (hierarchy.lastSlice != null) {
                ListValue(list.asList()!!.slice(hierarchy.lastSlice))
            } else {
                list
            }
        }
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        if (list !is IdentifierNode) {
            throw RuntimeException("Can only assign to identifiers, not $list")
        }
        val listValue = list.exec(context)
        val subscripts = subscripts.map { it.exec(context) }
        val hierarchy = RangeHierarchy(listValue, subscripts)
        if (hierarchy.isEmpty) throw RuntimeException("Failed subscripting on $list${subscripts.joinToString("") { "[$it]" }}")
        val path = mutableListOf<Int>()
        fun copyValue(cur: NodeValue): NodeValue {
            if (hierarchy.containPath(path)) {
                // Do the real assignment
                if (hierarchy.endsAtString) {
                    val string = cur.asString()!!
                    val slice = hierarchy.lastSlice!!
                    val newString = if (slice.first > 0) string.substring(
                        0, slice.first
                    ) else "" + value.toString() + if (slice.last + 1 < string.length) string.substring(slice.last + 1) else ""
                    return StringValue(newString)
                } else {
                    if (hierarchy.lastSlice != null) {
                        val list = cur.asList()!!
                        val slice = hierarchy.lastSlice
                        val newList = mutableListOf<NodeValue>()
                        if (slice.first > 0) {
                            newList.addAll(list.slice(0 until slice.first))
                        }
                        when (value) {
                            is ListValue -> {
                                newList.addAll(value.value)
                            }
                            else -> {
                                newList.add(value)
                            }
                        }
                        if (slice.last + 1 < list.size) {
                            newList.addAll(list.slice(slice.last + 1 until list.size))
                        }
                        return ListValue(newList)
                    } else {
                        return value
                    }
                }
            } else {
                return if (cur is ListValue) {
                    val newList = mutableListOf<NodeValue>()
                    for (i in cur.value.indices) {
                        path.add(i)
                        newList.add(copyValue(cur.value[i]))
                        path.removeLast()
                    }
                    ListValue(newList)
                } else {
                    cur
                }
            }
        }

        val newValue = copyValue(listValue)
        list.assign(context, newValue)
    }

    override fun toString(): String {
        return "listview($list, ${subscripts.joinToString("") { "[$it]" }})"
    }
}

class ProcedureNode(private val caller: Node?, private val func: IdentifierNode, private val args: ListNode) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val args = args.exec(context).value
        return when (func.name) {
            "defined" -> {
                val idName = (caller as IdentifierNode).name
                (context.stack[idName] != null).toNodeValue()
            }
            else -> {
                val procedure = context.stack[func.name]!!.asProcedure()!!
                val res: NodeValue
                val callerValue = caller?.exec(context)?.let { listOf(it) }
                try {
                    context.stack.push((if (callerValue == null) args else callerValue + args).toNodeValue())
                    res = procedure.execute(context)
                } finally {
                    context.stack.pop()
                }
                res
            }
        }
    }

    companion object {
        fun call(context: ExecutionContext, caller: NodeValue?, funcName: String, args: ListValue): NodeValue {
            return ProcedureNode(
                caller?.toNode(),
                IdentifierNode(Token(TokenType.IDENTIFIER, funcName)),
                ListNode(args.value.map { it.toNode() })
            ).exec(context)
        }
    }

    override fun toString(): String {
        return if (caller == null) "$func($args)" else "$caller.$func($args)"
    }
}

class FactorNode(private val units: List<Node>, private val ops: List<String>, private val prefix: FactorPrefix) :
    Node() {
    enum class FactorPrefix {
        NONE, NOT, NEGATIVE
    }

    override fun exec(context: ExecutionContext): NodeValue {
        val values = units.toMutableList()
        val ops = ops.toMutableList()
        var res = values.removeAt(0).exec(context)
        while (values.size > 0) {
            val next = values.removeAt(0).exec(context)
            val op = ops.removeAt(0)
            res = when (op) {
                "*" -> res * next
                "/" -> res / next
                "%" -> res % next
                else -> throw IllegalArgumentException("Unknown operator $op")
            }
        }
        when (prefix) {
            FactorPrefix.NONE -> return res
            FactorPrefix.NOT -> return res.toBoolean().not().toNodeValue()
            FactorPrefix.NEGATIVE -> {
                if (res is NumberValue) {
                    res = res.value.unaryMinus().toNodeValue()
                } else {
                    throw IllegalArgumentException("Invalid operation: -$res")
                }
            }
        }
        return res
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        if (prefix == FactorPrefix.NONE && units.size == 1) {
            units[0].assign(context, value)
        } else {
            throw IllegalArgumentException("Invalid assignment: $this = $value")
        }
    }

    override fun toString(): String {
        var str = when (prefix) {
            FactorPrefix.NONE -> ""
            FactorPrefix.NOT -> "!"
            FactorPrefix.NEGATIVE -> "-"
        }
        if(units.size == 1) return "$str${units[0]}"
        for (i in units.indices) {
            str += units[i].toString()
            if (i < ops.size) {
                str += ops[i]
            }
        }
        return "factor($str)"
    }
}

class TermNode(private val factors: List<Node>, private val ops: List<String>) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val values = factors.toMutableList()
        val ops = ops.toMutableList()
        var res = values.removeAt(0).exec(context)
        while (values.size > 0) {
            val next = values.removeAt(0).exec(context)
            val op = ops.removeAt(0)
            res = when (op) {
                "+" -> res + next
                "-" -> res - next
                else -> throw IllegalArgumentException("Unknown operator $op")
            }
        }
        return res
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        if (factors.size == 1) {
            factors[0].assign(context, value)
        } else {
            throw IllegalArgumentException("Invalid assignment: $this = $value")
        }
    }

    override fun toString(): String {
        var str = ""
        if (factors.size == 1) return "${factors[0]}"
        for (i in factors.indices) {
            str += factors[i].toString()
            if (i < ops.size) {
                str += ops[i]
            }
        }
        return "term($str)"
    }
}

class CompNode(private val terms: List<Node>, private val ops: List<String>) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val values = terms.toMutableList()
        val ops = ops.toMutableList()
        var res = values.removeAt(0).exec(context)
        while (values.size > 0) {
            val next = values.removeAt(0).exec(context)
            res = when (val op = ops.removeAt(0)) {
                "==" -> (res == next).toNodeValue()
                "!=" -> (res != next).toNodeValue()
                ">" -> (res > next).toNodeValue()
                "<" -> (res < next).toNodeValue()
                ">=" -> (res >= next).toNodeValue()
                "<=" -> (res <= next).toNodeValue()
                else -> throw IllegalArgumentException("Unknown operator $op")
            }
        }
        return res
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        if (terms.size == 1) {
            terms[0].assign(context, value)
        } else {
            throw IllegalArgumentException("Invalid assignment: $this = $value")
        }
    }

    override fun toString(): String {
        var str = ""
        if (terms.size == 1) return "${terms[0]}"
        for (i in terms.indices) {
            str += terms[i].toString()
            if (i < ops.size) {
                str += ops[i]
            }
        }
        return "comp($str)"
    }
}

class ExprNode(private val comps: List<Node>, private val ops: List<String>) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        if (comps.size == 1) {
            return comps[0].exec(context)
        }
        val values = comps.toMutableList()
        val ops = ops.toMutableList()
        var res = values.removeAt(0).exec(context).toBoolean()
        while (values.size > 0) {
            val next = values.removeAt(0)
            res = when (val op = ops.removeAt(0)) {
                "&&" -> res && next.exec(context).toBoolean()
                "||" -> res || next.exec(context).toBoolean()
                else -> throw IllegalArgumentException("Invalid operation: $res $op $next")
            }
        }
        return res.toNodeValue()
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        if (comps.size == 1) {
            comps[0].assign(context, value)
        } else {
            throw IllegalArgumentException("Invalid assignment: $this = $value")
        }
    }

    override fun toString(): String {
        var str = ""
        if (comps.size == 1) return "${comps[0]}"
        for (i in comps.indices) {
            str += comps[i].toString()
            if (i < ops.size) {
                str += ops[i]
            }
        }
        return "expr($str)"
    }
}

class StmtAssignNode(private val lvalue: Node, private val expr: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val value = expr.exec(context)
        lvalue.assign(context, value)
        return lvalue.exec(context)
    }

    override fun toString(): String {
        return "assign($lvalue, $expr)"
    }
}

class StmtActionNode(private val action: Token, private val expr: ExprNode) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        if (action.type != TokenType.ACTION) {
            throw IllegalArgumentException("Expected ACTION, got ${action.type}")
        }
        val value = expr.exec(context)
        when (action.value) {
            "say" -> {
                context.say(value.toString())
            }
            else -> throw IllegalArgumentException("Unknown action ${action.value}")
        }
        return NullValue()
    }

    override fun toString(): String {
        return "action(${action.value}, $expr)"
    }
}

class StmtIfNode(
    private val condition: Node, private val ifBody: Node, private val elseBody: Node? = null
) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        return if (condition.exec(context).toBoolean()) {
            ifBody.exec(context)
        } else {
            elseBody?.exec(context) ?: NullValue()
        }
    }

    override fun toString(): String {
        val elseText = if (elseBody == null) "" else ", else($elseBody)"
        return "if($condition, body($ifBody)$elseText)"
    }
}

class StmtInitNode(private val stmt: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        if (context.firstRun) {
            stmt.exec(context)
        }
        return NullValue()
    }

    override fun toString(): String {
        return "init($stmt)"
    }
}

class ReturnException(val value: NodeValue) : Exception()
class StmtFuncNode(private val content: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val res: NodeValue?
        try {
            res = content.exec(context)
        } catch (e: ReturnException) {
            return e.value
        }
        return res
    }

    override fun toString(): String {
        return "func($content)"
    }
}

class StmtReturnNode(private val expr: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        throw ReturnException(expr.exec(context))
    }

    override fun toString(): String {
        return "return($expr)"
    }
}

class StmtWhileNode(private val condition: Node, private val body: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        while (condition.exec(context).toBoolean()) {
            try {
                body.exec(context)
            } catch (continueEx: ContinueException) {
                continue
            } catch (breakEx: BreakException) {
                break
            }
        }
        return NullValue()
    }

    override fun toString(): String {
        return "while($condition, $body)"
    }
}

class ContinueException : Exception()
class StmtContinueNode : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        throw ContinueException()
    }

    override fun toString(): String {
        return "continue"
    }
}

class BreakException : Exception()
class StmtBreakNode : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        throw BreakException()
    }

    override fun toString(): String {
        return "break"
    }
}

class StmtForNode(private val iterator: Node, private val collection: Node, private val body: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val collection = collection.exec(context)
        var res: NodeValue = NullValue()
        if (collection is Iterable<*>) {
            for (item in collection) {
                iterator.assign(context, item as NodeValue)
                try {
                    res = body.exec(context)
                } catch (continueEx: ContinueException) {
                    continue
                } catch (breakEx: BreakException) {
                    break
                }
            }
        } else {
            throw RuntimeException("$collection is not iterable")
        }
        return res
    }
}

class StmtListNode(private val stmts: List<Node>, private val newScope: Boolean) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        var res: NodeValue = NullValue()
        try {
            if (newScope) {
                context.stack.push()
            }
            for (node in stmts) {
                res = node.exec(context)
            }
        } finally {
            if (newScope) {
                context.stack.pop()
            }
        }
        return res
    }

    override fun toString(): String {
        return "stmts(${stmts.joinToString(", ")})"
    }
}

class UnexpectedTokenException(val token: Token, private val expected: TokenType? = null) : Exception() {
    override fun toString(): String {
        return if (expected != null) "Unexpected token $token, expected $expected" else "Unexpected token $token"
    }
    override val message: String
        get() = toString()
}

class Parser(private val tokens: List<Token>) {
    private var current = 0

    private fun consume(type: TokenType): Token {
        if (isAtEnd()) {
            throw IllegalStateException("Unexpected end of input")
        }
        if (peek().type != type) {
            throw UnexpectedTokenException(peek(), type)
        }
        return tokens[current++]
    }

    private fun consumeLineBreak() {
        while (true) {
            when (peek().type) {
                TokenType.SEMICOLON -> consume(TokenType.SEMICOLON)
                TokenType.NEWLINE -> consume(TokenType.NEWLINE)
                else -> return
            }
        }
    }

    private fun isAtEnd() = current >= tokens.size
    private fun peek() = tokens[current]
//    private fun peekNext() = tokens[current + 1]

    private fun parseIdentifier() = IdentifierNode(consume(TokenType.IDENTIFIER))
    private fun parseNumber() = NumberNode(consume(TokenType.NUMBER))
    private fun parseString() = StringNode(consume(TokenType.STRING))

    private fun parseStmtList(newScope: Boolean): StmtListNode {
        val stmts = mutableListOf<Node>()
        consumeLineBreak()
        while (peek().type != TokenType.EOF && peek().type != TokenType.BRACE_CLOSE) {
            stmts.add(parseStmt())
        }
        return StmtListNode(stmts, newScope)
    }

    // calls consumeLineBreak() in the end of this function
    private fun parseStmt(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.ACTION -> {
                parseStmtAction()
            }
            TokenType.IF -> {
                parseStmtIf()
            }
            TokenType.INIT -> {
                consume(TokenType.INIT)
                consumeLineBreak()
                StmtInitNode(parseStmt())
            }
            TokenType.BRACE_OPEN -> {
                consume(TokenType.BRACE_OPEN)
                val stmtList = parseStmtList(true)
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                stmtList
            }
            TokenType.FUNC -> {
                consume(TokenType.FUNC)
                val func = parseIdentifier()
                consume(TokenType.PAREN_OPEN)
                val params = mutableListOf<IdentifierNode>()
                if (peek().type != TokenType.PAREN_CLOSE) {
                    params.add(parseIdentifier())
                    while (peek().type != TokenType.PAREN_CLOSE) {
                        consume(TokenType.COMMA)
                        params.add(parseIdentifier())
                    }
                }
                consume(TokenType.PAREN_CLOSE)
                consumeLineBreak()
                val body = parseStmt()
                declarations[func.name] = NodeProcedureValue(StmtFuncNode(body), params.map { it.name })
                func
            }
            TokenType.RETURN -> {
                consume(TokenType.RETURN)
                consumeLineBreak()
                StmtReturnNode(parseExpr())
            }
            TokenType.WHILE -> {
                consume(TokenType.WHILE)
                val condition = parseExpr()
                consumeLineBreak()
                val body = parseStmt()
                StmtWhileNode(condition, body)
            }
            TokenType.CONTINUE -> {
                consume(TokenType.CONTINUE)
                consumeLineBreak()
                StmtContinueNode()
            }
            TokenType.BREAK -> {
                consume(TokenType.BREAK)
                consumeLineBreak()
                StmtBreakNode()
            }
            TokenType.FOR -> {
                consume(TokenType.FOR)
                val iterator = parseExpr()
                consume(TokenType.IN)
                val collection = parseExpr()
                consumeLineBreak()
                val body = parseStmt()
                StmtForNode(iterator, collection, body)
            }
            else -> {
                val expr = parseExpr()
                if (peek().type == TokenType.ASSIGN) {
                    parseStmtAssign(expr)
                } else {
                    consumeLineBreak()
                    expr
                }
            }
        }
    }

    private fun parseStmtAssign(lvalue: Node): Node {
        val assignToken = consume(TokenType.ASSIGN)
        val stmt = when (assignToken.value) {
            "=" -> StmtAssignNode(lvalue, parseExpr())
            "+=" -> StmtAssignNode(lvalue, TermNode(listOf(lvalue, parseExpr()), listOf("+")))
            "-=" -> StmtAssignNode(lvalue, TermNode(listOf(lvalue, parseExpr()), listOf("-")))
            "*=" -> StmtAssignNode(
                lvalue, FactorNode(listOf(lvalue, parseExpr()), listOf("*"), FactorNode.FactorPrefix.NONE)
            )
            "/=" -> StmtAssignNode(
                lvalue, FactorNode(listOf(lvalue, parseExpr()), listOf("/"), FactorNode.FactorPrefix.NONE)
            )
            "%=" -> StmtAssignNode(
                lvalue, FactorNode(listOf(lvalue, parseExpr()), listOf("%"), FactorNode.FactorPrefix.NONE)
            )
            else -> throw UnexpectedTokenException(assignToken)
        }
        consumeLineBreak()
        return stmt
    }

    private fun parseStmtAction(): Node {
        val action = consume(TokenType.ACTION)
        val expr = parseExpr()
        consumeLineBreak()
        return StmtActionNode(action, expr)
    }

    private fun parseStmtIf(): Node {
        consume(TokenType.IF)
        val condition = parseExpr()
        consumeLineBreak()
        val ifBody = parseStmt()
        if (peek().type == TokenType.ELSE) {
            consume(TokenType.ELSE)
            consumeLineBreak()
            val elseBody = parseStmt()
            return StmtIfNode(condition, ifBody, elseBody)
        }
        return StmtIfNode(condition, ifBody)
    }

    private fun parseExpr(): ExprNode {
        val comps = mutableListOf(parseComp())
        val ops = mutableListOf<String>()
        while (true) {
            val token = peek()
            when (token.type) {
                TokenType.LOGIC_OP -> {
                    ops.add(consume(TokenType.LOGIC_OP).value)
                    comps.add(parseComp())
                }
                else -> return ExprNode(comps, ops)
            }
        }
    }

    private fun parseComp(): CompNode {
        val terms = mutableListOf(parseTerm())
        val ops = mutableListOf<String>()
        while (true) {
            val token = peek()
            when (token.type) {
                TokenType.COMP_OP -> {
                    ops.add(consume(TokenType.COMP_OP).value)
                    terms.add(parseTerm())
                }
                else -> return CompNode(terms, ops)
            }
        }
    }

    private fun parseTerm(): TermNode {
        val factors = mutableListOf(parseFactor())
        val ops = mutableListOf<String>()
        while (true) {
            val token = peek()
            when (token.type) {
                TokenType.ADD_OP -> {
                    ops.add(consume(TokenType.ADD_OP).value)
                    factors.add(parseFactor())
                }
                else -> return TermNode(factors, ops)
            }
        }
    }

    private fun parseFactor(): FactorNode {
        var prefix = FactorNode.FactorPrefix.NONE
        if (peek().type == TokenType.ADD_OP && peek().value == "-") {
            consume(TokenType.ADD_OP)
            prefix = FactorNode.FactorPrefix.NEGATIVE
        } else if (peek().type == TokenType.NOT) {
            consume(TokenType.NOT)
            prefix = FactorNode.FactorPrefix.NOT
        }
        val units = mutableListOf(parseUnit())
        val ops = mutableListOf<String>()
        while (true) {
            val token = peek()
            when (token.type) {
                TokenType.MULT_OP -> {
                    ops.add(consume(TokenType.MULT_OP).value)
                    units.add(parseUnit())
                }
                else -> return FactorNode(units, ops, prefix)
            }
        }
    }

    private fun parseUnitHead(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.IDENTIFIER -> {
                val identifier = parseIdentifier()
                if (peek().type == TokenType.PAREN_OPEN) {
                    consume(TokenType.PAREN_OPEN)
                    val arguments = parseParamList()
                    consume(TokenType.PAREN_CLOSE)
                    ProcedureNode(null, identifier, arguments)
                } else {
                    identifier
                }
            }
            TokenType.NUMBER -> parseNumber()
            TokenType.STRING -> parseString()
            TokenType.PAREN_OPEN -> {
                consume(TokenType.PAREN_OPEN)
                val expr = parseExpr()
                consume(TokenType.PAREN_CLOSE)
                expr
            }
            TokenType.BRACKET_OPEN -> {
                consume(TokenType.BRACKET_OPEN)
                val list = parseParamList()
                consume(TokenType.BRACKET_CLOSE)
                list
            }
            else -> throw UnexpectedTokenException(token)
        }
    }

    private fun parseParamList(): ListNode {
        val params = mutableListOf<ExprNode>()
        if (peek().type != TokenType.PAREN_CLOSE && peek().type != TokenType.BRACKET_CLOSE) {
            params.add(parseExpr())
            while (peek().type != TokenType.PAREN_CLOSE && peek().type != TokenType.BRACKET_CLOSE) {
                consume(TokenType.COMMA)
                params.add(parseExpr())
            }
        }
        return ListNode(params)
    }

    private fun parseUnitTail(unitHead: Node): Node {
        val token = peek()
        return when (token.type) {
            TokenType.DOT -> {
                consume(TokenType.DOT)
                val func = IdentifierNode(consume(TokenType.IDENTIFIER))
                consume(TokenType.PAREN_OPEN)
                val params = parseParamList()
                consume(TokenType.PAREN_CLOSE)
                ProcedureNode(unitHead, func, params)
            }
            TokenType.BRACKET_OPEN -> {
                consume(TokenType.BRACKET_OPEN)
                val begin = parseExpr()
                val subscript = if (peek().type == TokenType.COLON) {
                    consume(TokenType.COLON)
                    if (peek().type == TokenType.BRACKET_CLOSE) {
                        SubscriptNode(begin, true, null)
                    } else {
                        SubscriptNode(begin, true, parseExpr())
                    }
                } else {
                    SubscriptNode(begin, false)
                }
                consume(TokenType.BRACKET_CLOSE)
                SubscriptViewNode(unitHead, subscript)
            }
            else -> unitHead
        }
    }

    private fun parseUnit(): Node {
        var unit = parseUnitHead()
        while (peek().type == TokenType.DOT || peek().type == TokenType.BRACKET_OPEN) {
            unit = parseUnitTail(unit)
        }
        return unit
    }

    private var declarations: MutableMap<String, NodeValue> = mutableMapOf()
    fun parse(): Pair<Node, MutableMap<String, NodeValue>> {
        declarations = mutableMapOf()
        return Pair(parseStmtList(false), declarations)
    }
}

abstract class ExecutionContext(rootScope: Scope, declarations: MutableMap<String, NodeValue>, val firstRun: Boolean) {
    val stack: Stack

    init {
        stack = Stack(rootScope, declarations)
    }

    abstract fun say(text: String)
}

class ConsoleContext(rootScope: Scope? = null, declarations: MutableMap<String, NodeValue>) :
    ExecutionContext(rootScope ?: Scope.createRoot(), declarations, true) {
    override fun say(text: String) {
        println(text)
    }
}

class ControlledContext(rootScope: Scope, declarations: MutableMap<String, NodeValue>, firstRun: Boolean) :
    ExecutionContext(rootScope, declarations, firstRun) {
    private val record = mutableListOf<String>()
    override fun say(text: String) {
        record.add(text)
    }

    fun dumpOutput(): String {
        val str = record.joinToString("\n")
        record.clear()
        return str
    }
}

class Interpreter(source: String, private val restricted: Boolean) {
    private val ast: Node
    val declarations: MutableMap<String, NodeValue>

    init {
        val tokens = Tokenizer(source).scan()
        val parser = Parser(tokens)
        val res = parser.parse()
        ast = res.first
        declarations = res.second
    }

    fun run(context: ExecutionContext) {
        if (restricted) {
            val task = CompletableFuture.runAsync {
                ast.exec(context)
            }.orTimeout(800, TimeUnit.MILLISECONDS)
            task.get()
        } else {
            ast.exec(context)
        }
    }
}

class REPL {
    val rootScope = Scope.createRoot()
    private val declarations = mutableMapOf<String, NodeValue>()

    fun run() {
        val inputs = mutableListOf<String>()
        while (true) {
            print("> ")
            val input = readLine() ?: break
            if (input.isEmpty()) continue
            if (input == "exit" || input == "stop") break
            inputs.add(input)
            val ast = try {
                val node = Parser(Tokenizer(inputs.joinToString("\n")).scan()).parse()
                node
            } catch (e: UnexpectedTokenException) {
                if (e.token.type == TokenType.EOF) {
                    continue
                } else {
                    println("Compile Error: ${e.message}")
                    inputs.clear()
                    continue
                }
            } catch (e: Exception) {
                println("Compile Error: ${e.message}")
                inputs.clear()
                continue
            }
            inputs.clear()
            declarations.putAll(ast.second)
            val context = ControlledContext(rootScope, declarations, true)
            try {
                val res = ast.first.exec(context)
                val output = context.dumpOutput()
                if (output.isNotEmpty()) {
                    println(output)
                } else {
                    println(res)
                }
            } catch (e: Exception) {
                println("Runtime Error: ${e.message}")
            }
        }
    }
}
