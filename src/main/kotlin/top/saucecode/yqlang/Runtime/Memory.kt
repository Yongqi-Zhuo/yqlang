package top.saucecode.yqlang.Runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.w3c.dom.Node
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.SymbolTable

// Points to location on heap, static
typealias Pointer = Int
fun Pointer.load(memory: Memory): NodeValue = memory[this]
const val REGION_SHIFT = 28
const val REGION_MASK = 0xFFFFFFF
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
    var text: List<ByteCode>? = null
    var labels: List<Int>? = null
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
        const val callerOffset = 2
        const val argsOffset = 3
        const val paramsAndCaptureBase = 3 + 1
    }
    val argsPointer: Pointer get() = stack[bp + argsOffset]
    val caller: NodeValue get() = get(stack[bp + callerOffset])
    val args: ListValue get() = get(stack[bp + argsOffset]).asList()!!
    fun arg(index: Int): NodeValue = get(args[index])
    fun argOrNull(index: Int): NodeValue? {
        val a = args
        return if (index < a.size) get(a[index]) else null
    }
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

    operator fun get(pointer: Pointer): NodeValue {
        val region = pointer.region()
        val offset = pointer.offset()
        return when (region) {
            REGION_ID_HEAP -> heap[offset]
            REGION_ID_STATIC -> statics[offset]
            else -> throw YqlangRuntimeException("Invalid pointer: $pointer")
        }
    }
    operator fun set(pointer: Pointer, value: NodeValue) {
        val region = pointer.region()
        val offset = pointer.offset()
        when (region) {
            REGION_ID_HEAP -> heap[offset] = value
            REGION_ID_STATIC -> statics[offset] = value
            else -> throw YqlangRuntimeException("Invalid pointer: $pointer")
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

    fun assemblyText(symbolTable: SymbolTable): String {
        var buffer = "text:\n"
        val lineToLabel = MutableList(text!!.size + 1) { mutableListOf<Int>() }
        labels!!.forEachIndexed { index, label ->
            lineToLabel[label].add(index)
        }
        val captions = lineToLabel.map { l -> if (l.size > 0) l.joinToString("\n") { "label$it:" } + "\n" else "" }
        text!!.mapIndexed { line, byteCode ->
            buffer += captions[line] + "$line\t$byteCode\n"
        }
        if (captions.last().isNotEmpty()) {
            buffer += captions.last()
        }
        buffer += "static:\n"
        statics.forEachIndexed { index, value ->
            val staticPointer = StaticPointer(index)
            val caption = symbolTable.filter { it.value == staticPointer }.map { it.key }.firstOrNull()?.let { "\t$it" } ?: ""
            buffer += "${staticPointer.toString(16)}\t$value${caption}\n"
        }
        return buffer
    }

}