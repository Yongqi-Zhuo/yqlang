package top.saucecode.yqlang

import top.saucecode.yqlang.NodeValue.CollectionValue
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.Runtime.ByteCode
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer
import top.saucecode.yqlang.Runtime.StaticPointer

class CodegenContext {
    val text = mutableListOf<ByteCode>()
    fun add(code: ByteCode) {
        this.text.add(code)
    }
    val labels = mutableListOf<Int>()
    fun requestLabel(): Int {
        labels.add(-1)
        return labels.lastIndex
    }
    fun putLabel(label: Int) {
        labels[label] = text.size
    }
    val memory = Memory()
    fun addStaticValue(value: NodeValue): Pointer = memory.addStaticValue(value)
    fun addStaticString(value: String): Pointer = memory.addStaticString(value)
    fun reserveStatics(values: List<NodeValue>) {
        memory.addStatics(values)
    }
    val loopContexts = mutableListOf<Pair<Int, Int>>()
    fun withLoopContext(labelContinue: Int, labelBreak: Int, block: () -> Unit) {
        loopContexts.add(labelContinue to labelBreak)
        block()
        loopContexts.removeLast()
    }
    val continueLabel: Int? get() = loopContexts.lastOrNull()?.first
    val breakLabel: Int? get() = loopContexts.lastOrNull()?.second
}