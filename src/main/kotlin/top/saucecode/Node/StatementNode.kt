package top.saucecode.Node

import kotlinx.serialization.Serializable
import top.saucecode.ExecutionContext
import top.saucecode.NodeValue.NodeProcedureValue
import top.saucecode.NodeValue.NodeValue
import top.saucecode.NodeValue.NullValue
import top.saucecode.Token

@Serializable
class StmtAssignNode(private val lvalue: Node, private val expr: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val value = expr.exec(context)
        lvalue.assign(context, value)
        return lvalue.exec(context)
    }

    override fun toString(): String {
        return "assign($lvalue, $expr)"
    }
}

@Serializable
class StmtActionNode(private val action: String, private val expr: Node) : Node() {
    constructor(action: Token, expr: Node) : this(action.value, expr)

    override fun exec(context: ExecutionContext): NodeValue {
        val value = expr.exec(context)
        when (action) {
            "say" -> {
                context.say(value.toString())
            }
            "nudge" -> {
                context.nudge(value.asNumber()!!)
            }
            else -> throw IllegalArgumentException("Unknown action $action")
        }
        return NullValue
    }

    override fun toString(): String {
        return "action(${action}, $expr)"
    }
}

@Serializable
class StmtIfNode(
    private val condition: Node, private val ifBody: Node, private val elseBody: Node? = null
) : Node() {
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

@Serializable
class StmtInitNode(private val stmt: Node) : Node() {
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

@Serializable
class StmtDeclNode(private val name: String, private val body: Node, private val params: ListNode) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val procedure = NodeProcedureValue(body, params, null)
        context.stack.declare(name, procedure)
        return procedure
    }

    override fun toString(): String {
        return "decl($name, $body, $params)"
    }
}

class ReturnException(val value: NodeValue) : Exception()

@Serializable
class StmtReturnNode(private val expr: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        throw ReturnException(expr.exec(context))
    }

    override fun toString(): String {
        return "return($expr)"
    }
}

@Serializable
class StmtWhileNode(private val condition: Node, private val body: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        while (condition.exec(context).toBoolean()) {
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

@Serializable
object StmtContinueNode : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        throw ContinueException()
    }

    override fun toString(): String {
        return "continue"
    }
}

class BreakException : Exception()

@Serializable
object StmtBreakNode : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        throw BreakException()
    }

    override fun toString(): String {
        return "break"
    }
}

@Serializable
class StmtForNode(private val iterator: Node, private val collection: Node, private val body: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val collection = collection.exec(context)
        var res: NodeValue = NullValue
        if (collection is Iterable<*>) {
            for (item in collection) {
                iterator.assign(context, item as NodeValue)
                try {
                    res = body.exec(context)
                } catch (continueEx: ContinueException) {
                    continue
                } catch (breakEx: BreakException) {
                    break
                }
            }
        } else {
            throw RuntimeException("$collection is not iterable")
        }
        return res
    }
}

@Serializable
class StmtListNode(private val stmts: List<Node>, private val newScope: Boolean) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        var res: NodeValue = NullValue
        try {
            if (newScope) {
                context.stack.push()
            }
            for (node in stmts) {
                res = node.exec(context)
            }
        } finally {
            if (newScope) {
                context.stack.pop()
            }
        }
        return res
    }

    override fun toString(): String {
        return "stmts(${stmts.joinToString(", ")})"
    }
}