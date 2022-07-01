package top.saucecode.yqlang.Runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.SymbolTable
import top.saucecode.yqlang.YqlangException
import java.util.LinkedList

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

@Serializable
class Memory {
    @Transient
    lateinit var text: List<ByteCode>
    @Transient
    lateinit var labels: List<Int>
    lateinit var symbolTable: SymbolTable
    private val heap: MutableList<NodeValue> = mutableListOf()
    private val collectionPool: MutableList<CollectionValue> = mutableListOf()
    private val statics: MutableList<NodeValue> = mutableListOf()
    fun addStatics(statics: List<NodeValue>) {
        this.statics.addAll(statics)
    }

    fun addStatic(static: NodeValue): Pointer {
        statics.add(static)
        return StaticPointer(statics.lastIndex)
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

    fun assemblyText(): String {
        var buffer = "text:\n"
        val lineToLabel = MutableList(text.size + 1) { mutableListOf<Int>() }
        labels.forEachIndexed { index, label ->
            lineToLabel[label].add(index)
        }
        val captions = lineToLabel.map { l -> if (l.size > 0) l.joinToString("\n") { "label$it:" } + "\n" else "" }
        text.mapIndexed { line, byteCode ->
            buffer += captions[line] + "${line.toString().padEnd(5)}\t$byteCode\n"
        }
        if (captions.last().isNotEmpty()) {
            buffer += captions.last()
        }
        buffer += memoryDump()
        return buffer
    }

    fun memoryDump(): String {
        var buffer = "heap:\n"
        heap.forEachIndexed { index, value ->
            buffer += "${index.toString(16).padEnd(5)}\t$value\n"
        }
        buffer += "collectionPool:\n"
        collectionPool.forEachIndexed { index, value ->
            buffer += "${index.toString(16).padEnd(5)}\t$value\n"
        }
        buffer += "static:\n"
        statics.forEachIndexed { index, value ->
            val staticPointer = StaticPointer(index)
            val caption =
                symbolTable.filter { it.value == staticPointer }.map { it.key }.firstOrNull()?.let { "\t$it" } ?: ""
            buffer += "${staticPointer.toString(16)}\t$value${caption}\n"
        }
        return buffer
    }

    val heapSize: Int get() = heap.size
    fun gc() {
        val heapMap = mutableMapOf<Pointer, Pointer>() // old -> new
        val collectionMap = mutableMapOf<CollectionPoolPointer, CollectionPoolPointer>() // old -> new
        val newHeap = mutableListOf<NodeValue>()
        val newCollectionPool = mutableListOf<CollectionValue>()
        val heapGCQueue = LinkedList<Pointer>()
        val collectionGCQueue = LinkedList<CollectionPoolPointer>()

        // bfs in bipartite graph
        fun processPrimitive(pointer: Pointer, newLocation: Pointer) {
            val primitive = get(pointer)
            val pointee = if (primitive is PrimitivePointingObject) primitive.pointeeCollection() else return
            if (pointee < 0) return
            val potentialNewPointeeLocation = collectionMap[pointee]
            val actualNewPointeeLocation = if (potentialNewPointeeLocation == null) {
                // now move it to new pool
                val collection = getFromPool(pointee)
                newCollectionPool.add(collection)
                val newPointeeLocation = newCollectionPool.lastIndex
                collectionMap[pointee] = newPointeeLocation
                collection.moveThisToNewLocation(newPointeeLocation)
                collectionGCQueue.add(pointee)
                newPointeeLocation
            } else {
                potentialNewPointeeLocation
            }
            // change myself
            val newPointerValue = (primitive as PrimitivePointingObject).repointedTo(actualNewPointeeLocation)
            if (newLocation.region() == REGION_ID_HEAP) {
                newHeap[newLocation] = newPointerValue
            } else if (newLocation.region() == REGION_ID_STATIC) {
                statics[newLocation.offset()] = newPointerValue
            } else {
                throw YqlangRuntimeException("Invalid pointer: $newLocation")
            }
            return
        }

        fun processCollection(pointer: CollectionPoolPointer, newLocation: CollectionPoolPointer) {
            val collection = getFromPool(pointer)
            collection.transformPointeePrimitives { pointee ->
                // have to move!
                if (pointee.region() != REGION_ID_HEAP) return@transformPointeePrimitives pointee
                val potentialNewPointeeLocation = heapMap[pointee]
                if (potentialNewPointeeLocation != null) {
                    // change myself
                    return@transformPointeePrimitives potentialNewPointeeLocation
                }
                // now move it to new heap
                newHeap.add(get(pointee))
                val actualNewPointeeLocation = newHeap.lastIndex
                heapMap[pointee] = actualNewPointeeLocation
                heapGCQueue.add(pointee)
                return@transformPointeePrimitives actualNewPointeeLocation
            }
        }
        statics.indices.forEach {
            val ptr = StaticPointer(it)
            heapGCQueue.add(ptr)
            heapMap[ptr] = ptr
        }
        var age = 0
        while (age < 2) {
            age += 1
            while (heapGCQueue.isNotEmpty()) {
                val ptr = heapGCQueue.pop()
                processPrimitive(ptr, heapMap[ptr]!!)
                age = 0
            }
            age += 1
            while (collectionGCQueue.isNotEmpty()) {
                val ptr = collectionGCQueue.pop()
                processCollection(ptr, collectionMap[ptr]!!)
                age = 0
            }
        }
        heap.clear()
        heap.addAll(newHeap)
        collectionPool.clear()
        collectionPool.addAll(newCollectionPool)
    }

    fun serialize(): String {
        return Json.encodeToString(serializer(), this)
    }

    companion object {
        fun deserialize(text: String): Memory {
            return Json.decodeFromString(serializer(), text)
        }
    }

    fun updateFrom(old: Memory) {
        val oldSymbols = old.symbolTable
        val heapSize = heap.size
        val poolSize = collectionPool.size
        val migrationMap = mutableMapOf<Pointer, Pointer>() // static address map
        fun migrateStatic(static: Pointer): Pointer {
            assert(static.region() == REGION_ID_STATIC)
            return migrationMap[static] ?: run {
                val staticName = oldSymbols.filter { it.value == static }.map { it.key }.firstOrNull()
                val newPointer = old[static].apply {
                    (this as? MemoryDependent)?.bindMemory(this@Memory)
                }.let {
                    (it as? PrimitivePointingObject)?.repointedTo(it.pointeeCollection() + poolSize) ?: it
                }
                val newLoc = if (staticName?.let { symbolTable.containsKey(it) } == true) {
                    // substitute
                    val newLocation = symbolTable[staticName]!!
                    this[newLocation] = newPointer
                    newLocation
                } else {
                    // append
                    val newLocation = addStatic(newPointer)
                    staticName?.let { symbolTable[it] = newLocation }
                    newLocation
                }
                migrationMap[static] = newLoc
                newLoc
            }
        }
        oldSymbols.values.forEach { migrateStatic(it) }
        heap.addAll(old.heap.map {
            (it as? MemoryDependent)?.bindMemory(this)
            if (it is PrimitivePointingObject) {
                it.repointedTo(it.pointeeCollection() + poolSize)
            } else it
        })
        old.collectionPool.forEachIndexed { index, collection ->
            collection.bindMemory(this)
            collection.moveThisToNewLocation(index + poolSize)
            collection.transformPointeePrimitives { pointee ->
                when (pointee.region()) {
                    REGION_ID_HEAP -> HeapPointer(pointee + heapSize)
                    REGION_ID_STATIC -> migrateStatic(pointee)
                    else -> throw YqlangException("this should not happen")
                }
            }
        }
        collectionPool.addAll(old.collectionPool)
    }
}