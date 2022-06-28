package top.saucecode.yqlang.Node

import top.saucecode.yqlang.CodegenContext
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.NullValue
import top.saucecode.yqlang.Runtime.ByteCode
import top.saucecode.yqlang.Runtime.ImmediateCode
import top.saucecode.yqlang.Runtime.Op
import top.saucecode.yqlang.Scope
import top.saucecode.yqlang.Token
import kotlin.math.exp

class StmtExprNode(scope: Scope, val expr: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        buffer.add(ByteCode(Op.POP_SAVE_TO_REG.code))
    }

    override fun toString(): String {
        return "expr($expr)"
    }
}

class StmtAssignNode(scope: Scope, private val lvalue: Node, private val expr: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        lvalue.generateCode(buffer)
        buffer.add(ByteCode(Op.CLEAR_REG.code))
    }

    override fun toString(): String {
        return "assign($lvalue, $expr)"
    }
}

class UnimplementedActionException(action: String): InterpretationRuntimeException("Unimplemented action: $action")

enum class ActionCode(val code: Int) {
    SAY(0), NUDGE(1), PICSAVE(2), PICSEND(3);
    companion object {
        fun fromCode(code: Int): ActionCode {
            return values().firstOrNull { it.code == code } ?: throw UnimplementedActionException("$code")
        }
    }
}
class StmtActionNode(scope: Scope, private val action: String, private val expr: Node) : Node(scope) {
    constructor(scope: Scope, action: Token, expr: Node) : this(scope, action.value, expr)

    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        val actionCode = ActionCode.valueOf(action.uppercase()).code
        buffer.add(ByteCode(Op.ACTION.code, actionCode))
        buffer.add(ByteCode(Op.CLEAR_REG.code))
    }

    override fun toString(): String {
        return "action($action($expr))"
    }
}

class StmtIfNode(
    scope: Scope, private val condition: Node, private val ifBody: Node, private val elseBody: Node? = null
) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        val mid = buffer.requestLabel()
        val end = buffer.requestLabel()
        condition.generateCode(buffer)
        buffer.add(ByteCode(Op.JUMP_ZERO.code, mid))
        ifBody.generateCode(buffer)
        buffer.add(ByteCode(Op.JUMP.code, end))
        buffer.putLabel(mid)
        elseBody?.generateCode(buffer)
        buffer.putLabel(end)
    }

    override fun toString(): String {
        val elseText = if (elseBody == null) "" else ", else($elseBody)"
        return "if($condition, body($ifBody)$elseText)"
    }
}

class StmtInitNode(scope: Scope, private val stmt: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        val jump = buffer.requestLabel()
        buffer.add(ByteCode(Op.JUMP_NOT_FIRST_RUN.code, jump))
        stmt.generateCode(buffer)
        buffer.putLabel(jump)
    }

    override fun toString(): String {
        return "init($stmt)"
    }
}

class StmtReturnNode(scope: Scope, private val expr: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        buffer.add(ByteCode(Op.POP_RETURN.code))
    }

    override fun toString(): String {
        return "return($expr)"
    }
}

class StmtWhileNode(scope: Scope, private val condition: Node, private val body: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        val start = buffer.requestLabel()
        val end = buffer.requestLabel()
        buffer.putLabel(start)
        condition.generateCode(buffer)
        buffer.add(ByteCode(Op.JUMP_ZERO.code, end))
        buffer.withLoopContext(start, end) {
            body.generateCode(buffer)
        }
        buffer.add(ByteCode(Op.JUMP.code, start))
        buffer.putLabel(end)
    }

    override fun toString(): String {
        return "while($condition, $body)"
    }
}

class StmtContinueNode(scope: Scope) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        buffer.add(ByteCode(Op.JUMP.code, buffer.continueLabel!!))
    }

    override fun toString(): String {
        return "continue"
    }
}

class StmtBreakNode(scope: Scope) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        buffer.add(ByteCode(Op.JUMP.code, buffer.breakLabel!!))
    }

    override fun toString(): String {
        return "break"
    }
}

class StmtForNode(scope: Scope, private val iterator: Node, private val collection: Node, private val body: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        collection.generateCode(buffer)
        // now the top of stack is the collection
        buffer.add(ByteCode(Op.PUSH_ITERATOR.code))
        val start = buffer.requestLabel()
        val end = buffer.requestLabel()
        buffer.putLabel(start)
        buffer.add(ByteCode(Op.JUMP_IF_ITER_DONE.code, end))
        buffer.add(ByteCode(Op.ITER_NEXT_PUSH.code))
        iterator.generateCode(buffer)
        buffer.withLoopContext(start, end) {
            body.generateCode(buffer)
        }
        buffer.putLabel(end)
        buffer.add(ByteCode(Op.POP_ITERATOR.code))
    }
}

class StmtListNode(scope: Scope, private val stmts: List<Node>) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        buffer.add(ByteCode(Op.CLEAR_REG.code))
        stmts.forEach { it.generateCode(buffer) }
    }

    override fun toString(): String {
        return "stmts(${stmts.joinToString(", ")})"
    }
}