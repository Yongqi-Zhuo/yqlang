package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.BooleanValue
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.NullValue
import top.saucecode.yqlang.Runtime.ImmediateCode
import top.saucecode.yqlang.Runtime.Op

sealed class OperatorNode(scope: Scope) : Node(scope) {
    enum class OperatorType {
        UNARY, BINARY
    }

    data class Precedence(val operators: List<TokenType>, val opType: OperatorType) {
        val isAndOr: Boolean get() = opType == OperatorType.BINARY
                && operators.contains(TokenType.LOGIC_AND) || operators.contains(TokenType.LOGIC_OR)
        val isAnd: Boolean get() = opType == OperatorType.BINARY && operators.contains(TokenType.LOGIC_AND)
    }
    companion object {
        val PrecedenceList = listOf(
            Precedence(listOf(TokenType.NOT, TokenType.MINUS),
                OperatorType.UNARY),
            Precedence(listOf(TokenType.MULT, TokenType.DIV, TokenType.MOD),
                OperatorType.BINARY),
            Precedence(listOf(TokenType.PLUS, TokenType.MINUS),
                OperatorType.BINARY),
            Precedence(listOf(TokenType.GREATER_EQ, TokenType.LESS_EQ, TokenType.GREATER, TokenType.LESS),
                OperatorType.BINARY),
            Precedence(listOf(TokenType.EQUAL, TokenType.NOT_EQUAL),
                OperatorType.BINARY),
            Precedence(listOf(TokenType.LOGIC_AND),
                OperatorType.BINARY),
            Precedence(listOf(TokenType.LOGIC_OR),
                OperatorType.BINARY),
            Precedence(listOf(TokenType.IN),
                OperatorType.BINARY),
        )
    }
}

enum class BinaryOperatorCode(val type: TokenType) {
    ADD(TokenType.PLUS),
    SUB(TokenType.MINUS),
    MUL(TokenType.MULT),
    DIV(TokenType.DIV),
    MOD(TokenType.MOD),
    EQUAL(TokenType.EQUAL),
    NOT_EQUAL(TokenType.NOT_EQUAL),
    GREATER(TokenType.GREATER),
    LESS(TokenType.LESS),
    GREATER_EQ(TokenType.GREATER_EQ),
    LESS_EQ(TokenType.LESS_EQ),
    LOGIC_AND(TokenType.LOGIC_AND),
    LOGIC_OR(TokenType.LOGIC_OR),
    IN(TokenType.IN);
    val value: Int get() = values().indexOf(this)
    companion object {
        fun fromToken(type: TokenType) = values().firstOrNull { it.type == type }
            ?: throw CompileException("Unknown binary operator $type")
        fun fromValue(value: Int) = values()[value]
    }
}

open class BinaryOperatorNode(scope: Scope, protected val components: List<Node>, protected val ops: List<TokenType>) : OperatorNode(scope) {
    override fun generateCode(buffer: CodegenContext) {
        if (components.isEmpty()) buffer.add(Op.PUSH_IMM, ImmediateCode.NULL.code)
        components[0].generateCode(buffer)
        for (i in 1 until components.size) {
            components[i].generateCode(buffer)
            val op = BinaryOperatorCode.fromToken(ops[i - 1])
            buffer.add(Op.BINARY_OP, op.value)
        }
    }

    override fun toString(): String {
        if (components.size == 1) return components[0].toString()
        var str = ""
        components.forEachIndexed { index, node -> str += "$node${if (index < ops.size) ops[index].toHumanReadable() else ""}" }
        return "Binary($str)"
    }
}

class LogicBinaryOperatorNode(scope: Scope, components: List<Node>, ops: List<TokenType>, private val isAnd: Boolean) : BinaryOperatorNode(scope, components, ops) {
    override fun generateCode(buffer: CodegenContext) {
        if (components.isEmpty()) buffer.add(Op.PUSH_IMM, ImmediateCode.NULL.code)
        else if (components.size == 1) {
            components[0].generateCode(buffer)
        } else {
            components[0].generateCode(buffer)
            val stop = buffer.requestLabel()
            if (isAnd) {
                buffer.add(Op.JUMP_ZERO, stop)
            } else { // isOr
                buffer.add(Op.JUMP_NOT_ZERO, stop)
            }
            for (i in 1 until components.size) {
                components[i].generateCode(buffer)
                val op = BinaryOperatorCode.fromToken(ops[i - 1])
                buffer.add(Op.BINARY_OP, op.value)
            }
            buffer.putLabel(stop)
            buffer.add(Op.TO_BOOL)
        }
    }
}

enum class UnaryOperatorCode(val type: TokenType) {
    MINUS(TokenType.MINUS),
    NOT(TokenType.NOT);
    val value: Int = values().indexOf(this)
    companion object {
        fun fromToken(type: TokenType) = values().firstOrNull { it.type == type }
            ?: throw CompileException("Unknown unary operator $type")
        fun fromValue(value: Int) = values()[value]
    }
}

class UnaryOperatorNode(scope: Scope, private val component: Node, private val op: TokenType) : OperatorNode(scope) {
    override fun generateCode(buffer: CodegenContext) {
        component.generateCode(buffer)
        val op = UnaryOperatorCode.fromToken(op)
        buffer.add(Op.UNARY_OP, op.value)
    }

    override fun toString(): String {
        return "Unary(${op.toHumanReadable()}$component)"
    }
}