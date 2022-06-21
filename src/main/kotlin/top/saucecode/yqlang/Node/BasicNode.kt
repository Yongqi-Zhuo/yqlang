package top.saucecode.yqlang.Node

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Token
import top.saucecode.yqlang.TokenType
import kotlin.math.min

class TypeMismatchRuntimeException(expected: List<Class<*>>, got: Any) :
    InterpretationRuntimeException("Type mismatch, expected one of ${
        expected.joinToString(", ") { it.simpleName }
    }, got ${got.javaClass.simpleName}")

@Serializable
class ValueNode(private val value: NodeValue) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        return value
    }

    override fun toString(): String {
        return value.toString()
    }
}

@Serializable
class IdentifierNode(val name: String) : Node() {

    constructor(token: Token) : this(token.value)

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

@Serializable
class IntegerNode(private val value: Long) : Node() {

    constructor(token: Token) : this(token.value.toLong())

    override fun exec(context: ExecutionContext): NodeValue {
        return value.toNodeValue()
    }

    override fun toString(): String {
        return "integer($value)"
    }
}

@Serializable
class FloatNode(private val value: Double) : Node() {

    constructor(token: Token) : this(token.value.toDouble())

    override fun exec(context: ExecutionContext): NodeValue {
        return value.toNodeValue()
    }

    override fun toString(): String {
        return "float($value)"
    }
}

@Serializable
class StringNode(private val value: String) : Node() {

    constructor(token: Token) : this(token.value)

    override fun exec(context: ExecutionContext): NodeValue {
        return value.toNodeValue()
    }

    override fun toString(): String {
        return "str(\"$value\")"
    }
}

@Serializable
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

    constructor(vararg items: String) : this(items.map { IdentifierNode(Token(TokenType.IDENTIFIER, it)) })

    override fun toString(): String {
        return "[${items.joinToString(", ")}]"
    }
}

@Serializable
class SubscriptNode(private val begin: Node, private val extended: Boolean, private val end: Node? = null) : Node() {
    override fun exec(context: ExecutionContext): SubscriptValue {
        return when (val begin = begin.exec(context)) {
            is IntegerValue -> IntegerSubscriptValue(
                begin.value.toInt(), extended, end?.exec(context)?.asInteger()?.toInt()
            )
            is StringValue -> KeySubscriptValue(begin.value)
            else -> throw TypeMismatchRuntimeException(listOf(IntegerValue::class.java, StringValue::class.java), begin)
        }
    }

    override fun toString(): String {
        return if (end != null) "subscript($begin, $end)" else "subscript($begin)"
    }
}

@Serializable
class ObjectNode(private val items: List<Pair<IdentifierNode, Node>>) : Node() {
    override fun exec(context: ExecutionContext): ObjectValue {
        return items.associateTo(mutableMapOf()) { (key, value) ->
            key.name to value.exec(context)
        }.toNodeValue()
    }

    override fun toString(): String {
        return "{${items.joinToString(", ") { (key, value) -> "$key: $value" }}}"
    }
}

@Serializable
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