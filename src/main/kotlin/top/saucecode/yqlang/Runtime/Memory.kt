package top.saucecode.yqlang.Runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.saucecode.yqlang.Constants
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.Node.Node
import top.saucecode.yqlang.NodeValue.*

// Points to location on heap, static
typealias Pointer = Int
const val REGION_SHIFT = 28
const val REGION_MASK = 0x3FFFFFFF
const val REGION_ID_HEAP = 0
const val REGION_ID_STATIC = 1
fun Pointer.region() = this shr REGION_SHIFT
fun Pointer.offset() = this and REGION_MASK
fun HeapPointer(offset: Int) = offset or (REGION_ID_HEAP shl REGION_SHIFT)
fun StaticPointer(offset: Int) = offset or (REGION_ID_STATIC shl REGION_SHIFT)
typealias CollectionPoolPointer = Int

// TODO: GC
@Serializable
class Memory {
    @Transient private val stack: MutableList<Pointer> = mutableListOf()
    @Transient private var bp: Int = 0
    fun pushFrame(retAddr: Int, caller: Pointer, args: Pointer, captures: Pointer) {
        stack.add(bp)
        bp = stack.lastIndex // lastBp = 0(bp)
        stack.add(retAddr) // retAddr = 1(bp)
        stack.add(caller) // caller = 2(bp)
        // components of args can be accessed indirectly
        stack.add(args) // args = 3(bp)
        stack.add(captures) // captures = 4(bp) // expanding captures is callee job
    }
    companion object {
        val callerOffset = 2
        val argsOffset = 3
        val paramsAndCaptureBase = 3 + 1
    }
    val caller: Pointer get() = stack[bp + 2]
    val args: Pointer get() = stack[bp + 3]
    val captures: Pointer get() = stack[bp + 4]
    // returns retAddr
    fun popFrame(): Int {
        while (stack.lastIndex > bp + 1) {
            stack.removeLast() // remove args, caller
        }
        val pc = stack.removeLast() // remove pc
        bp = stack.removeLast() // remove bp
        return pc
    }
    fun push(ptr: Int) {
        stack.add(ptr)
    }
    fun pop(): Int {
        return stack.removeLast()
    }
    fun getLocal(index: Int): Pointer {
        return stack[bp + index]
    }

    private val heap: MutableList<NodeValue> = mutableListOf()
    private val collectionPool: MutableList<CollectionValue> = mutableListOf()
    // TODO: implement builtins by external calls
//    @Transient private val builtins: MutableList<NodeValue> = mutableListOf()
//    private fun addBuiltins(builtins: List<NodeValue>) {
//        this.builtins.addAll(builtins)
//    }
//    init {
//        addBuiltins(Constants.builtinSymbols.toList().map { it.second })
//        addBuiltins(Constants.builtinProcedures.toList().map { it.second })
//    }
    private val statics: MutableList<NodeValue> = mutableListOf()
    fun addStatics(statics: List<NodeValue>) {
        this.statics.addAll(statics)
    }
    fun addStaticValue(value: NodeValue): Pointer {
        statics.add(value)
        return StaticPointer(statics.lastIndex)
    }
    fun addStaticString(value: String): Pointer {
        statics.add(StringValue(value, this).reference)
        return StaticPointer(statics.lastIndex)
    }
//    var text: Node? = null

    operator fun get(pointer: Pointer): NodeValue {
        val region = pointer.region()
        val offset = pointer.offset()
        return when (region) {
            REGION_ID_HEAP -> heap[offset]
            REGION_ID_STATIC -> statics[offset]
            else -> throw InterpretationRuntimeException("Invalid pointer: $pointer")
        }
    }
    operator fun set(pointer: Pointer, value: NodeValue) {
        val region = pointer.region()
        val offset = pointer.offset()
        when (region) {
            REGION_ID_HEAP -> heap[offset] = value
            REGION_ID_STATIC -> statics[offset] = value
            else -> throw InterpretationRuntimeException("Invalid pointer: $pointer")
        }
    }
    fun getFromPool(pointer: CollectionPoolPointer): CollectionValue {
        return collectionPool[pointer]
    }
    fun putToPool(value: CollectionValue): CollectionPoolPointer {
        collectionPool.add(value)
        return collectionPool.lastIndex
    }

    fun allocate(value: NodeValue): Pointer {
        heap.add(value)
        return HeapPointer(heap.lastIndex)
    }
    fun copy(pointer: Pointer): Pointer {
        return allocate(get(pointer))
    }
    fun copyTo(src: Pointer, dst: Pointer) {
        set(dst, get(src))
    }
    // no need to createReference, just copy, because references are passed by value
//    fun createReference(value: NodeValue): Pointer {
//        return when (value.passingScheme) {
//            PassingScheme.BY_VALUE -> allocate(value)
//            PassingScheme.BY_REFERENCE -> (value as CollectionValue).let {
//                it.address ?: it.apply { solidify(this@Memory) }.address!!
//            }
//        }
//    }
//    fun makeCopy(value: Pointer): Pointer {
//        return createReference(get(value))
//    }
}