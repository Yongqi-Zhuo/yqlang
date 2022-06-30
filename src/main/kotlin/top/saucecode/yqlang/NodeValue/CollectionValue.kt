package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.Constants
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer

@Serializable
data class StringValue(var value: String) : CollectionValue() {
    constructor(value: String, memory: Memory): this(value) {
        solidify(memory)
    }
    override fun debugStr(level: Int): String= "\"$value\""
    override fun printStr(level: Int): String = value
    override fun toString(): String = debugStr(0)
    override fun gcTransformPointeePrimitives(transform: (Pointer) -> Pointer) {
        return
    }
    override fun isNotEmpty(): Boolean = value.isNotEmpty()
    override operator fun contains(that: NodeValue): Boolean = value.contains(that.printStr(0))
    override fun compareTo(other: CollectionValue): Int {
        if (other is StringValue) {
            return value.compareTo(other.value)
        } else {
            throw OperationRuntimeException("Invalid operation: $this <=> $other")
        }
    }
    override fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        return if (that is ReferenceValue && that.value is ListValue) { // format to string
            that.exchangeablePlus(reference, !inverse)
        } else {
            val fmt = that.printStr(0)
            if (!inverse) {
                StringValue(value + fmt, memory).reference
            } else {
                StringValue(fmt + value, memory).reference
            }
        }
    }
    override operator fun times(that: NodeValue): NodeValue {
        return when (that) {
            is ArithmeticValue -> StringValue(
                value.repeat(that.coercedTo(IntegerValue::class).value.toInt()),
                memory
            ).reference
            else -> super.times(that) // throws exception
        }
    }
    override fun addAssign(that: NodeValue): NodeValue {
        value += that.printStr(0)
        return reference
    }

    override fun mulAssign(that: NodeValue): NodeValue {
        return when (that) {
            is ArithmeticValue -> {
                value = value.repeat(that.coercedTo(IntegerValue::class).value.toInt())
                reference
            }
            else -> super.times(that) // throws exception
        }
    }
    override fun iterator(): Iterator<NodeValue> {
        return value.map { StringValue(it.toString(), memory).reference }.iterator()
    }
}

fun String.toStringValueReference(memory: Memory) = StringValue(this, memory).reference

@Serializable
class ListValue(val value: MutableList<Pointer>) : CollectionValue() {
    constructor(value: MutableList<Pointer>, memory: Memory): this(value) {
        solidify(memory)
    }
    override fun debugStr(level: Int): String {
        if (level > Constants.printLevelMax) return "[...]"
        return "[${value.joinToString(", ") { memory[it].debugStr(level + 1) }}]"
    }
    override fun printStr(level: Int): String = debugStr(level)
    override fun toString(): String = debugStr(0)
    override fun gcTransformPointeePrimitives(transform: (Pointer) -> Pointer) {
        value.indices.forEach { i ->
            value[i] = transform(value[i])
        }
    }
    override fun isNotEmpty(): Boolean = value.isNotEmpty()
    operator fun get(index: Int): Pointer = value[index]
    operator fun set(index: Int, value: Pointer) {
        this.value[index] = value
    }
    val size: Int get() = value.size
    override fun contains(that: NodeValue): Boolean {
        for (ptr in value) {
            if (memory[ptr] == that) return true
        }
        return false
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ListValue) return false
        if (value.size != other.value.size) return false
        if (value.zip(other.value).any { memory[it.first] != memory[it.second] }) return false
        return true
    }
    override fun hashCode(): Int {
        return value.map { memory[it] }.hashCode()
    }
    override fun compareTo(other: CollectionValue): Int {
        if (other is ListValue) {
            val size = minOf(value.size, other.value.size)
            for (i in 0 until size) {
                val cmp = memory[value[i]].compareTo(memory[other.value[i]])
                if (cmp != 0) return cmp
            }
            return value.size - other.value.size
        } else {
            throw OperationRuntimeException("Invalid operation: $this <=> $other")
        }
    }
    override fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        val thisListCopy = value.mapTo(mutableListOf()) { memory.copy(it) }
        return if (that is ReferenceValue && that.value is ListValue) {
            val thatListCopy = (that.value as ListValue).value.map { memory.copy(it) }
            ListValue(thisListCopy.apply { addAll(thatListCopy) }, memory).reference
        } else {
            val ref = memory.allocate(that)
            if (!inverse) {
                ListValue(thisListCopy.apply { add(ref) }, memory).reference
            } else {
                ListValue(thisListCopy.apply { add(0, ref) }, memory).reference
            }
        }
    }
    override operator fun times(that: NodeValue): NodeValue {
        return when (that) {
            is ArithmeticValue -> {
                val cnt = that.coercedTo(IntegerValue::class).value.toInt()
                val thisSize = value.size
                ListValue(MutableList(cnt * thisSize) { index ->
                    memory.copy(value[index % thisSize]) }, memory).reference
            }
            else -> super.times(that) // throws
        }
    }
    override fun addAssign(that: NodeValue): NodeValue {
        if (that is ReferenceValue && that.value is ListValue) {
            val thatListCopy = (that.value as ListValue).value.map { memory.copy(it) }
            value.addAll(thatListCopy)
        } else {
            value.add(memory.allocate(that))
        }
        return reference
    }

    override fun mulAssign(that: NodeValue): NodeValue {
        return when (that) {
            is ArithmeticValue -> {
                val cnt = that.coercedTo(IntegerValue::class).value.toInt() - 1
                val thisSize = value.size
                val iterations = cnt * thisSize
                repeat(iterations) {
                    value.add(value[it % thisSize])
                }
                reference
            }
            else -> super.times(that) // throws
        }
    }
    override fun iterator(): Iterator<NodeValue> {
        return value.map { memory[it] }.iterator()
    }
}

