package top.saucecode.yqlang.Node

import top.saucecode.yqlang.CodegenContext
import top.saucecode.yqlang.Runtime.Op
import top.saucecode.yqlang.Runtime.YqlangRuntimeException
import top.saucecode.yqlang.Scope
import top.saucecode.yqlang.Token

class StmtExprNode(scope: Scope, private val expr: ExprNode) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        buffer.add(Op.POP_SAVE_TO_REG)
    }
    init {
        expr.declareProduce(true)
    }
    override fun toString(): String = "expr($expr)"
}

class StmtAssignNode(scope: Scope, private val lvalue: ExprNode, private val expr: ExprNode) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        lvalue.generateCode(buffer)
        buffer.add(Op.CLEAR_REG)
    }
    init {
        expr.declareProduce(false)
        lvalue.declareConsume(false)
    }
    override fun toString(): String {
        return "assign($lvalue, $expr)"
    }
}

enum class OpAssignCode(val value: Int) {
    ADD_ASSIGN(0), SUB_ASSIGN(1), MUL_ASSIGN(2), DIV_ASSIGN(3), MOD_ASSIGN(4);
    companion object {
        fun from(value: Int): OpAssignCode {
            return values()[value]
        }
    }
}
class StmtOpAssignNode(scope: Scope, private val lvalue: ExprNode, private val opType: OpAssignCode, private val expr: ExprNode) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        lvalue.generateCode(buffer)
        expr.generateCode(buffer)
        buffer.add(Op.OP_ASSIGN, opType.value)
    }
    init {
        lvalue.declareProduce(true)
        expr.declareProduce(false)
    }

    override fun toString(): String {
        return "opAssign($lvalue, ${opType.name}, $expr)"
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
class StmtActionNode(scope: Scope, private val action: String, private val expr: ExprNode) : Node(scope) {
    constructor(scope: Scope, action: Token, expr: ExprNode) : this(scope, action.value, expr)
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        val actionCode = ActionCode.valueOf(action.uppercase()).code
        buffer.add(Op.ACTION, actionCode)
        buffer.add(Op.CLEAR_REG)
    }
    init {
        expr.declareProduce(true)
    }
    override fun toString(): String {
        return "action($action($expr))"
    }
}

class StmtIfNode(
    scope: Scope, private val condition: ExprNode, private val ifBody: Node, private val elseBody: Node? = null
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
    init {
        condition.declareProduce(true)
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

class StmtReturnNode(scope: Scope, private val expr: ExprNode) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        buffer.add(Op.POP_RETURN)
    }
    init {
        expr.declareProduce(false)
    }
    override fun toString(): String {
        return "return($expr)"
    }
}

class StmtWhileNode(scope: Scope, private val condition: ExprNode, private val body: Node) : Node(scope) {
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
    init {
        condition.declareProduce(true)
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

class StmtForNode(scope: Scope, private val iterator: ExprNode, private val collection: ExprNode, private val body: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        collection.generateCode(buffer)
        // now the top of stack is the collection
        buffer.add(Op.PUSH_ITERATOR)
        val start = buffer.requestLabel()
        val end = buffer.requestLabel()
        buffer.putLabel(start)
        buffer.add(Op.JUMP_IF_ITER_DONE, end)
        buffer.add(Op.ITER_NEXT_PUSH)
        // TODO: iterator must rebind! add such semantics
        iterator.generateCode(buffer)
        buffer.withLoopContext(start, end) {
            body.generateCode(buffer)
        }
        buffer.add(Op.JUMP, start)
        buffer.putLabel(end)
        buffer.add(Op.POP_ITERATOR)
    }
    init {
        iterator.declareConsume(true)
        collection.declareProduce(false)
    }
    override fun toString(): String = "for($iterator, $collection, $body)"
}

class StmtListNode(scope: Scope, private val stmts: List<Node>) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
//        buffer.add(Op.CLEAR_REG)
        stmts.forEach { it.generateCode(buffer) }
    }
    override fun toString(): String {
        return "{${stmts.joinToString(", ")}}"
    }
}