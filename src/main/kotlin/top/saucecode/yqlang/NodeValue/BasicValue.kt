package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.Runtime.CollectionPoolPointer
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
    override fun addAssign(that: NodeValue): NodeValue {
        return when (that) {
            is ArithmeticValue -> {
                val level = getHigherLevel(this, that)
                coercedTo(level).plusImpl(that.coercedTo(level))
            }
            else -> super.addAssign(that) // throws
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
    override fun subAssign(that: NodeValue): NodeValue = this.minus(that)
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
    override fun mulAssign(that: NodeValue): NodeValue {
        return if (that is ArithmeticValue) {
            val level = getHigherLevel(this, that)
            coercedTo(level).timesImpl(that.coercedTo(level))
        } else {
            super.mulAssign(that) // throws
        }
    }
    // the argument has the same type as the caller
    protected abstract fun divImpl(that: ArithmeticValue): ArithmeticValue
    override operator fun div(that: NodeValue): NodeValue {
        return if (that is ArithmeticValue) {
            val level = getHigherLevel(this, that)
            coercedTo(level).divImpl(that.coercedTo(level))
        } else {
            super.div(that) // throws
        }
    }
    override fun divAssign(that: NodeValue): NodeValue = this.div(that)
    // the argument has the same type as the caller
    protected abstract fun remImpl(that: ArithmeticValue): ArithmeticValue
    override operator fun rem(that: NodeValue): NodeValue {
        return if (that is ArithmeticValue) {
            val level = getHigherLevel(this, that)
            coercedTo(level).remImpl(that.coercedTo(level))
        } else {
            super.rem(that)
        }
    }
    override fun modAssign(that: NodeValue): NodeValue = this.rem(that)
    abstract fun abs(): ArithmeticValue
    @Suppress("UNCHECKED_CAST")
    fun<T: ArithmeticValue> coercedTo(level: KClass<T>): T {
        return when (level) {
            BooleanValue::class -> BooleanValue(this) as T
            IntegerValue::class -> IntegerValue(this) as T
            FloatValue::class -> FloatValue(this) as T
            else -> throw IllegalArgumentException("Cannot coerce $this to $level")
        }
    }
    companion object {
        // TODO: avoid reflection
        private val coercionLevels = listOf(BooleanValue::class, IntegerValue::class, FloatValue::class)
        private fun getHigherLevel(value1: ArithmeticValue, value2: ArithmeticValue): KClass<out ArithmeticValue> {
            val level1 = coercionLevels.indexOf(value1::class)
            val level2 = coercionLevels.indexOf(value2::class)
            return if (level1 > level2) value1::class else value2::class
        }
    }
}

@Serializable
data class IntegerValue(val value: Long) : ArithmeticValue() {
    override fun debugStr(level: Int): String = value.toString()
    override fun printStr(level: Int): String = debugStr(0)
    override fun toString(): String = debugStr(0)
    override fun toBoolean(): Boolean = value != 0L
    constructor(what: ArithmeticValue): this(when(what) {
        is IntegerValue -> what.value
        is FloatValue -> what.value.toLong()
        is BooleanValue -> if (what.value) 1L else 0L
    })
    override fun plusImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(value + (that as IntegerValue).value)
    }
    override fun minusImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(value - (that as IntegerValue).value)
    }
    override fun timesImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(value * (that as IntegerValue).value)
    }

    override fun divImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(value / (that as IntegerValue).value)
    }
    override fun remImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(value % (that as IntegerValue).value)
    }
    override operator fun unaryMinus(): ArithmeticValue {
        return IntegerValue(-value)
    }

    override fun abs(): ArithmeticValue = IntegerValue(kotlin.math.abs(value))
}

fun Int.toIntegerValue(): NodeValue = IntegerValue(this.toLong())
fun Long.toIntegerValue(): NodeValue = IntegerValue(this)

@Serializable
data class FloatValue(val value: Double) : ArithmeticValue() {
    override fun debugStr(level: Int): String = value.toString()
    override fun printStr(level: Int): String = debugStr(0)