fun List<NodeValue>.toListValue(memory: Memory) = ListValue(this.mapTo(mutableListOf()) { memory.allocate(it) }, memory)
fun List<NodeValue>.toListValueReference(memory: Memory) = this.toListValue(memory).reference
fun Iterable<String>.toStringListReference(memory: Memory) = this.map { it.toStringValueReference(memory) }.toListValueReference(memory)
fun Iterable<Int>.toIntegerListReference(memory: Memory) = this.map { it.toIntegerValue() }.toListValueReference(memory)

@Serializable
class ObjectValue(private val attributes: MutableMap<String, Pointer> = mutableMapOf()) : CollectionValue() {
    constructor(value: MutableMap<String, Pointer>, memory: Memory): this(value) {
        solidify(memory)
    }
    override fun debugStr(level: Int): String {
        if (level > Constants.printLevelMax) return "{...}"
        return "{${attributes.map { "\"${it.key}\": ${memory[it.value].debugStr(level)}" }.joinToString(", ")}}"
    }
    override fun printStr(level: Int): String = debugStr(level)
    override fun toString(): String = debugStr(0)
    override fun gcTransformPointeePrimitives(transform: (Pointer) -> Pointer) {
        attributes.keys.forEach { key ->
            attributes[key] = transform(attributes[key]!!)
        }
    }
    override fun isNotEmpty(): Boolean = attributes.isNotEmpty()
    operator fun get(key: String): Pointer? = attributes[key]
    operator fun set(key: String, value: Pointer) {
        attributes[key] = value
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObjectValue) return false
        if (attributes.size != other.attributes.size) return false
        if (attributes.keys != other.attributes.keys) return false
        for ((key, value) in attributes) {
            if (memory[value] != memory[other.attributes[key]!!]) return false
        }
        return true
    }
    override fun hashCode(): Int {
        return attributes.map { it.key to memory[it.value] }.hashCode()
    }
    override fun contains(that: NodeValue): Boolean {
        for (ptr in attributes.values) {
            if (memory[ptr] == that) return true
        }
        return false
    }
    override fun iterator(): Iterator<NodeValue> {
        return attributes.map {
            ListValue(mutableListOf(
                memory.allocate(StringValue(it.key, memory).reference), it.value
            ), memory).reference
        }.iterator()
    }
}

// fun MutableMap<String, NodeValue>.toNodeValue() = ObjectValue(this)