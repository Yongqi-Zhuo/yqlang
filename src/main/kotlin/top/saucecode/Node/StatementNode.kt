package top.saucecode.Node

import top.saucecode.ExecutionContext
import top.saucecode.NodeValue.NodeValue
import top.saucecode.NodeValue.NullValue
import top.saucecode.Token
import top.saucecode.TokenType

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

class StmtActionNode(private val action: Token, private val expr: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        if (action.type != TokenType.ACTION) {
            throw IllegalArgumentException("Expected ACTION, got ${action.type}")
        }
        val value = expr.exec(context)
        when (action.value) {
            "say" -> {
                context.say(value.toString())
            }
            "nudge" -> {
                context.nudge(value.asNumber()!!)
            }
            else -> throw IllegalArgumentException("Unknown action ${action.value}")
        }
        return NullValue
    }

    override fun toString(): String {
        return "action(${action.value}, $expr)"
    }
}

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

class ReturnException(val value: NodeValue) : Exception()
class StmtFuncNode(private val content: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val res: NodeValue?
        try {
            res = content.exec(context)
        } catch (e: ReturnException) {
            return e.value
        }
        return res
    }

    override fun toString(): String {
        return "func($content)"
    }
}

class StmtReturnNode(private val expr: Node) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        throw ReturnException(expr.exec(context))
    }

    override fun toString(): String {
        return "return($expr)"
    }
}

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
class StmtContinueNode : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        throw ContinueException()
    }

    override fun toString(): String {
        return "continue"
    }
}

class BreakException : Exception()
class StmtBreakNode : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        throw BreakException()
    }

    override fun toString(): String {
        return "break"
    }
}

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