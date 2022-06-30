package top.saucecode.yqlang

import top.saucecode.yqlang.Node.Node
import top.saucecode.yqlang.NodeValue.ClosureValue
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.NullValue
import top.saucecode.yqlang.Runtime.*

data class CodegenResult(val symbolTable: SymbolTable, val preloadedMemory: Memory)
class CodeGenerator {
    fun generate(ast: Node): CodegenResult {
        val buffer = CodegenContext()
        buffer.reserveStatics(ast.scope.currentFrame.reserveGlobals())
        ast.generateCode(buffer)
        buffer.add(Op.EXIT)
        buffer.linkLibrary()
        buffer.memory.text = buffer.text
        buffer.memory.labels = buffer.labels
        return CodegenResult(ast.scope.exportSymbolTable(), buffer.memory)
    }
}

class CodegenContext(val memory: Memory = Memory()) {
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
    fun addStaticValue(value: NodeValue): Pointer = memory.addStaticValue(value)
    fun addStaticString(value: String): Pointer = memory.addStaticString(value)
    fun reserveStatics(values: List<NodeValue>) {
        memory.addStatics(values)
    }
    private val loopContexts = mutableListOf<Pair<Int, Int>>()
    fun withLoopContext(labelContinue: Int, labelBreak: Int, block: () -> Unit) {
        loopContexts.add(labelContinue to labelBreak)
        block()
        loopContexts.removeLast()
    }
    val continueLabel: Int? get() = loopContexts.lastOrNull()?.first
    val breakLabel: Int? get() = loopContexts.lastOrNull()?.second
    private val linkages = mutableListOf<Pair<String, Int>>()
    fun includeLibrary(name: String): Pointer {
        val existing = linkages.firstOrNull { it.first == name }
        if (existing != null) {
            return existing.second
        }
        val ptr = memory.addStaticValue(NullValue)
        linkages.add(name to ptr)
        return ptr
    }
    fun linkLibrary() {
        for ((name, ptr) in linkages) {
            val entry = requestLabel()
            putLabel(entry)
            // no need to assign the parameters or expand captures, just invoke the builtin
            add(Op.INVOKE_BUILTIN, BuiltinProcedures.id(name))
            // no need to return, vm does it all
            // No captures. No dynamic closure creation.
            memory[ptr] = ClosureValue(-1, entry)
        }
    }
}