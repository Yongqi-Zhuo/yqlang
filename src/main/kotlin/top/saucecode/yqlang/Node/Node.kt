package top.saucecode.yqlang.Node

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.NodeValue.NodeValue

class AssignmentRuntimeException(self: Any, value: Any, msg: String? = null) :
    InterpretationRuntimeException("$value cannot be assigned to ${self.javaClass.simpleName}${msg?.let { ": $msg" } ?: ""}")

@Serializable
sealed class Node {
    abstract fun exec(context: ExecutionContext): NodeValue
    open fun assign(context: ExecutionContext, value: NodeValue): Unit =
        throw AssignmentRuntimeException(this, value)
}