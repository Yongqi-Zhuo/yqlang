import java.util.regex.Pattern
import kotlin.math.min

enum class TokenType {
    BRACE_OPEN, BRACE_CLOSE, PAREN_OPEN, PAREN_CLOSE, BRACKET_OPEN, BRACKET_CLOSE, NEWLINE, SEMICOLON, COLON, ASSIGN, DOT, COMMA, INIT,
    MULT_OP, // MULTIPLY, DIVIDE, MODULO
    ADD_OP, // PLUS, MINUS,
    COMP_OP, // EQUAL, NOT_EQUAL, GREATER, LESS, GREATER_EQUAL, LESS_EQUAL
    LOGIC_OP, //AND, OR
    NOT, IF, ELSE, ACTION, IDENTIFIER, BUILTIN, NUMBER, STRING, EOF
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
                currentChar == '/' -> handleTwoCharOp(TokenType.ASSIGN, "/=", TokenType.MULT_OP)
                currentChar == '%' -> handleTwoCharOp(TokenType.ASSIGN, "%=", TokenType.MULT_OP)
                currentChar == '&' -> handleTwoCharOp(TokenType.LOGIC_OP, "&&")
                currentChar == '|' -> handleTwoCharOp(TokenType.LOGIC_OP, "||")
                currentChar == '=' -> handleTwoCharOp(TokenType.COMP_OP, "==", TokenType.ASSIGN)
                currentChar == '!' -> handleTwoCharOp(TokenType.COMP_OP, "!=", TokenType.NOT)
                currentChar == '>' -> handleTwoCharOp(TokenType.COMP_OP, ">=", TokenType.COMP_OP)
                currentChar == '<' -> handleTwoCharOp(TokenType.COMP_OP, "<=", TokenType.COMP_OP)
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
                        "init" -> tokens.add(Token(TokenType.INIT, "init"))
                        "say" -> tokens.add(Token(TokenType.ACTION, "say"))
                        "split" -> tokens.add(Token(TokenType.BUILTIN, "split"))
                        "join" -> tokens.add(Token(TokenType.BUILTIN, "join"))
                        "slice" -> tokens.add(Token(TokenType.BUILTIN, "slice"))
                        "find" -> tokens.add(Token(TokenType.BUILTIN, "find"))
                        "contains" -> tokens.add(Token(TokenType.BUILTIN, "contains"))
                        "length" -> tokens.add(Token(TokenType.BUILTIN, "length"))
                        "defined" -> tokens.add(Token(TokenType.BUILTIN, "defined"))
                        "time" -> tokens.add(Token(TokenType.BUILTIN, "time"))
                        "random" -> tokens.add(Token(TokenType.BUILTIN, "random"))
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

abstract class NodeValue {
    abstract fun toBoolean(): Boolean
    fun asString() = (this as? StringValue)?.value
    fun asNumber() = (this as? NumberValue)?.value
    fun asList() = (this as? ListValue)?.value
    fun asProcedure() = (this as? ProcedureValue)
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

