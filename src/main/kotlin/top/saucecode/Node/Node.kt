package top.saucecode.Node

import top.saucecode.ExecutionContext
import top.saucecode.NodeValue.NodeValue

abstract class Node {
    abstract fun exec(context: ExecutionContext): NodeValue
    open fun assign(context: ExecutionContext, value: NodeValue): Unit =
        throw IllegalArgumentException("Not assignable: ${this.javaClass.simpleName}")
}