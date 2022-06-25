package top.saucecode.yqlang.Node

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.NodeValue.BooleanValue
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.NullValue
import top.saucecode.yqlang.TokenType

@Serializable
sealed class OperatorNode : Node() {
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
                listOf(TokenType.GREATER_EQ, TokenType.LESS_EQ, TokenType.GREATER, TokenType.LESS),
                OperatorType.BINARY
            ),
            Precedence(listOf(TokenType.EQUAL, TokenType.NOT_EQUAL), OperatorType.BINARY),
            Precedence(listOf(TokenType.LOGIC_AND), OperatorType.BINARY),
            Precedence(listOf(TokenType.LOGIC_OR), OperatorType.BINARY),
            Precedence(listOf(TokenType.IN), OperatorType.BINARY)
        )
    }
}

@Serializable
class BinaryOperatorNode(private val components: List<Node>, private val ops: List<TokenType>) : OperatorNode(), ConvertibleToAssignablePattern {
    class LazyNodeValue(private val node: Node? = null, private val context: ExecutionContext? = null) {
        private var result: NodeValue? = null
        constructor(value: NodeValue) : this(null, null) {
            result = value
        }
        operator fun invoke(): NodeValue {
            return result ?: node!!.exec(context!!)
        }
    }
    companion object {
        private val opMap =
            mapOf<TokenType, (LazyNodeValue, LazyNodeValue) -> LazyNodeValue>(
                TokenType.PLUS to { a, b -> LazyNodeValue(a() + b()) },
                TokenType.MINUS to { a, b -> LazyNodeValue(a() - b()) },
                TokenType.MULT to { a, b -> LazyNodeValue(a() * b()) },
                TokenType.DIV to { a, b -> LazyNodeValue(a() / b()) },
                TokenType.MOD to { a, b -> LazyNodeValue(a() % b()) },
                TokenType.EQUAL to { a, b -> LazyNodeValue(BooleanValue(a() == b())) },
                TokenType.NOT_EQUAL to { a, b -> LazyNodeValue(BooleanValue(a() != b())) },
                TokenType.GREATER to { a, b -> LazyNodeValue(BooleanValue(a() > b())) },
                TokenType.LESS to { a, b -> LazyNodeValue(BooleanValue(a() < b())) },
                TokenType.GREATER_EQ to { a, b -> LazyNodeValue(BooleanValue(a() >= b())) },
                TokenType.LESS_EQ to { a, b -> LazyNodeValue(BooleanValue(a() <= b())) },
                TokenType.LOGIC_AND to { a, b -> LazyNodeValue(BooleanValue(a().toBoolean() && b().toBoolean())) },
                TokenType.LOGIC_OR to { a, b -> LazyNodeValue(BooleanValue(a().toBoolean() || b().toBoolean())) },
                TokenType.IN to { a, b -> LazyNodeValue(BooleanValue(a() in b())) }
            )
    }

    override fun exec(context: ExecutionContext): NodeValue {
        return if (components.isEmpty()) NullValue
        else if (components.size == 1) {
            components[0].exec(context)
        } else {
            val lazyValues = components.map { LazyNodeValue(it, context) }
            var res = lazyValues[0]
            for (i in 1 until components.size) {
                val op = ops[i - 1]
                val next = lazyValues[i]
                res = opMap[op]!!(res, next)
            }
            res()
        }
    }

    override fun toPattern(context: ExecutionContext): AssignablePattern {
        if (components.size == 1) {
            val what = components[0]
            if (what is ConvertibleToAssignablePattern) {
                return what.toPattern(context)
            }
        }
        throw TypeMismatchRuntimeException(listOf(ConvertibleToAssignablePattern::class.java), this)
    }

    override fun toString(): String {
        if (components.size == 1) return components[0].toString()
        var str = ""
        components.forEachIndexed { index, node -> str += "$node${if (index < ops.size) ops[index].toHumanReadable() else ""}" }
        return "Binary($str)"
    }
}

@Serializable
class UnaryOperatorNode(private val component: Node, private val op: TokenType) : OperatorNode() {
    companion object {
        private val opMap = mapOf<TokenType, (ExecutionContext, Node) -> NodeValue>(
            TokenType.MINUS to { context, node -> -node.exec(context) },
            TokenType.NOT to { context, node -> !node.exec(context) },
        )
    }

    override fun exec(context: ExecutionContext): NodeValue {
        return opMap[op]!!(context, component)
    }

    override fun toString(): String {
        return "Unary(${op.toHumanReadable()}$component)"
    }
}