    override operator fun equals(other: Any?): Boolean {
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

    operator fun compareTo(that: NodeValue): Int {
        return if (this is NumberValue && that is NumberValue) {
            this.value.compareTo(that.value)
        } else if (this is StringValue && that is StringValue) {
            this.value.compareTo(that.value)
        } else if (this is BooleanValue && that is BooleanValue) {
            this.value.compareTo(that.value)
        } else if (this is NullValue && that is NullValue) {
            0
        } else {
            throw IllegalArgumentException("Invalid operation: $this <=> $that")
        }
    }
}

class StringValue(val value: String) : NodeValue() {
    override fun toString() = value
    override fun toBoolean(): Boolean = value.isNotEmpty()
}

fun String.toNodeValue() = StringValue(this)

class ListValue(val value: List<NodeValue>) : NodeValue() {
    override fun toString() = "[${value.joinToString(", ")}]"
    override fun toBoolean(): Boolean = value.isNotEmpty()
}

fun List<NodeValue>.toNodeValue() = ListValue(this)

class NumberValue(val value: Long) : NodeValue() {
    override fun toString() = value.toString()
    override fun toBoolean(): Boolean = value != 0L
}

fun Int.toNodeValue(): NodeValue = NumberValue(this.toLong())
fun Long.toNodeValue(): NodeValue = NumberValue(this)

class BooleanValue(val value: Boolean) : NodeValue() {
    override fun toString() = value.toString()
    override fun toBoolean(): Boolean = value
}

fun Boolean.toNodeValue() = BooleanValue(this)
fun Boolean.toLong() = if (this) 1L else 0L

class SubscriptValue(val begin: Int, val extended: Boolean, val end: Int? = null) : NodeValue() {
    override fun toString() = if (extended) "$begin:$end" else "$begin"
    override fun toBoolean(): Boolean = true
}

abstract class ProcedureValue(private val params: List<String>) : NodeValue() {
    override fun toBoolean(): Boolean = true
    fun nameArgs(context: ExecutionContext) {
        val argc = min(params.size, context.stack.args.size)
        for (i in 0 until argc) {
            context.stack[params[i]] = context.stack.args[i]
        }
    }
    abstract fun execute(context: ExecutionContext): NodeValue
    companion object {
        val Slice = NodeProcedureValue(
            listOf("begin", "end"),
            SubscriptViewNode(
                IdentifierNode(Token(TokenType.IDENTIFIER, "this")),
                SubscriptNode(
                    IdentifierNode(Token(TokenType.IDENTIFIER, "begin")),
                    true,
                    IdentifierNode(Token(TokenType.IDENTIFIER, "end"))
                )
            )
        )
        private val whiteSpace = Pattern.compile("\\s+")
        val Split = BuiltinProcedureValue(listOf("separator"), "split") { context ->
            val str = context.stack["this"]!!.asString()!!
            val arg = context.stack["separator"]?.asString()
            if (arg == null) {
                whiteSpace.split(str).filter { it.isNotEmpty() }.map { it.toNodeValue() }.toList().toNodeValue()
            } else {
                str.split(arg).map { it.toNodeValue() }.toList().toNodeValue()
            }
        }
        val Join = BuiltinProcedureValue(listOf("separator"), "join") { context ->
            val list = context.stack["this"]!!.asList()!!
            val arg = context.stack["separator"]?.asString()
            if (arg == null) {
                list.joinToString("").toNodeValue()
            } else {
                list.joinToString(arg).toNodeValue()
            }
        }
        val Find = BuiltinProcedureValue(listOf("substring"), "find") { context ->
            val expr = context.stack["this"]!!.asString()!!
            val arg = context.stack["substring"]!!.asString()!!
            expr.indexOf(arg).toNodeValue()
        }
        val Contains = BuiltinProcedureValue(listOf("substring"), "contains") { context ->
            val expr = context.stack["this"]!!.asString()!!
            val arg = context.stack["substring"]!!.asString()!!
            expr.contains(arg).toNodeValue()
        }
        val Length = BuiltinProcedureValue(listOf(), "length") { context ->
            when (val expr = context.stack["this"]!!) {
                is StringValue -> expr.value.length.toNodeValue()
                is ListValue -> expr.value.size.toNodeValue()
                else -> throw RuntimeException("$expr has no such method as \"length\"")
            }
        }
        val Time = BuiltinProcedureValue(listOf(""), "time") {
            System.currentTimeMillis().toNodeValue()
        }
        val Random = BuiltinProcedureValue(listOf("begin", "end"), "random") { context ->
            val min = context.stack["begin"]!!.asNumber()!!
            val max = context.stack["end"]!!.asNumber()!!
            (min until max).random().toNodeValue()
        }
    }
}

class BuiltinProcedureValue(params: List<String>, private val name: String, private val func: (context: ExecutionContext) -> NodeValue): ProcedureValue(params) {
    override fun toString(): String = "builtin($name)"
    override fun execute(context: ExecutionContext): NodeValue {
        nameArgs(context)
        return func(context)
    }
}

class NodeProcedureValue(params: List<String>, private val func: Node) : ProcedureValue(params) {
    override fun toString() = "procedure($func)"
    override fun execute(context: ExecutionContext): NodeValue {
        nameArgs(context)
        return func.exec(context)
    }
}

class NullValue : NodeValue() {
    override fun toString() = "null"
    override fun toBoolean(): Boolean = false
}

class Scope(private val parent: Scope?, private val symbols: MutableMap<String, NodeValue>, val args: List<NodeValue> = emptyList()) {
    operator fun get(name: String): NodeValue? {
        return symbols[name] ?: parent?.get(name)
    }
    operator fun set(name: String, value: NodeValue) {
        if(symbols[name] == null) {
            if(parent?.get(name) == null) {
                symbols[name] = value
            } else {
                parent[name] = value
            }
        } else {
            symbols[name] = value
        }
    }
    fun remove(name: String) {
        symbols.remove(name)
    }
    companion object {
        fun createRoot(defs: Map<String, NodeValue> = mapOf()): Scope {
            val builtins = mutableMapOf(
                "true" to true.toNodeValue(),
                "false" to false.toNodeValue(),
                "null" to NullValue(),
                "slice" to ProcedureValue.Slice,
                "split" to ProcedureValue.Split,
                "join" to ProcedureValue.Join,
                "find" to ProcedureValue.Find,
                "contains" to ProcedureValue.Contains,
                "length" to ProcedureValue.Length,
                "time" to ProcedureValue.Time,
                "random" to ProcedureValue.Random
            )
            builtins.putAll(defs)
            return Scope(null, builtins)
        }
    }
}

class Stack(private val scopes: MutableList<Scope>) {
    fun push(args: List<NodeValue> = emptyList()) {
        scopes.add(Scope(scopes.lastOrNull(), mutableMapOf(), args))
    }
    fun pop() {
        scopes.removeAt(scopes.lastIndex)
    }
    val args: List<NodeValue>
        get() = scopes.last().args
    operator fun get(name: String): NodeValue? {
        return scopes.lastOrNull()?.get(name)
    }
    operator fun set(name: String, value: NodeValue) {
        scopes.lastOrNull()?.set(name, value)
    }
}

abstract class Node {
    abstract fun exec(context: ExecutionContext): NodeValue
    open fun assign(context: ExecutionContext, value: NodeValue): Unit =
        throw IllegalArgumentException("Not assignable: ${this.javaClass.simpleName}")
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

class ListNode(private val items: List<ExprNode>) : Node() {
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

class SubscriptNode(private val begin: Node, private val extended: Boolean, private val end: Node? = null) :
    Node() {
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

class UnitCallNode(private val expr: Node, func: Token, private val args: ListNode) : Node() {
    private val func: String

    init {
        this.func = func.value
    }

    override fun exec(context: ExecutionContext): NodeValue {
        val args = args.exec(context).value
        return when (func) {
            "defined" -> {
                val idName = (expr as IdentifierNode).name
                (context.stack[idName] != null).toNodeValue()
            }
            else -> {
                context.stack.push(args)
                context.stack["this"] = expr.exec(context)
                val res = context.stack[func]?.asProcedure()?.execute(context)
                context.stack.pop()
                res ?: NullValue()
            }
        }
    }

    override fun toString(): String {
        return "func($func, $expr, $args)"
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
        for (i in comps.indices) {
            str += comps[i].toString()
            if (i < ops.size) {
                str += ops[i]
            }
        }
        return "expr($str)"
    }
}

class StmtScopeNode(private val content: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        context.stack.push()
        val res = content.exec(context)
        context.stack.pop()
        return res
    }

    override fun toString(): String {
        return "scope($content)"
    }
}

class StmtAssignNode(private val lvalue: Node, private val expr: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val value = expr.exec(context)
        lvalue.assign(context, value)
        return NullValue()
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
        if (condition.exec(context).toBoolean()) {
            ifBody.exec(context)
        } else {
            elseBody?.exec(context)
        }
        return NullValue()
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

class StmtListNode(private val stmts: List<Node>) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        for (node in stmts) {
            node.exec(context)
        }
        return NullValue()
    }

