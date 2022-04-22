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
    ACTION, IDENTIFIER, NUMBER, STRING, EOF
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
                currentChar == '*' -> handleTwoCharOp(TokenType.ASSIGN, "*=", TokenType.MULT)
                currentChar == '%' -> handleTwoCharOp(TokenType.ASSIGN, "%=", TokenType.MOD)
                currentChar == '+' -> handleTwoCharOp(TokenType.ASSIGN, "+=", TokenType.PLUS)
                currentChar == '-' -> {
                    if (index < input.length - 1 && input[index + 1] == '>') {
                        tokens.add(Token(TokenType.ARROW, "->"))
                        advance()
                        advance()
                    } else {
                        handleTwoCharOp(TokenType.ASSIGN, "-=", TokenType.MINUS)
                    }
                }
                currentChar == '>' -> handleTwoCharOp(TokenType.GREATER_EQ, ">=", TokenType.GREATER)
                currentChar == '<' -> handleTwoCharOp(TokenType.LESS_EQ, "<=", TokenType.LESS)
                currentChar == '=' -> handleTwoCharOp(TokenType.EQUAL, "==", TokenType.ASSIGN)
                currentChar == '!' -> handleTwoCharOp(TokenType.NOT_EQUAL, "!=", TokenType.NOT)
                currentChar == '&' -> handleTwoCharOp(TokenType.LOGIC_AND, "&&")
                currentChar == '|' -> handleTwoCharOp(TokenType.LOGIC_OR, "||")
                currentChar == '/' -> {
                    if (index == input.length - 1) {
                        pushAndAdvance(Token(TokenType.DIV, "/"))
                    } else {
                        if (input[index + 1] == '/') {
                            while (index < input.length && currentChar != '\n') {
                                advance()
                            }
                        } else {
                            handleTwoCharOp(TokenType.ASSIGN, "/=", TokenType.DIV)
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
                currentChar == '\'' -> {
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
                                '\'' -> str += '\''
                                else -> str += "\\$currentChar"
                            }
                        } else if (currentChar == '\\') {
                            escape = true
                        } else if (currentChar == '\'') {
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
                currentChar.isLetter() || currentChar == '_' || currentChar == '$' -> {
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
                        "nudge" -> tokens.add(Token(TokenType.ACTION, "nudge"))
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
sealed class NodeValue : Comparable<NodeValue> {
    abstract fun toBoolean(): Boolean
    fun asString() = (this as? StringValue)?.value
    fun asNumber() = (this as? NumberValue)?.value
    fun asList() = (this as? ListValue)?.value
    fun asObject() = this as? ObjectValue
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

    operator fun unaryMinus(): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        if (expr is NumberValue) {
            return NumberValue(-expr.value)
        } else {
            throw IllegalArgumentException("Invalid operation: -$this")
        }
    }

    operator fun not(): NodeValue {
        return toBoolean().not().toNodeValue()
    }

    operator fun contains(that: NodeValue): Boolean {
        return if (this is StringValue && that is StringValue) {
            this.value.contains(that.value)
        } else if (this is Iterable<*>) {
            (this as Iterable<*>).contains(that)
        } else {
            throw IllegalArgumentException("Invalid operation: $that in $this")
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
        val builtinSymbols = mapOf("true" to true.toNodeValue(), "false" to false.toNodeValue(), "null" to NullValue)
    }

}

@Serializable
data class StringValue(val value: String) : NodeValue(), Iterable<StringValue> {
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
data class ListValue(val value: List<NodeValue>) : NodeValue(), Iterable<NodeValue> {
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
data class NumberValue(val value: Long) : NodeValue() {
    override fun toString() = value.toString()
    override fun toBoolean(): Boolean = value != 0L
}

fun Int.toNodeValue(): NodeValue = NumberValue(this.toLong())
fun Long.toNodeValue(): NodeValue = NumberValue(this)

@Serializable
data class BooleanValue(val value: Boolean) : NodeValue() {
    override fun toString() = value.toString()
    override fun toBoolean(): Boolean = value
}

fun Boolean.toNodeValue() = BooleanValue(this)
fun Boolean.toLong() = if (this) 1L else 0L

@Serializable
sealed class SubscriptValue : NodeValue()

@Serializable
data class NumberSubscriptValue(val begin: Int, val extended: Boolean, val end: Int? = null) : SubscriptValue() {
    override fun toString() = if (extended) "$begin:$end" else "$begin"
    override fun toBoolean(): Boolean = true
}

@Serializable
data class KeySubscriptValue(val key: String) : SubscriptValue() {
    override fun toString() = key
    override fun toBoolean(): Boolean = true
}

@Serializable
data class ObjectValue(val attributes: Map<String, NodeValue> = mapOf()) : NodeValue() {
    override fun toBoolean(): Boolean = attributes.isNotEmpty()
    operator fun get(key: String): NodeValue? = attributes[key]
    fun keys(): List<String> = attributes.keys.toList()
    override fun toString(): String {
        return "{" + attributes.map { "${it.key}: ${it.value}" }.joinToString(", ") + "}"
    }
    fun bindSelf(): ObjectValue {
        for ((key, value) in attributes) {
            if (value is ProcedureValue) {
                value.bind(this)
            }
        }
        return this
    }
}

fun Map<String, NodeValue>.toNodeValue() = ObjectValue(this)

sealed class ProcedureValue(protected val params: List<String>, protected var self: NodeValue?) : NodeValue() {
    override fun toBoolean(): Boolean = true
    abstract fun execute(context: ExecutionContext, self: NodeValue?): NodeValue
    fun call(context: ExecutionContext, args: ListValue, otherSelf: NodeValue? = null): NodeValue {
        val res: NodeValue
        try {
            context.stack.push(args)
            res = execute(context, otherSelf ?: self)
        } finally {
            context.stack.pop()
        }
        return res
    }
    fun bind(self: NodeValue?): ProcedureValue {
        this.self = self
        return this
    }
    abstract fun copy(): ProcedureValue

    companion object {
        private val Slice = { self: NodeValue ->
            NodeProcedureValue(
                AccessViewNode(
                    IdentifierNode(Token(TokenType.IDENTIFIER, "this")), SubscriptNode(
                        IdentifierNode(Token(TokenType.IDENTIFIER, "begin")),
                        true,
                        IdentifierNode(Token(TokenType.IDENTIFIER, "end"))
                    )
                ), listOf("begin", "end"), self
            )
        }
        private val whiteSpace = Pattern.compile("\\s+")
        private val Split = { self: NodeValue ->
            BuiltinProcedureValue("split", listOf("separator"), { context ->
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
            BuiltinProcedureValue("join", listOf("separator"), { context ->
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
            BuiltinProcedureValue("find", listOf("what"), { context ->
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
            BuiltinProcedureValue("contains", listOf("what"), { context ->
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
            BuiltinProcedureValue("length", listOf(), { context ->
                when (val expr = context.stack["this"]!!) {
                    is StringValue -> expr.value.length.toNodeValue()
                    is ListValue -> expr.value.size.toNodeValue()
                    is RangeValue<*> -> expr.size.toNodeValue()
                    else -> throw RuntimeException("$expr has no such method as \"length\"")
                }
            }, self)
        }
        private val Time = BuiltinProcedureValue("time", listOf(), {
            System.currentTimeMillis().toNodeValue()
        }, null)
        private val Random = { self: NodeValue ->
            BuiltinProcedureValue("random", listOf("first", "second"), { context ->
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
        private val Range = BuiltinProcedureValue("range", listOf("begin", "end"), { context ->
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
        private val RangeInclusive = BuiltinProcedureValue("rangeInclusive", listOf("begin", "end"), { context ->
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
        private val Number = BuiltinProcedureValue("number", listOf("str"), { context ->
            context.stack["str"]!!.asString()!!.toLong().toNodeValue()
        }, null)
        private val String = BuiltinProcedureValue("string", listOf("num"), { context ->
            context.stack["num"]!!.asNumber()!!.toString().toNodeValue()
        }, null)
        private val Object = BuiltinProcedureValue("object", listOf("fields"), { context ->
            val fields = context.stack["fields"]?.asList() ?: return@BuiltinProcedureValue ObjectValue(emptyMap())
            val result = mutableMapOf<String, NodeValue>()
            for (field in fields) {
                val key = field.asList()!![0].asString()!!
                val value = field.asList()!![1]
                result[key] = value
            }
            return@BuiltinProcedureValue ObjectValue(result)
        }, null)
        private val Abs = BuiltinProcedureValue("abs", listOf("num"), { context ->
            context.stack["num"]!!.asNumber()!!.absoluteValue.toNodeValue()
        }, null)
        private val Enumerated = { self: NodeValue ->
            BuiltinProcedureValue("enumerated", listOf(), { context ->
                val list = context.stack["this"]!!
                return@BuiltinProcedureValue if (list is Iterable<*>) {
                    list.mapIndexed { index, value -> ListValue(listOf(index.toNodeValue(), value as NodeValue)) }
                        .toNodeValue()
                } else {
                    throw RuntimeException("$list has no such method as \"enumerated\"")
                }
            }, self)
        }
        private val Ord = BuiltinProcedureValue("ord", listOf("str"), { context ->
            context.stack["str"]!!.asString()!!.first().code.toLong().toNodeValue()
        }, null)
        private val Chr = BuiltinProcedureValue("chr", listOf("num"), { context ->
            context.stack["num"]!!.asNumber()!!.toInt().toChar().toString().toNodeValue()
        }, null)
        private val Pow = BuiltinProcedureValue("pow", listOf("num", "exp"), { context ->
            val num = context.stack["num"]!!.asNumber()!!
            val exp = context.stack["exp"]!!.asNumber()!!
            num.toDouble().pow(exp.toDouble()).toLong().toNodeValue()
        }, null)
        private val Sum = { self: NodeValue ->
            BuiltinProcedureValue("sum", listOf(), { context ->
                val list = context.stack["this"]!!
                return@BuiltinProcedureValue if (list is Iterable<*>) {
                    list.sumOf { (it as NodeValue).asNumber()!! }.toNodeValue()
                } else {
                    throw RuntimeException("$list has no such method as \"sum\"")
                }
            }, self)
        }
        private val Boolean = BuiltinProcedureValue("boolean", listOf("value"), { context ->
            context.stack["value"]!!.toBoolean().toNodeValue()
        }, null)
        private val Filter = { self: NodeValue ->
            BuiltinProcedureValue("filter", listOf("predicate"), { context ->
                val predicate = context.stack["predicate"]!!.asProcedure()!!
                fun predicateCall(it: NodeValue) = predicate.call(context, ListValue(listOf(it)))
                val list = context.stack["this"]!!
                return@BuiltinProcedureValue if (list is Iterable<*>) {
                    @Suppress("UNCHECKED_CAST") (list.filter { predicateCall(it as NodeValue).toBoolean() } as List<NodeValue>).toNodeValue()
                } else {
                    throw RuntimeException("$list has no such method as \"filter\"")
                }
            }, self)
        }
        private val Reduce = { self: NodeValue ->
            BuiltinProcedureValue("reduce", listOf("initial", "reducer"), { context ->
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
            BuiltinProcedureValue("map", listOf("mapper"), { context ->
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
            BuiltinProcedureValue("max", listOf("list"), { context ->
                val list = (context.stack["this"] as? Iterable<*>) ?: (context.stack["list"]!! as Iterable<*>)
                return@BuiltinProcedureValue list.maxByOrNull { it as NumberValue }!! as NodeValue
            }, self)
        }
        private val Min = { self: NodeValue ->
            BuiltinProcedureValue("max", listOf("list"), { context ->
                val list = (context.stack["this"] as? Iterable<*>) ?: (context.stack["list"]!! as Iterable<*>)
                return@BuiltinProcedureValue list.minByOrNull { it as NumberValue }!! as NodeValue
            }, self)
        }
        private val Reversed = { self: NodeValue ->
            BuiltinProcedureValue("reversed", listOf(), { context ->
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
            BuiltinProcedureValue("sorted", listOf("cmp"), { context ->
                @Suppress("UNCHECKED_CAST") val list = context.stack["this"]!! as Iterable<NodeValue>
                val cmp = context.stack["cmp"]?.asProcedure()
                return@BuiltinProcedureValue if (cmp == null) {
                    list.sorted().toNodeValue()
                } else {
                    list.sortedWith { a, b ->
                        val res = cmp.call(context, ListValue(listOf(a, b)))
                        if (res.toBoolean()) {
                            1
                        } else {
                            -1
                        }
                    }.toNodeValue()
                }
            }, self)
        }
        private val GetNickname = BuiltinProcedureValue("getNickname", listOf("id"), { context ->
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
        )
    }
}

class BuiltinProcedureValue(
    private val name: String,
    params: List<String>,
    private val func: (context: ExecutionContext) -> NodeValue,
    self: NodeValue?
) : ProcedureValue(params, self) {
    override fun toString(): String = "builtin($name)"
    override fun execute(context: ExecutionContext, self: NodeValue?): NodeValue {
        context.stack.nameArgs(params, self)
        return func(context)
    }
    override fun copy(): ProcedureValue {
        return BuiltinProcedureValue(name, params, func, self)
    }
}

class NodeProcedureValue(private val func: Node, params: List<String>, self: NodeValue?) :
    ProcedureValue(params, self) {
    override fun toString() = "procedure($func)"
    override fun execute(context: ExecutionContext, self: NodeValue?): NodeValue {
        context.stack.nameArgs(params, self)
        return func.exec(context)
    }
    override fun copy(): ProcedureValue {
        return NodeProcedureValue(func, params, self)
    }
}

@Serializable
sealed class RangeValue<T : NodeValue>(
    protected val begin: T, protected val end: T, protected val inclusive: Boolean
) : NodeValue(), Iterable<T> {
    override fun toBoolean() = true
    override fun toString() = "range($begin, $end)"
    abstract fun random(): T
    abstract val size: Long
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

    operator fun contains(value: NumberValue): Boolean {
        return if (inclusive) {
            value.value in (begin.value..end.value)
        } else {
            value.value in (begin.value until end.value)
        }
    }

    override fun random(): NumberValue {
        return if (inclusive) {
            NumberValue((begin.value..end.value).random())
        } else {
            NumberValue((begin.value until end.value).random())
        }
    }

    override val size: Long = if (inclusive) {
        end.value - begin.value + 1
    } else {
        end.value - begin.value
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

    operator fun contains(value: StringValue): Boolean {
        return if (inclusive) {
            value.value[0] in (begin.value[0]..end.value[0])
        } else {
            value.value[0] in (begin.value[0] until end.value[0])
        }
    }

    override fun random(): StringValue {
        return if (inclusive) {
            StringValue((begin.value[0]..end.value[0]).random().toString())
        } else {
            StringValue((begin.value[0] until end.value[0]).random().toString())
        }
    }

    override val size: Long
        get() = if (inclusive) {
            end.value[0] - begin.value[0] + 1
        } else {
            end.value[0] - begin.value[0]
        }.toLong()

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
object NullValue : NodeValue() {
    override fun toString() = "null"
    override fun toBoolean(): Boolean = false
}

class Scope(val symbols: MutableMap<String, NodeValue>, val args: ListValue = ListValue(emptyList())) {
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

class RecursionTooDeepException(private val depth: Int) : Exception() {
    override fun toString(): String {
        return "Recursion too deep: $depth"
    }

    override val message: String = toString()
}

class Stack(rootScope: Scope, private val declarations: MutableMap<String, (NodeValue) -> ProcedureValue>) {
    private val scopes: MutableList<Scope>

    private var depth = 0

    init {
        scopes = mutableListOf(rootScope)
    }

    // The first argument must be the value of "this"
    fun push(args: ListValue = emptyList<NodeValue>().toNodeValue()) {
        scopes.add(Scope(mutableMapOf(), args))
        depth++
        if (depth > 300) {
            throw RecursionTooDeepException(depth)
        }
    }

    fun pop() {
        depth--
        scopes.removeAt(scopes.lastIndex)
    }

    private val args: ListValue
        get() = scopes.last().args

    fun nameArgs(params: List<String>, self: NodeValue?) {
        val argc = min(params.size, args.size)
        for (i in 0 until argc) {
            scopes.last()[params[i]] = args[i]
        }
        if (argc > params.size) {
            scopes.last()["\$varargs"] = args.value.slice(params.size until args.size).toList().toNodeValue()
        }
        scopes.last()["\$"] = args
        args.forEachIndexed { index, nodeValue -> scopes.last()["\$$index"] = nodeValue }
        if (self != null) {
            scopes.last()["this"] = self
        }
    }

    operator fun get(name: String): NodeValue? {
        for (scope in scopes.reversed()) {
            val value = scope[name]
            if (value != null) {
                return value
            }
        }
        return NodeValue.builtinSymbols[name] ?: declarations[name]?.invoke(NullValue)
        ?: ProcedureValue.builtinFunctions[name] ?: ProcedureValue.builtinMethods[name]?.invoke(NullValue)
    }

    fun getProcedure(name: String): ((NodeValue) -> ProcedureValue)? {
        return declarations[name] ?: ProcedureValue.builtinMethods[name]
        ?: ProcedureValue.builtinFunctions[name]?.let { it -> { _: NodeValue -> it } }
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
        return context.stack[name] ?: NullValue
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
        return when (val begin = begin.exec(context)) {
            is NumberValue -> NumberSubscriptValue(
                begin.value.toInt(), extended, end?.exec(context)?.asNumber()?.toInt()
            )
            is StringValue -> KeySubscriptValue(begin.value)
            else -> throw IllegalArgumentException("Illegal accessing: expected NUMBER or STRING, got ${begin.javaClass.simpleName}")
        }
    }

    override fun toString(): String {
        return if (end != null) "subscript($begin, $end)" else "subscript($begin)"
    }
}

class ObjectNode(private val items: List<Pair<IdentifierNode, Node>>) : Node() {
    override fun exec(context: ExecutionContext): ObjectValue {
        val objVal =  items.associate { (key, value) ->
            val res = value.exec(context)
            if (res is ProcedureValue) {
                key.name to res.copy()
            } else {
                key.name to res
            }
        }.toNodeValue()
        return objVal.bindSelf()
    }

    override fun toString(): String {
        return "{${items.joinToString(", ") { (key, value) -> "$key: $value" }}}"
    }
}

class AccessViewNode(private val list: Node, private val subscripts: List<SubscriptNode>) : Node() {
    constructor(
        existing: Node, subscript: SubscriptNode
    ) : this(
        if (existing is AccessViewNode) existing.list else existing,
        if (existing is AccessViewNode) existing.subscripts + subscript else listOf(subscript)
    )

    private class AccessorHierarchy(list: NodeValue, subscripts: List<SubscriptValue>) {

        sealed class Pointer
        data class IndexPointer(val index: Int) : Pointer()
        data class KeyPointer(val key: String) : Pointer()

        val isEmpty: Boolean
        val pointers: List<Pointer>
        val endsAtString: Boolean
        val newAttribute: Boolean
        val methodCall: Boolean
        val lastSlice: IntRange?

        init {
            var currentList = list
            var isEmpty = false
            val pointers = mutableListOf<Pointer>()
            var endsAtString = false
            var newAttribute = false
            var methodCall = false
            var lastRecursion = false
            var lastSlice: IntRange? = null

            for (accessor in subscripts) {
                if (lastRecursion) {
                    isEmpty = true
                    break
                }
                when (currentList) {
                    is ObjectValue -> {
                        val subscript = accessor as KeySubscriptValue
                        pointers.add(KeyPointer(subscript.key))
                        val newList = currentList[subscript.key]
                        if (newList == null) {
                            newAttribute = true
                            methodCall = true
                            lastRecursion = true
                        } else {
                            currentList = newList
                        }
                    }
                    is ListValue -> {
                        val range = lastSlice ?: currentList.value.indices
                        when (accessor) {
                            is NumberSubscriptValue -> {
                                if (accessor.extended) {
                                    val slice = range.safeSlice(accessor.begin, accessor.end)
                                    if (slice != null) {
                                        lastSlice = slice
                                    } else {
                                        isEmpty = true
                                        break
                                    }
                                } else {
                                    val index = range.safeSubscript(accessor.begin)
                                    if (index != null) {
                                        pointers.add(IndexPointer(index))
                                        lastSlice = null
                                        currentList = currentList.value[index]
                                    } else {
                                        isEmpty = true
                                        break
                                    }
                                }
                            }
                            is KeySubscriptValue -> {
                                val key = accessor.key
                                methodCall = true
                                lastRecursion = true
                                pointers.add(KeyPointer(key))
                            }
                        }
                    }
                    is StringValue -> {
                        endsAtString = true
                        val range = lastSlice ?: currentList.value.indices
                        when (accessor) {
                            is NumberSubscriptValue -> {
                                if (accessor.extended) {
                                    val slice = range.safeSlice(accessor.begin, accessor.end)
                                    if (slice != null) {
                                        lastSlice = slice
                                    } else {
                                        isEmpty = true
                                        break
                                    }
                                } else {
                                    val index = range.safeSubscript(accessor.begin)
                                    if (index != null) {
                                        lastSlice = IntRange(index, index)
//                                        lastRecursion = true
                                    } else {
                                        isEmpty = true
                                        break
                                    }
                                }
                            }
                            is KeySubscriptValue -> {
                                val key = accessor.key
                                methodCall = true
                                lastRecursion = true
                                lastSlice = range
                                pointers.add(KeyPointer(key))
                            }
                        }
                    }
                    else -> {
                        methodCall = true
                        lastRecursion = true
                        pointers.add(KeyPointer((accessor as KeySubscriptValue).key))
                    }
                }
            }
            this.isEmpty = isEmpty
            this.pointers = pointers
            this.endsAtString = endsAtString
            this.newAttribute = newAttribute
            this.methodCall = methodCall
            this.lastSlice = lastSlice
        }

        fun containPath(path: List<Pointer>): Boolean {
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
        val hierarchy = AccessorHierarchy(list, subscripts)
        if (hierarchy.isEmpty) return NullValue
        fun handleLastSlice(l: NodeValue): NodeValue {
            return if (hierarchy.endsAtString) {
                val string = l.asString()!!
                StringValue(string.substring(hierarchy.lastSlice!!))
            } else {
                if (hierarchy.lastSlice != null) {
                    ListValue(l.asList()!!.slice(hierarchy.lastSlice))
                } else {
                    l
                }
            }
        }
        hierarchy.pointers.mapIndexed { index, pointer ->
            if (hierarchy.methodCall && index == hierarchy.pointers.size - 1) {
                list = handleLastSlice(list)
                return context.stack.getProcedure((hierarchy.pointers.last() as AccessorHierarchy.KeyPointer).key)!!
                    .invoke(list)
            }
            list = when (pointer) {
                is AccessorHierarchy.IndexPointer -> {
                    list.asList()!![pointer.index]
                }
                is AccessorHierarchy.KeyPointer -> {
                    list.asObject()
                        ?.let { it[pointer.key] }!! // ?: context.stack.getProcedure(pointer.key)?.invoke(list)!! // if getProcedure, hierarchy.methodCall must be true
                }
            }
        }
        return handleLastSlice(list)
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        if (list !is IdentifierNode) {
            throw RuntimeException("Can only assign to identifiers, not $list")
        }
        val listValue = list.exec(context)
        val subscripts = subscripts.map { it.exec(context) }
        val hierarchy = AccessorHierarchy(listValue, subscripts)
        if (hierarchy.isEmpty) throw RuntimeException("Failed subscripting on $list${subscripts.joinToString("") { "[$it]" }}")
        val path = mutableListOf<AccessorHierarchy.Pointer>()
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
                return when (cur) {
                    is ListValue -> {
                        val newList = mutableListOf<NodeValue>()
                        for (i in cur.value.indices) {
                            path.add(AccessorHierarchy.IndexPointer(i))
                            newList.add(copyValue(cur.value[i]))
                            path.removeLast()
                        }
                        ListValue(newList)
                    }
                    is ObjectValue -> {
                        val newAttributes = mutableMapOf<String, NodeValue>()
                        for (i in cur.keys()) {
                            path.add(AccessorHierarchy.KeyPointer(i))
                            newAttributes[i] = copyValue(cur[i]!!)
                            path.removeLast()
                        }
                        if (hierarchy.newAttribute) {
                            if (hierarchy.containPath(path + hierarchy.pointers.last())) {
                                newAttributes[(hierarchy.pointers.last() as AccessorHierarchy.KeyPointer).key] = value
                            }
                        }
                        ObjectValue(newAttributes)
                    }
                    else -> cur
                }
            }
        }

        val newValue = copyValue(listValue)
        list.assign(context, newValue)
    }

    override fun toString(): String {
        return "AccessView($list${subscripts.joinToString("") { "[$it]" }})"
    }
}

class ProcedureNode(private val func: Node, private val args: ListNode) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val procedure = func.exec(context).asProcedure()!!
        val args = args.exec(context)
        return procedure.call(context, args)
    }

    override fun toString(): String {
        return "$func($args)"
    }
}

abstract class OperatorNode : Node() {
    enum class OperatorType {
        UNARY, BINARY
    }

    data class Precedence(val operators: List<TokenType>, val opType: OperatorType)
    companion object {
        val PrecedenceList = listOf(
            Precedence(listOf(TokenType.NOT, TokenType.MINUS), OperatorType.UNARY),
            Precedence(listOf(TokenType.MULT, TokenType.DIV, TokenType.MOD), OperatorType.BINARY),
            Precedence(listOf(TokenType.PLUS, TokenType.MINUS), OperatorType.BINARY),
            Precedence(
                listOf(TokenType.GREATER_EQ, TokenType.LESS_EQ, TokenType.GREATER, TokenType.LESS), OperatorType.BINARY
            ),
            Precedence(listOf(TokenType.EQUAL, TokenType.NOT_EQUAL), OperatorType.BINARY),
            Precedence(listOf(TokenType.LOGIC_AND), OperatorType.BINARY),
            Precedence(listOf(TokenType.LOGIC_OR), OperatorType.BINARY),
            Precedence(listOf(TokenType.IN), OperatorType.BINARY)
        )
    }
}

class BinaryOperatorNode(private val components: List<Node>, private val ops: List<TokenType>) : OperatorNode() {
    private val opMap =
        mapOf<TokenType, (ExecutionContext, Node, Node) -> NodeValue>(
            TokenType.PLUS to { context, left, right -> left.exec(context) + right.exec(context) },
            TokenType.MINUS to { context, left, right -> left.exec(context) - right.exec(context) },
            TokenType.MULT to { context, left, right -> left.exec(context) * right.exec(context) },
            TokenType.DIV to { context, left, right -> left.exec(context) / right.exec(context) },
            TokenType.MOD to { context, left, right -> left.exec(context) % right.exec(context) },
            TokenType.EQUAL to { context, left, right -> (left.exec(context) == right.exec(context)).toNodeValue() },
            TokenType.NOT_EQUAL to { context, left, right -> (left.exec(context) != right.exec(context)).toNodeValue() },
            TokenType.GREATER to { context, left, right -> (left.exec(context) > right.exec(context)).toNodeValue() },
            TokenType.LESS to { context, left, right -> (left.exec(context) < right.exec(context)).toNodeValue() },
            TokenType.GREATER_EQ to { context, left, right -> (left.exec(context) >= right.exec(context)).toNodeValue() },
            TokenType.LESS_EQ to { context, left, right -> (left.exec(context) <= right.exec(context)).toNodeValue() },
            TokenType.LOGIC_AND to { context, left, right ->
                (left.exec(context).toBoolean() && right.exec(context).toBoolean()).toNodeValue()
            },
            TokenType.LOGIC_OR to { context, left, right ->
                (left.exec(context).toBoolean() || right.exec(context).toBoolean()).toNodeValue()
            },
            TokenType.IN to { context, left, right -> (left.exec(context) in right.exec(context)).toNodeValue() }
        )

    override fun exec(context: ExecutionContext): NodeValue {
        return if (components.isEmpty()) NullValue
        else if (components.size == 1) {
            components[0].exec(context)
        } else {
            var res = components[0].exec(context)
            for (i in 1 until components.size) {
                val op = ops[i - 1]
                val next = components[i]
                res = opMap[op]!!(context, res.toNode(), next)
            }
            res
        }
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        if (components.size == 1) {
            components[0].assign(context, value)
        } else {
            throw RuntimeException("$this is not a left value")
        }
    }

    override fun toString(): String {
        if (components.size == 1) return components[0].toString()
        val str = components.forEachIndexed { index, node -> "$node${if (index < ops.size) ops[index] else ""}" }
        return "Binary($str)"
    }
}

class UnaryOperatorNode(private val component: Node, private val op: TokenType) : OperatorNode() {
    private val opMap = mapOf<TokenType, (ExecutionContext, Node) -> NodeValue>(
        TokenType.MINUS to { context, node -> -node.exec(context) },
        TokenType.NOT to { context, node -> !node.exec(context) },
    )

    override fun exec(context: ExecutionContext): NodeValue {
        return opMap[op]!!(context, component)
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        throw RuntimeException("$this is not a left value")
    }

    override fun toString(): String {
        return "Unary($op$component)"
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

class StmtActionNode(private val action: Token, private val expr: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        if (action.type != TokenType.ACTION) {
            throw IllegalArgumentException("Expected ACTION, got ${action.type}")
        }
        val value = expr.exec(context)
        when (action.value) {
            "say" -> {
                context.say(value.toString())
            }
            "nudge" -> {
                context.nudge(value.asNumber()!!)
            }
            else -> throw IllegalArgumentException("Unknown action ${action.value}")
        }
        return NullValue
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
            elseBody?.exec(context) ?: NullValue
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
        return NullValue
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
        return NullValue
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
        var res: NodeValue = NullValue
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
        var res: NodeValue = NullValue
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
    private fun peekNext() = if (current < tokens.size - 1) tokens[current + 1] else null
    private fun peekNextNext() = if (current < tokens.size - 2) tokens[current + 2] else null

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
                declarations[func.name] = { obj -> NodeProcedureValue(StmtFuncNode(body), params.map { it.name }, obj) }
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
                val iterator = parseTerm()
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
            "+=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.PLUS)))
            "-=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.MINUS)))
            "*=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.MULT)))
            "/=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.DIV)))
            "%=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.MOD)))
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

    private fun parseExpr(): Node {
        if ((peek().type == TokenType.BRACE_OPEN && peekNext()?.type != TokenType.BRACE_CLOSE && peekNextNext()?.type != TokenType.COLON) || peek().type == TokenType.FUNC) {
            return parseLambda()
        }
        return parseOperator()
    }

    private fun parseOperator(precedence: Int = OperatorNode.PrecedenceList.lastIndex): Node {
        if (precedence < 0) {
            return parseTerm()
        }
        val op = OperatorNode.PrecedenceList[precedence]
        return when (op.opType) {
            OperatorNode.OperatorType.UNARY -> {
                if (peek().type in op.operators) {
                    val unaryOp = consume(peek().type)
                    val next = parseOperator(precedence - 1)
                    UnaryOperatorNode(next, unaryOp.type)
                } else {
                    parseOperator(precedence - 1)
                }
            }
            OperatorNode.OperatorType.BINARY -> {
                val nodes = mutableListOf(parseOperator(precedence - 1))
                val ops = mutableListOf<TokenType>()
                while (peek().type in op.operators) {
                    ops.add(consume(peek().type).type)
                    nodes.add(parseOperator(precedence - 1))
                }
                BinaryOperatorNode(nodes, ops)
            }
        }
    }

    private fun parseLambda(): Node {
        return when (peek().type) {
            TokenType.FUNC -> {
                consume(TokenType.FUNC)
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
                NodeProcedureValue(StmtFuncNode(body), params.map { it.name }, null).toNode() // have to make sure caller is assigned to self
            }
            TokenType.BRACE_OPEN -> {
                consume(TokenType.BRACE_OPEN)
                val params = mutableListOf<IdentifierNode>()
                if (peek().type == TokenType.IDENTIFIER && (peekNext()?.type == TokenType.COMMA || peekNext()?.type == TokenType.ARROW)) {
                    // lambda with params
                    params.add(parseIdentifier())
                    while (peek().type != TokenType.ARROW) {
                        consume(TokenType.COMMA)
                        params.add(parseIdentifier())
                    }
                    consume(TokenType.ARROW)
                }
                consumeLineBreak()
                val body = parseStmt()
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                NodeProcedureValue(body, params.map { it.name }, null).toNode()
            }
            else -> throw UnexpectedTokenException(peek(), TokenType.FUNC)
        }
    }

    private fun parseTermHead(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.IDENTIFIER -> parseIdentifier()
            TokenType.NUMBER -> parseNumber()
            TokenType.STRING -> parseString()
            TokenType.PAREN_OPEN -> {
                consume(TokenType.PAREN_OPEN)
                val expr = parseOperator()
                consume(TokenType.PAREN_CLOSE)
                expr
            }
            TokenType.BRACKET_OPEN -> { // list literal
                consume(TokenType.BRACKET_OPEN)
                val list = parseParamList()
                consume(TokenType.BRACKET_CLOSE)
                list
            }
            TokenType.BRACE_OPEN -> { // object literal
                consume(TokenType.BRACE_OPEN)
                val obj = if (peek().type == TokenType.BRACE_CLOSE) {
                    ObjectNode(emptyList())
                } else {
                    val k = parseIdentifier()
                    consume(TokenType.COLON)
                    val items = mutableListOf(k to parseExpr())
                    while (peek().type != TokenType.BRACE_CLOSE) {
                        consume(TokenType.COMMA)
                        val key = parseIdentifier()
                        consume(TokenType.COLON)
                        val expr = parseExpr()
                        items.add(key to expr)
                    }
                    ObjectNode(items)
                }
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                obj
            }
            else -> throw UnexpectedTokenException(token)
        }
    }

    private fun parseParamList(): ListNode {
        val params = mutableListOf<Node>()
        if (peek().type != TokenType.PAREN_CLOSE && peek().type != TokenType.BRACKET_CLOSE) {
            params.add(parseExpr())
            while (peek().type != TokenType.PAREN_CLOSE && peek().type != TokenType.BRACKET_CLOSE) {
                consume(TokenType.COMMA)
                params.add(parseExpr())
            }
        }
        return ListNode(params)
    }

    private fun parseTermTail(termHead: Node): Node {
        val token = peek()
        return when (token.type) {
            TokenType.DOT -> {
                consume(TokenType.DOT)
                val attribute = IdentifierNode(consume(TokenType.IDENTIFIER))
                AccessViewNode(termHead, SubscriptNode(attribute.name.toNodeValue().toNode(), false))
            }
            TokenType.BRACKET_OPEN -> {
                consume(TokenType.BRACKET_OPEN)
                val begin = if (peek().type == TokenType.COLON) {
                    0.toNodeValue().toNode()
                } else parseExpr()
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
                AccessViewNode(termHead, subscript)
            }
            TokenType.PAREN_OPEN -> {
                consume(TokenType.PAREN_OPEN)
                val params = parseParamList()
                consume(TokenType.PAREN_CLOSE)
                ProcedureNode(termHead, params)
            }
            else -> termHead
        }
    }

    private fun parseTerm(): Node {
        var term = parseTermHead()
        while (peek().type == TokenType.DOT || peek().type == TokenType.BRACKET_OPEN || peek().type == TokenType.PAREN_OPEN) {
            term = parseTermTail(term)
        }
        return term
    }

    private var declarations: MutableMap<String, (NodeValue) -> ProcedureValue> = mutableMapOf()
    fun parse(): Pair<Node, MutableMap<String, (NodeValue) -> ProcedureValue>> {
        declarations = mutableMapOf()
        return Pair(parseStmtList(false), declarations)
    }
}

abstract class ExecutionContext(
    rootScope: Scope, declarations: MutableMap<String, (NodeValue) -> ProcedureValue>, val firstRun: Boolean
) {
    val stack: Stack

    init {
        stack = Stack(rootScope, declarations)
    }

    abstract fun say(text: String)
    abstract fun nudge(target: Long)
    abstract fun nickname(id: Long): String
}

class ConsoleContext(rootScope: Scope? = null, declarations: MutableMap<String, (NodeValue) -> ProcedureValue>) :
    ExecutionContext(rootScope ?: Scope.createRoot(), declarations, true) {
    override fun say(text: String) {
        println(text)
    }

    override fun nudge(target: Long) {
        println("Nudge $target")
    }

    override fun nickname(id: Long): String {
        return "$id"
    }
}

open class ControlledContext(
    rootScope: Scope, declarations: MutableMap<String, (NodeValue) -> ProcedureValue>, firstRun: Boolean
) : ExecutionContext(rootScope, declarations, firstRun) {
    private val record = mutableListOf<String>()
    override fun say(text: String) {
        record.add(text)
    }

    override fun nudge(target: Long) {
        record.add("Nudge $target")
    }

    override fun nickname(id: Long): String {
        return "$id"
    }

    open fun dumpOutput(): String {
        val str = if(record.isEmpty()) "" else record.joinToString("\n")
        record.clear()
        return str
    }
}

class Interpreter(source: String, private val restricted: Boolean) {
    private val ast: Node
    val declarations: MutableMap<String, (NodeValue) -> ProcedureValue>

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
    private val declarations = mutableMapOf<String, (NodeValue) -> ProcedureValue>()

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
