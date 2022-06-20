package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
sealed class ArithmeticValue : NodeValue() {
    // the argument has the same type as the caller
    protected abstract fun plusImpl(that: ArithmeticValue): ArithmeticValue
    override fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        return when (that) {
            is ArithmeticValue -> {
                val level = getHigherLevel(this, that)
                coercedTo(level).plusImpl(that.coercedTo(level))
            }
            else -> that.exchangeablePlus(this, !inverse)
        }
    }
    // the argument has the same type as the caller
    protected abstract fun minusImpl(that: ArithmeticValue): ArithmeticValue
    override operator fun minus(that: NodeValue): NodeValue {
        return if (that is ArithmeticValue) {
            val level = getHigherLevel(this, that)
            coercedTo(level).minusImpl(that.coercedTo(level))
        } else {
            super.minus(that) // throws exception
        }
    }
    // the argument has the same type as the caller
    protected abstract fun timesImpl(that: ArithmeticValue): ArithmeticValue
    override operator fun times(that: NodeValue): NodeValue {
        return if (that is ArithmeticValue) {
            val level = getHigherLevel(this, that)
            coercedTo(level).timesImpl(that.coercedTo(level))
        } else {
            that.times(this) // for lists and strings
        }
    }
    // the argument has the same type as the caller
    protected abstract fun divImpl(that: ArithmeticValue): ArithmeticValue
    override operator fun div(that: NodeValue): NodeValue {
        return if (that is ArithmeticValue) {
            val level = getHigherLevel(this, that)
            coercedTo(level).divImpl(that.coercedTo(level))
        } else {
            that.div(this) // for lists and strings
        }
    }
    // the argument has the same type as the caller
    protected abstract fun remImpl(that: ArithmeticValue): ArithmeticValue
    override operator fun rem(that: NodeValue): NodeValue {
        return if (that is ArithmeticValue) {
            val level = getHigherLevel(this, that)
            coercedTo(level).remImpl(that.coercedTo(level))
        } else {
            that.rem(this) // for lists and strings
        }
    }
    fun<T: ArithmeticValue> coercedTo(level: KClass<T>): T {
        return when (level) {
            BooleanValue::class -> BooleanValue(this) as T
            NumberValue::class -> NumberValue(this) as T
            else -> throw IllegalArgumentException("Cannot coerce $this to $level")
        }
    }
    companion object {
        private val coercionLevels = listOf(BooleanValue::class, NumberValue::class)
        private fun getHigherLevel(value1: ArithmeticValue, value2: ArithmeticValue): KClass<out ArithmeticValue> {
            val level1 = coercionLevels.indexOf(value1::class)
            val level2 = coercionLevels.indexOf(value2::class)
            return if (level1 > level2) value1::class else value2::class
        }
    }
}

@Serializable
data class StringValue(val value: String) : NodeValue(), Iterable<StringValue> {
    override val debugStr: String
        get() = "\"$value\""
    override val printStr: String
        get() = value
    override fun toString(): String = debugStr
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
    override fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        return if (that !is ListValue) { // format to string
            val fmt = that.printStr
            if (!inverse) {
                StringValue(value + fmt)
            } else {
                StringValue(fmt + value)
            }
        } else {
            that.exchangeablePlus(this, !inverse)
        }
    }
    override operator fun times(that: NodeValue): NodeValue {
        return when (that) {
            is ArithmeticValue -> value.repeat(that.coercedTo(NumberValue::class).value.toInt()).toNodeValue()
            else -> super.times(that) // throws exception
        }
    }
}

fun String.toNodeValue() = StringValue(this)

@Serializable
data class ListValue(val value: MutableList<NodeValue>) : NodeValue(), Iterable<NodeValue> {
    override val debugStr: String
        get() = "[${value.joinToString(", ") { it.debugStr }}]"
    override val printStr: String
        get() = debugStr
    override fun toString(): String = debugStr
    override fun toBoolean(): Boolean = value.isNotEmpty()
    val size: Int get() = value.size
    operator fun get(index: Int): NodeValue = value[index]
    operator fun set(index: Int, value: NodeValue) {
        this.value[index] = value
    }
    override fun iterator(): Iterator<NodeValue> {
        return value.iterator()
    }