    override fun toString(): String {
        return "stmts(${stmts.joinToString(", ")})"
    }
}

class Parser(private val tokens: List<Token>) {
    private var current = 0

    private fun consume(type: TokenType): Token {
        if (isAtEnd()) {
            throw IllegalStateException("Unexpected end of input")
        }
        if (tokens[current].type != type) {
            throw IllegalStateException("Expected $type, got ${tokens[current]}")
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

    private fun parseStmtList(): StmtListNode {
        val stmts = mutableListOf<Node>()
        consumeLineBreak()
        while (peek().type != TokenType.EOF && peek().type != TokenType.BRACE_CLOSE) {
            stmts.add(parseStmt())
        }
        return StmtListNode(stmts)
    }

    // calls consumeLineBreak() in the end of this function
    private fun parseStmt(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.IDENTIFIER -> {
                parseStmtAssign()
            }
            TokenType.BRACKET_OPEN -> {
                parseStmtAssign()
            }
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
                val stmtList = parseStmtList()
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                StmtScopeNode(stmtList)
            }
            else -> throw IllegalStateException("Unexpected token ${token.value}")
        }
    }

    private fun parseStmtAssign(): Node {
        val lvalue = parseExpr()
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
            else -> throw IllegalStateException("Unexpected token $assignToken")
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
            TokenType.IDENTIFIER -> parseIdentifier()
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
            else -> throw IllegalStateException("Unexpected token ${token.value}")
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
                val func = consume(TokenType.BUILTIN)
                consume(TokenType.PAREN_OPEN)
                val params = parseParamList()
                consume(TokenType.PAREN_CLOSE)
                UnitCallNode(unitHead, func, params)
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

    fun parse(): Node {
        return parseStmtList()
    }
}

abstract class ExecutionContext(rootScope: Scope, val firstRun: Boolean) {
    val stack: Stack
    init {
        stack = Stack(mutableListOf(rootScope))
    }
    abstract fun say(text: String)
}

class ConsoleContext(rootScope: Scope? = null) : ExecutionContext(rootScope ?: Scope.createRoot(), true) {
    override fun say(text: String) {
        println(text)
    }
}

class Interpreter(source: String) {
    private val ast: Node

    init {
        val tokens = Tokenizer(source).scan()
        val parser = Parser(tokens)
        ast = parser.parse()
    }

    fun run(context: ExecutionContext) {
        ast.exec(context)
    }
}

fun main() {
    val inputs = mutableListOf<String>()
    while (true) {
        print("> ")
        val line = readLine() ?: break
        inputs.add(line)
    }
    val input = inputs.joinToString("\n")
//    println(input)
    val st = Scope.createRoot(mapOf("text" to StringValue("this is a brand new world the world of parsing")))
    st.remove("unknown")
    val context = ConsoleContext(st)
//    println("\nTokenizing...")
//    val tokens = Tokenizer(input).scan()
//    println(tokens.joinToString(", "))
//    println("\nParsing...")
//    val ast = Parser(tokens).parse()
//    println(ast)
//    println("\nExecuting...")
//    ast.exec(context)
    val interpreter = Interpreter(input)
    interpreter.run(context)
    println("\nDone!")
}