    override fun toString(): String = debugStr(0)
    override fun toBoolean(): Boolean = value != 0.0

    constructor(what: ArithmeticValue) : this(
        when (what) {
            is IntegerValue -> what.value.toDouble()
            is FloatValue -> what.value
            is BooleanValue -> if (what.value) 1.0 else 0.0
        }
    )

    override fun plusImpl(that: ArithmeticValue): ArithmeticValue {
        return FloatValue(value + (that as FloatValue).value)
    }

    override fun minusImpl(that: ArithmeticValue): ArithmeticValue {
        return FloatValue(value - (that as FloatValue).value)
    }

    override fun timesImpl(that: ArithmeticValue): ArithmeticValue {
        return FloatValue(value * (that as FloatValue).value)
    }

    override fun divImpl(that: ArithmeticValue): ArithmeticValue {
        return FloatValue(value / (that as FloatValue).value)
    }

    override fun remImpl(that: ArithmeticValue): ArithmeticValue {
        throw OperationRuntimeException("remainder is not defined for floating point numbers")
    }

    override operator fun unaryMinus(): ArithmeticValue {
        return FloatValue(-value)
    }

    override fun abs(): ArithmeticValue = FloatValue(kotlin.math.abs(value))
}

fun Double.toFloatValue(): NodeValue = FloatValue(this)

@Serializable
data class BooleanValue(val value: Boolean) : ArithmeticValue() {
    override fun debugStr(level: Int): String = value.toString()
    override fun printStr(level: Int): String = debugStr(0)
    override fun toString(): String = debugStr(0)
    override fun toBoolean(): Boolean = value
    fun toLong(): Long = if (value) 1L else 0L
    constructor(what: ArithmeticValue): this(what.toBoolean())
    override fun plusImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(toLong() + (that as BooleanValue).toLong())
    }
    override fun minusImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(toLong() - (that as BooleanValue).toLong())
    }
    override fun timesImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(toLong() * (that as BooleanValue).toLong())
    }
    override fun divImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(toLong() / (that as BooleanValue).toLong())
    }
    override fun remImpl(that: ArithmeticValue): ArithmeticValue {
        return IntegerValue(toLong() % (that as BooleanValue).toLong())
    }
    override operator fun unaryMinus(): ArithmeticValue {
        return IntegerValue(-toLong())
    }

    override fun abs(): ArithmeticValue = this.coercedTo(IntegerValue::class)
}

fun Boolean.toBooleanValue() = BooleanValue(this)

@Serializable
sealed class SubscriptValue : NodeValue()

@Serializable
data class IntegerSubscriptValue(val begin: Int, val extended: Boolean, val end: Int? = null) : SubscriptValue() {
    override fun debugStr(level: Int): String = if (extended) {
            if (end == null) {
                "$begin:"
            } else {
                "$begin:$end"
            }
        } else {
            "$begin"
        }
    override fun printStr(level: Int): String = debugStr(0)
    override fun toString(): String = debugStr(0)
    override fun toBoolean(): Boolean = true
}

@Serializable
data class KeySubscriptValue(val key: String) : SubscriptValue() {
    override fun debugStr(level: Int): String = key
    override fun printStr(level: Int): String = debugStr(0)
    override fun toString(): String = debugStr(0)
    override fun toBoolean(): Boolean = true
}

@Serializable
data class ClosureValue(val captureList: CollectionPoolPointer, val entry: Int) : NodeValue(), PrimitivePointingObject {
    override fun toBoolean(): Boolean = true
    override fun debugStr(level: Int): String = "closure($captureList, $entry)"
    override fun printStr(level: Int): String = debugStr(0)
    override fun gcPointeeCollection(): CollectionPoolPointer = captureList
    override fun gcRepointedTo(newPointee: CollectionPoolPointer): ClosureValue {
        return ClosureValue(newPointee, entry)
    }
}

@Serializable
object NullValue : NodeValue() {
    override fun debugStr(level: Int): String = "null"
    override fun printStr(level: Int): String = debugStr(0)
    override fun toString(): String = debugStr(0)
    override fun toBoolean(): Boolean = false
}
