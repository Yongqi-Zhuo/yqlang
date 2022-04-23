package top.saucecode.NodeValue

import top.saucecode.ExecutionContext
import top.saucecode.Node.ListNode
import top.saucecode.Node.Node

sealed class ProcedureValue(protected val params: ListNode, protected var self: NodeValue?) : NodeValue() {
    override fun toBoolean(): Boolean = true
    abstract fun execute(context: ExecutionContext, self: NodeValue?): NodeValue
    fun call(context: ExecutionContext, args: ListValue): NodeValue {
        val res: NodeValue
        try {
            context.stack.push(args)
            res = execute(context, self)
        } finally {
            context.stack.pop()
        }
        return res
    }

    fun bind(self: NodeValue?): ProcedureValue {
        this.self = self
        return this
    }

    abstract fun copy(): ProcedureValue
}

class BuiltinProcedureValue(
    private val name: String,
    params: ListNode,
    private val func: (context: ExecutionContext) -> NodeValue,
    self: NodeValue?
) : ProcedureValue(params, self) {
    override fun toString(): String = "builtin($name)"
    override fun execute(context: ExecutionContext, self: NodeValue?): NodeValue {
        context.stack.nameArgs(context, params, self)
        return func(context)
    }

    override fun copy(): ProcedureValue {
        return BuiltinProcedureValue(name, params, func, self)
    }
}

class NodeProcedureValue(private val func: Node, params: ListNode, self: NodeValue?) :
    ProcedureValue(params, self) {
    override fun toString() = "procedure($func)"
    override fun execute(context: ExecutionContext, self: NodeValue?): NodeValue {
        context.stack.nameArgs(context, params, self)
        return func.exec(context)
    }

    override fun copy(): ProcedureValue {
        return NodeProcedureValue(func, params, self)
    }
}