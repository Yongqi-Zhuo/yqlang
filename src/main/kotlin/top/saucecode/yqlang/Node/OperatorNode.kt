package top.saucecode.yqlang.Node

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.NullValue
import top.saucecode.yqlang.NodeValue.toNodeValue
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

// TODO: delete ValueNode
@Serializable
class ValueNode(val value: NodeValue) : OperatorNode() {
    override fun exec(context: ExecutionContext): NodeValue {
        return value
    }
}
@Serializable
class BinaryOperatorNode(private val components: List<Node>, private val ops: List<TokenType>) : OperatorNode(), ConvertibleToAssignablePattern {
    companion object {
        private val opMap =
            mapOf<TokenType, (ExecutionContext, Node, Node) -> NodeValue>(
                TokenType.PLUS
                        to { context, left, right -> left.exec(context) + right.exec(context) },
                TokenType.MINUS
                        to { context, left, right -> left.exec(context) - right.exec(context) },
                TokenType.MULT
                        to { context, left, right -> left.exec(context) * right.exec(context) },
                TokenType.DIV
                        to { context, left, right -> left.exec(context) / right.exec(context) },
                TokenType.MOD
                        to { context, left, right -> left.exec(context) % right.exec(context) },
                TokenType.EQUAL
                        to { context, left, right -> (left.exec(context) == right.exec(context)).toNodeValue() },
                TokenType.NOT_EQUAL
                        to { context, left, right -> (left.exec(context) != right.exec(context)).toNodeValue() },
                TokenType.GREATER
                        to { context, left, right -> (left.exec(context) > right.exec(context)).toNodeValue() },
                TokenType.LESS
                        to { context, left, right -> (left.exec(context) < right.exec(context)).toNodeValue() },
                TokenType.GREATER_EQ
                        to { context, left, right -> (left.exec(context) >= right.exec(context)).toNodeValue() },
                TokenType.LESS_EQ
                        to { context, left, right -> (left.exec(context) <= right.exec(context)).toNodeValue() },
                TokenType.LOGIC_AND
                        to { context, left, right ->
                    (left.exec(context).toBoolean() && right.exec(context).toBoolean()).toNodeValue()
                },
                TokenType.LOGIC_OR
                        to { context, left, right ->
                    (left.exec(context).toBoolean() || right.exec(context).toBoolean()).toNodeValue()
                },
                TokenType.IN
                        to { context, left, right -> (left.exec(context) in right.exec(context)).toNodeValue() })
    }

    override fun exec(context: ExecutionContext): NodeValue {
        return if (components.isEmpty()) NullValue
        else if (components.size == 1) {
            components[0].exec(context)
        } else {
            var res = components[0].exec(context)
            for (i in 1 until components.size) {
                val op = ops[i - 1]
                val next = components[i]
                res = opMap[op]!!(context, ValueNode(res), next)
            }
            res
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