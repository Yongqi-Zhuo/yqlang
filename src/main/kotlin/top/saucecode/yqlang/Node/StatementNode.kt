package top.saucecode.yqlang.Node

import top.saucecode.yqlang.CodegenContext
import top.saucecode.yqlang.Runtime.Op
import top.saucecode.yqlang.Runtime.YqlangRuntimeException
import top.saucecode.yqlang.Scope
import top.saucecode.yqlang.Token

class StmtExprNode(scope: Scope, val expr: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        buffer.add(Op.POP_SAVE_TO_REG)
    }

    override fun toString(): String {
        return "expr($expr)"
    }
}

class StmtAssignNode(scope: Scope, private val lvalue: Node, private val expr: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        lvalue.generateCode(buffer)
        buffer.add(Op.CLEAR_REG)
    }

    override fun toString(): String {
        return "assign($lvalue, $expr)"
    }
}

class UnimplementedActionException(action: String): YqlangRuntimeException("Unimplemented action: $action")

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
        buffer.add(Op.ACTION, actionCode)
        buffer.add(Op.CLEAR_REG)
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
        buffer.add(Op.JUMP_ZERO, mid)
        ifBody.generateCode(buffer)
        buffer.add(Op.JUMP, end)
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
        buffer.add(Op.JUMP_NOT_FIRST_RUN, jump)
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
        buffer.add(Op.POP_RETURN)
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
        buffer.add(Op.JUMP_ZERO, end)
        buffer.withLoopContext(start, end) {
            body.generateCode(buffer)
        }
        buffer.add(Op.JUMP, start)
        buffer.putLabel(end)
    }

    override fun toString(): String {
        return "while($condition, $body)"
    }
}

class StmtContinueNode(scope: Scope) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        buffer.add(Op.JUMP, buffer.continueLabel!!)
    }

    override fun toString(): String {
        return "continue"
    }
}

class StmtBreakNode(scope: Scope) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        buffer.add(Op.JUMP, buffer.breakLabel!!)
    }

    override fun toString(): String {
        return "break"
    }
}

class StmtForNode(scope: Scope, private val iterator: Node, private val collection: Node, private val body: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        collection.generateCode(buffer)
        // now the top of stack is the collection
        buffer.add(Op.PUSH_ITERATOR)
        val start = buffer.requestLabel()
        val end = buffer.requestLabel()
        buffer.putLabel(start)
        buffer.add(Op.JUMP_IF_ITER_DONE, end)
        buffer.add(Op.ITER_NEXT_PUSH)
        iterator.generateCode(buffer)
        buffer.withLoopContext(start, end) {
            body.generateCode(buffer)
        }
        buffer.add(Op.JUMP, start)
        buffer.putLabel(end)
        buffer.add(Op.POP_ITERATOR)
    }
}

class StmtListNode(scope: Scope, private val stmts: List<Node>) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        buffer.add(Op.CLEAR_REG)
        stmts.forEach { it.generateCode(buffer) }
    }

    override fun toString(): String {
        return "stmts(${stmts.joinToString(", ")})"
    }
}