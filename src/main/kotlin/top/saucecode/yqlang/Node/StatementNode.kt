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

class StmtAssignNode(scope: Scope, private val lvalue: Node, private val expr: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        lvalue.generateCode(buffer)
        buffer.add(ByteCode(Op.PUSH_IMM.code, ImmediateCode.NULL.code))
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
            return values().first { it.code == code }
        }
    }
}
class StmtActionNode(scope: Scope, private val action: String, private val expr: Node) : Node(scope) {
    constructor(scope: Scope, action: Token, expr: Node) : this(scope, action.value, expr)

    override fun generateCode(buffer: CodegenContext) {
        expr.generateCode(buffer)
        val actionCode = ActionCode.valueOf(action.uppercase()).code
        buffer.add(ByteCode(Op.ACTION.code, actionCode))
        buffer.add(ByteCode(Op.PUSH_IMM.code, ImmediateCode.NULL.code))
    }

    override fun toString(): String {
        return "action($action($expr))"
    }
}

class StmtIfNode(
    scope: Scope, private val condition: Node, private val ifBody: Node, private val elseBody: Node? = null
) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        return if (condition.generateCode().toBoolean()) {
            ifBody.generateCode()
        } else {
            elseBody?.generateCode() ?: NullValue
        }
    }

    override fun toString(): String {
        val elseText = if (elseBody == null) "" else ", else($elseBody)"
        return "if($condition, body($ifBody)$elseText)"
    }
}

class StmtInitNode(scope: Scope, private val stmt: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        if (context.firstRun) {
            stmt.generateCode()
        }
        return NullValue
    }

    override fun toString(): String {
        return "init($stmt)"
    }
}

class ReturnException(val value: NodeValue) : Exception()

class StmtReturnNode(scope: Scope, private val expr: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        throw ReturnException(expr.generateCode())
    }

    override fun toString(): String {
        return "return($expr)"
    }
}

class StmtWhileNode(scope: Scope, private val condition: Node, private val body: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        while (condition.generateCode().toBoolean() && !Thread.currentThread().isInterrupted) {
            try {
                body.generateCode()
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

class StmtContinueNode(scope: Scope) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        throw ContinueException()
    }

    override fun toString(): String {
        return "continue"
    }
}

class BreakException : Exception()

class StmtBreakNode(scope: Scope) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        throw BreakException()
    }

    override fun toString(): String {
        return "break"
    }
}

class StmtForNode(scope: Scope, private val iterator: Node, private val collection: Node, private val body: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        val collection = collection.generateCode()
        var res: NodeValue = NullValue
        if (collection is Iterable<*>) {
            for (item in collection) {
                if (Thread.currentThread().isInterrupted) break
                (iterator as ConvertibleToAssignablePattern).toPattern(context).assign(context,
                    context.memory.allocate(item as NodeValue))
                try {
                    res = body.generateCode()
                } catch (continueEx: ContinueException) {
                    continue
                } catch (breakEx: BreakException) {
                    break
                }
            }
        } else {
            throw InterpretationRuntimeException("$collection is not iterable")
        }
        return res
    }
}

class StmtListNode(scope: Scope, private val stmts: List<Node>, private val newScope: Boolean) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        var res: NodeValue = NullValue
        try {
            if (newScope) {
                context.referenceEnvironment.pushScope()
            }
            for (node in stmts) {
                res = node.generateCode()
            }
        } finally {
            if (newScope) {
                context.referenceEnvironment.popScope()
            }
        }
        return res
    }

    override fun toString(): String {
        return "stmts(${stmts.joinToString(", ")})"
    }
}