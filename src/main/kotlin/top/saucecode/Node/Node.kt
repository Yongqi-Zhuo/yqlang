package top.saucecode.Node

import kotlinx.serialization.Serializable
import top.saucecode.ExecutionContext
import top.saucecode.NodeValue.NodeValue

@Serializable
sealed class Node {
    abstract fun exec(context: ExecutionContext): NodeValue
    open fun assign(context: ExecutionContext, value: NodeValue): Unit =
        throw IllegalArgumentException("Not assignable: ${this.javaClass.simpleName}")
}