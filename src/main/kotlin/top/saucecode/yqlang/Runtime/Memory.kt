package top.saucecode.yqlang.Runtime

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.NodeValue.CollectionValue
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.StringValue
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
    companion object {
        const val callerOffset = 2
        const val argsOffset = 3
        const val paramsAndCaptureBase = 3 + 1
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
            buffer += captions[line] + "${line.toString().padEnd(5)}\t$byteCode\n"
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