    override fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        return if (that !is ListValue) {
            if (!inverse) {
                ListValue(value.toMutableList().apply { add(that) })
            } else {
                ListValue(mutableListOf(that).apply { addAll(value) })
            }
        } else {
            value.toMutableList().apply { addAll(that.value) }.toNodeValue()
        }
    }
    override operator fun times(that: NodeValue): NodeValue {
        return when (that) {
            is ArithmeticValue -> {
                val cnt = that.coercedTo(NumberValue::class).value.toInt()
                List(cnt * size) { index -> value[index % size] }.toNodeValue()
            }
            else -> super.times(that) // throws
        }
    }
}

fun List<NodeValue>.toNodeValue() = ListValue(if (this is MutableList) this else this.toMutableList())

@Serializable
data class NumberValue(val value: Long) : ArithmeticValue() {
    override val debugStr: String
        get() = value.toString()
    override val printStr: String
        get() = debugStr
    override fun toString(): String = debugStr
    override fun toBoolean(): Boolean = value != 0L
    constructor(what: ArithmeticValue): this(when(what) {
        is NumberValue -> what.value
        is BooleanValue -> if (what.value) 1L else 0L
    })
    override fun plusImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(value + (that as NumberValue).value)
    }
    override fun minusImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(value - (that as NumberValue).value)
    }
    override fun timesImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(value * (that as NumberValue).value)
    }

    override fun divImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(value / (that as NumberValue).value)
    }
    override fun remImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(value % (that as NumberValue).value)
    }
    override operator fun unaryMinus(): ArithmeticValue {
        return NumberValue(-value)
    }
}

fun Int.toNodeValue(): NodeValue = NumberValue(this.toLong())
fun Long.toNodeValue(): NodeValue = NumberValue(this)

@Serializable
data class BooleanValue(val value: Boolean) : ArithmeticValue() {
    override val debugStr: String
        get() = value.toString()
    override val printStr: String
        get() = debugStr
    override fun toString(): String = debugStr
    override fun toBoolean(): Boolean = value
    fun toLong(): Long = if (value) 1L else 0L
    constructor(what: ArithmeticValue): this(what.toBoolean())
    override fun plusImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(toLong() + (that as BooleanValue).toLong())
    }
    override fun minusImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(toLong() - (that as BooleanValue).toLong())
    }
    override fun timesImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(toLong() * (that as BooleanValue).toLong())
    }
    override fun divImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(toLong() / (that as BooleanValue).toLong())
    }
    override fun remImpl(that: ArithmeticValue): ArithmeticValue {
        return NumberValue(toLong() % (that as BooleanValue).toLong())
    }
    override operator fun unaryMinus(): ArithmeticValue {
        return NumberValue(-toLong())
    }
}

fun Boolean.toNodeValue() = BooleanValue(this)

@Serializable
sealed class SubscriptValue : NodeValue()

@Serializable
data class NumberSubscriptValue(val begin: Int, val extended: Boolean, val end: Int? = null) : SubscriptValue() {
    override val debugStr: String
        get() = if (extended) {
            if (end == null) {
                "$begin:"
            } else {
                "$begin:$end"
            }
        } else {
            "$begin"
        }
    override val printStr: String
        get() = debugStr
    override fun toString(): String = debugStr
    override fun toBoolean(): Boolean = true
}

@Serializable
data class KeySubscriptValue(val key: String) : SubscriptValue() {
    override val debugStr: String
        get() = key
    override val printStr: String
        get() = debugStr
    override fun toString(): String = debugStr
    override fun toBoolean(): Boolean = true
}

@Serializable
data class ObjectValue(private val attributes: MutableMap<String, NodeValue> = mutableMapOf()) : NodeValue() {
    override val debugStr: String
        get() = "{${attributes.map { "\"${it.key}\": ${it.value.debugStr}" }.joinToString(", ")}}"
    override val printStr: String
        get() = debugStr
    override fun toString(): String = debugStr
    init {
        for (key in attributes.keys) {
            if (attributes[key] is ProcedureValue) {
                attributes[key] = (attributes[key] as ProcedureValue).copy().bind(this)
            }
        }
    }

    override fun toBoolean(): Boolean = attributes.isNotEmpty()
    operator fun get(key: String): NodeValue? = attributes[key]
    operator fun set(key: String, value: NodeValue) {
        attributes[key] = value
    }
}

fun MutableMap<String, NodeValue>.toNodeValue() = ObjectValue(this)

@Serializable
object NullValue : NodeValue() {
    override val debugStr: String
        get() = "null"
    override val printStr: String
        get() = debugStr
    override fun toString(): String = debugStr
    override fun toBoolean(): Boolean = false
}