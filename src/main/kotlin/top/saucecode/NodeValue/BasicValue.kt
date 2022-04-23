package top.saucecode.NodeValue

import kotlinx.serialization.Serializable

@Serializable
data class StringValue(val value: String) : NodeValue(), Iterable<StringValue> {
    override fun toString() = value
    override fun toBoolean(): Boolean = value.isNotEmpty()
    override fun iterator(): Iterator<StringValue> {
        return object : Iterator<StringValue> {
            var index = 0
            override fun hasNext(): Boolean = index < value.length
            override fun next(): StringValue {
                val result = StringValue(value.substring(index, index + 1))
                index++
                return result
            }
        }
    }
}

fun String.toNodeValue() = StringValue(this)

@Serializable
data class ListValue(val value: MutableList<NodeValue>) : NodeValue(), Iterable<NodeValue> {
    override fun toString() = "[${value.joinToString(", ")}]"
    override fun toBoolean(): Boolean = value.isNotEmpty()
    val size: Int get() = value.size
    operator fun get(index: Int): NodeValue = value[index]
    operator fun set(index: Int, value: NodeValue) {
        this.value[index] = value
    }

    override fun iterator(): Iterator<NodeValue> {
        return value.iterator()
    }
}

fun List<NodeValue>.toNodeValue() = ListValue(if (this is MutableList) this else this.toMutableList())

@Serializable
data class NumberValue(val value: Long) : NodeValue() {
    override fun toString() = value.toString()
    override fun toBoolean(): Boolean = value != 0L
}

fun Int.toNodeValue(): NodeValue = NumberValue(this.toLong())
fun Long.toNodeValue(): NodeValue = NumberValue(this)

@Serializable
data class BooleanValue(val value: Boolean) : NodeValue() {
    override fun toString() = value.toString()
    override fun toBoolean(): Boolean = value
}

fun Boolean.toNodeValue() = BooleanValue(this)
fun Boolean.toLong() = if (this) 1L else 0L

@Serializable
sealed class SubscriptValue : NodeValue()

@Serializable
data class NumberSubscriptValue(val begin: Int, val extended: Boolean, val end: Int? = null) : SubscriptValue() {
    override fun toString() = if (extended) "$begin:$end" else "$begin"
    override fun toBoolean(): Boolean = true
}

@Serializable
data class KeySubscriptValue(val key: String) : SubscriptValue() {
    override fun toString() = key
    override fun toBoolean(): Boolean = true
}

@Serializable
data class ObjectValue(private val attributes: MutableMap<String, NodeValue> = mutableMapOf()) : NodeValue() {
    override fun toBoolean(): Boolean = attributes.isNotEmpty()
    operator fun get(key: String): NodeValue? = attributes[key]
    operator fun set(key: String, value: NodeValue) {
        attributes[key] = value
    }

    override fun toString(): String {
        return "{" + attributes.map { "${it.key}: ${it.value}" }.joinToString(", ") + "}"
    }

    fun bindSelf(): ObjectValue {
        for ((_, value) in attributes) {
            if (value is ProcedureValue) {
                value.bind(this)
            }
        }
        return this
    }
}

fun MutableMap<String, NodeValue>.toNodeValue() = ObjectValue(this)

@Serializable
object NullValue : NodeValue() {
    override fun toString() = "null"
    override fun toBoolean(): Boolean = false
}