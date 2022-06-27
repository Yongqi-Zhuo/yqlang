package top.saucecode.yqlang.Node

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.NullValue
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer
import top.saucecode.yqlang.Scope
import top.saucecode.yqlang.Token

class StmtAssignNode(scope: Scope, private val lvalue: Node, private val expr: Node) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        val value = context.memory.allocate(expr.exec(context))
        if (lvalue is ConvertibleToAssignablePattern) {
            lvalue.toPattern(context).assign(context, value)
        } else {
            throw TypeMismatchRuntimeException(listOf(ConvertibleToAssignablePattern::class.java), lvalue)
        }
        return lvalue.exec(context)
    }

    override fun toString(): String {
        return "assign($lvalue, $expr)"
    }
}

class UnimplementedActionException(action: String): InterpretationRuntimeException("Unimplemented action: $action")

class StmtActionNode(scope: Scope, private val action: String, private val expr: Node) : Node(scope) {
    constructor(scope: Scope, action: Token, expr: Node) : this(scope, action.value, expr)

    override fun exec(context: ExecutionContext): NodeValue {
        val value = expr.exec(context)
        when (action) {
            "say" -> {
                context.say(value.printStr)
            }
            "nudge" -> {
                context.nudge(value.asInteger()!!)
            }
            "picsave" -> {
                context.picSave(value.asString()!!)
            }
            "picsend" -> {
                context.picSend(value.asString()!!)
            }
            else -> throw UnimplementedActionException(action)
        }
        return NullValue
    }

    override fun toString(): String {
        return "action($action($expr))"
    }
}

class StmtIfNode(
    scope: Scope, private val condition: Node, private val ifBody: Node, private val elseBody: Node? = null
) : Node(scope) {
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

class StmtInitNode(scope: Scope, private val stmt: Node) : Node(scope) {
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

class StmtReturnNode(scope: Scope, private val expr: Node) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        throw ReturnException(expr.exec(context))
    }

    override fun toString(): String {
        return "return($expr)"
    }
}

class StmtWhileNode(scope: Scope, private val condition: Node, private val body: Node) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        while (condition.exec(context).toBoolean() && !Thread.currentThread().isInterrupted) {
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

class StmtContinueNode(scope: Scope) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        throw ContinueException()
    }

    override fun toString(): String {
        return "continue"
    }
}

class BreakException : Exception()

class StmtBreakNode(scope: Scope) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        throw BreakException()
    }

    override fun toString(): String {
        return "break"
    }
}

class StmtForNode(scope: Scope, private val iterator: Node, private val collection: Node, private val body: Node) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        val collection = collection.exec(context)
        var res: NodeValue = NullValue
        if (collection is Iterable<*>) {
            for (item in collection) {
                if (Thread.currentThread().isInterrupted) break
                (iterator as ConvertibleToAssignablePattern).toPattern(context).assign(context,
                    context.memory.allocate(item as NodeValue))
                try {
                    res = body.exec(context)
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
    override fun exec(context: ExecutionContext): NodeValue {
        var res: NodeValue = NullValue
        try {
            if (newScope) {
                context.referenceEnvironment.pushScope()
            }
            for (node in stmts) {
                res = node.exec(context)
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