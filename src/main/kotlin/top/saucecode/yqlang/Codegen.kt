package top.saucecode.yqlang

import top.saucecode.yqlang.NodeValue.CollectionValue
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.Runtime.*

class CodegenContext {
    val text = mutableListOf<ByteCode>()
    fun add(opcode: Op, operand: Int = 0) {
        this.text.add(ByteCode(opcode.code, operand))
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