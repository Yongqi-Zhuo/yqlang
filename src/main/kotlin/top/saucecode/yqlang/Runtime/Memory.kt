package top.saucecode.yqlang.Runtime

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.Constants
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.NullValue
import top.saucecode.yqlang.NodeValue.PassByReferenceNodeValue

enum class PassingScheme {
    BY_VALUE, BY_REFERENCE
}

@Serializable
data class Pointer(val loc: Memory.Location, val offset: Int) {
    fun add(offset: Int) = Pointer(loc, this.offset + offset)
    companion object {
        val caller = Pointer(Memory.Location.STACK, 2)
        val args = Pointer(Memory.Location.STACK, 3)
        fun arg(index: Int) = Pointer(Memory.Location.STACK, 4 + index)
    }
}
// TODO: GC
@Serializable
class Memory {
    enum class Location {
        STACK, HEAP, BUILTIN, STATIC
    }
    private val stack: MutableList<Int> = mutableListOf()
    private var bp: Int = 0
    fun pushFrame(pc: Int, caller: Pointer?, args: Pointer) {
        stack.add(bp)
        bp = stack.lastIndex
        stack.add(pc)
        if (caller != null) {
            assert(caller.loc == Location.HEAP) { "caller must be in heap" }
            stack.add(caller.offset)
        } else {
            stack.add(allocate(NullValue).offset)
        }
        assert(args.loc == Location.HEAP) { "args must be in heap" }
        stack.add(args.offset)
        val argPointers = get(args).asList()!!
        argPointers.forEach {
            val ptr = allocate(NullValue)
            mov(ptr, it)
            stack.add(ptr.offset)
        }
    }
    // returns pc
    fun popFrame(): Int {
        while (stack.lastIndex > bp + 1) {
            stack.removeLast() // remove args, caller
        }
        val pc = stack.removeLast() // remove pc
        bp = stack.removeLast() // remove bp
        return pc
    }

    private val heap: MutableList<NodeValue> = mutableListOf()
    private val builtins: MutableList<NodeValue> = mutableListOf()
    fun addBuiltins(builtins: List<NodeValue>) {
        this.builtins.addAll(builtins)
    }
    init {
        addBuiltins(Constants.builtinSymbols.toList().map { it.second })
        addBuiltins(Constants.builtinProcedures.toList().map { it.second })
    }
    private val statics: MutableList<NodeValue> = mutableListOf()
    fun addStatics(statics: List<NodeValue>) {
        this.statics.addAll(statics)
    }

    operator fun get(pointer: Pointer): NodeValue {
        return when (pointer.loc) {
            Location.STACK -> heap[stack[pointer.offset + bp]]
            Location.HEAP -> heap[pointer.offset]
            Location.BUILTIN -> builtins[pointer.offset]
            Location.STATIC -> statics[pointer.offset]
        }
    }
    operator fun set(pointer: Pointer, value: NodeValue) {
        when (pointer.loc) {
            Location.STACK -> heap[stack[pointer.offset + bp]] = value
            Location.HEAP -> heap[pointer.offset] = value
            else -> throw InterpretationRuntimeException("Cannot set value in readonly memory")
        }
    }
    fun mov(dst: Pointer, src: Pointer) {
        set(dst, get(src))
    }
    fun movi(dst: Pointer, value: NodeValue) {
        set(dst, value)
    }

    fun allocate(value: NodeValue): Pointer {
        heap.add(value)
        return Pointer(Location.HEAP, heap.size - 1)
    }
    fun createReference(value: NodeValue): Pointer {
        return when (value.passingScheme) {
            PassingScheme.BY_VALUE -> allocate(value)
            PassingScheme.BY_REFERENCE -> (value as PassByReferenceNodeValue).let {
                it.address ?: it.apply { solidify(this@Memory) }.address!!
            }
        }
    }
}