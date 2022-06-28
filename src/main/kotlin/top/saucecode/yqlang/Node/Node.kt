package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.StringValue
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer
import kotlin.math.min

sealed class Node(val scope: Scope) {
    // return value put on stack
    abstract fun generateCode(buffer: CodegenContext)
    protected var isLvalue = false
    open fun testPattern(allBinds: Boolean): Boolean {
        isLvalue = true
        return false
    }
    open fun actuallyRvalue() {
        isLvalue = false
    }
    open fun declarePattern(allBinds: Boolean): Unit = throw YqlangException("Cannot declare bind on non-bindable node")
}