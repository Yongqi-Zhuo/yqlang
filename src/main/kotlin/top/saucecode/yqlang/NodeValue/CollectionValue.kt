package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer

@Serializable
data class StringValue(var value: String) : CollectionValue() {
    constructor(value: String, memory: Memory): this(value) {
        solidify(memory)
    }
    override val debugStr: String
        get() = "\"$value\""
    override val printStr: String
        get() = value
    override fun isNotEmpty(): Boolean = value.isNotEmpty()
    override operator fun contains(that: NodeValue): Boolean = value.contains(that.printStr)
    override fun compareTo(other: CollectionValue): Int {
        if (other is StringValue) {
            return value.compareTo(other.value)
        } else {
            throw OperationRuntimeException("Invalid operation: $this <=> $other")
        }
    }
    override fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        if (memory == null) throw AccessingUnsolidifiedValueException(this)
        return if (that is ReferenceValue && that.value is ListValue) { // format to string
            that.exchangeablePlus(reference, !inverse)
        } else {
            val fmt = that.printStr
            if (!inverse) {
                StringValue(value + fmt, memory!!).reference
            } else {
                StringValue(fmt + value, memory!!).reference
            }
        }
    }
    override operator fun times(that: NodeValue): NodeValue {
        return when (that) {
            is ArithmeticValue -> StringValue(
                value.repeat(that.coercedTo(IntegerValue::class).value.toInt()),
                memory!!
            ).reference
            else -> super.times(that) // throws exception
        }
    }
    override fun addAssign(that: NodeValue): NodeValue {
        value += that.printStr
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
    override fun iterator(): Iterator<Pointer> {
        return value.map { memory!!.allocate(StringValue(it.toString(), memory!!).reference) }.iterator()
    }
}

// fun String.toNodeValue() = StringValue(this)

@Serializable
data class ListValue(val value: MutableList<Pointer>) : CollectionValue() {
    constructor(value: MutableList<Pointer>, memory: Memory): this(value) {
        solidify(memory)
    }
    override val debugStr: String
        get() = "[${value.joinToString(", ") { memory?.get(it)?.debugStr ?: it.toString() }}]"
    override val printStr: String
        get() = debugStr
    override fun isNotEmpty(): Boolean = value.isNotEmpty()
    operator fun get(index: Int): Pointer = value[index]
    operator fun set(index: Int, value: Pointer) {
        this.value[index] = value
    }
    override fun contains(that: NodeValue): Boolean {
        for (ptr in value) {
            if (memory!![ptr] == that) return true
        }
        return false
    }
    override fun compareTo(other: CollectionValue): Int {
        if (other is ListValue) {
            val size = minOf(value.size, other.value.size)
            for (i in 0 until size) {
                val cmp = memory!![value[i]].compareTo(memory!![other.value[i]])
                if (cmp != 0) return cmp
            }
            return value.size - other.value.size
        } else {
            throw OperationRuntimeException("Invalid operation: $this <=> $other")
        }
    }
    override fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        if (memory == null) throw AccessingUnsolidifiedValueException(this)
        val thisListCopy = value.mapTo(mutableListOf()) { memory!!.copy(it) }
        return if (that is ReferenceValue && that.value is ListValue) {
            val thatListCopy = (that.value as ListValue).value.map { memory!!.copy(it) }
            ListValue(thisListCopy.apply { addAll(thatListCopy) }, memory!!).reference
        } else {
            val ref = memory!!.allocate(that)
            if (!inverse) {
                ListValue(thisListCopy.apply { add(ref) }, memory!!).reference
            } else {
                ListValue(thisListCopy.apply { add(0, ref) }, memory!!).reference
            }
        }
    }
    override operator fun times(that: NodeValue): NodeValue {
        return when (that) {
            is ArithmeticValue -> {
                val cnt = that.coercedTo(IntegerValue::class).value.toInt()
                val thisSize = value.size
                ListValue(MutableList(cnt * thisSize) { index ->
                    memory!!.copy(value[index % thisSize]) }, memory!!).reference
            }
            else -> super.times(that) // throws
        }
    }
    override fun addAssign(that: NodeValue): NodeValue {
        if (that is ReferenceValue && that.value is ListValue) {
            val thatListCopy = (that.value as ListValue).value.map { memory!!.copy(it) }
            value.addAll(thatListCopy)
        } else {
            value.add(memory!!.allocate(that))
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
    override fun iterator(): Iterator<Pointer> {
        return value.iterator()
    }
}

// fun List<NodeValue>.toNodeValue() = ListValue(if (this is MutableList) this else this.toMutableList())

@Serializable
data class ObjectValue(private val attributes: MutableMap<String, Pointer> = mutableMapOf()) : CollectionValue() {
    constructor(value: MutableMap<String, Pointer>, memory: Memory): this(value) {
        solidify(memory)
    }
    override val debugStr: String
        get() = "{${attributes.map { "\"${it.key}\": ${memory?.get(it.value)?.debugStr ?: it.value}" }.joinToString(", ")}}"
    override val printStr: String
        get() = debugStr
    override fun isNotEmpty(): Boolean = attributes.isNotEmpty()
    operator fun get(key: String): Pointer? = attributes[key]
    operator fun set(key: String, value: Pointer) {
        attributes[key] = value
    }
    override fun contains(that: NodeValue): Boolean {
        for (ptr in attributes.values) {
            if (memory!![ptr] == that) return true
        }
        return false
    }
    override fun iterator(): Iterator<Pointer> {
        return attributes.map {
            memory!!.allocate(ListValue(mutableListOf(
                memory!!.allocate(StringValue(it.key, memory!!).reference), it.value
            ), memory!!).reference)
        }.iterator()
    }
}

// fun MutableMap<String, NodeValue>.toNodeValue() = ObjectValue(this)