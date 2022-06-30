package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.saucecode.yqlang.Runtime.CollectionPoolPointer
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer
import top.saucecode.yqlang.Runtime.YqlangRuntimeException

class OperationRuntimeException(message: String) : YqlangRuntimeException(message)

@Serializable
sealed class NodeValue : Comparable<NodeValue> {
    // Collection values must not be directly assigned, and must be passed by reference
//    abstract val passingScheme: PassingScheme
    abstract fun toBoolean(): Boolean
    fun asInteger() = (this as? IntegerValue)?.value
    fun asArithmetic() = this as? ArithmeticValue
    fun asRegEx() = (this as? RegExValue)
    fun asCollection() = (this as? ReferenceValue)?.value
    fun asString() = (this as? ReferenceValue)?.asStringValue()
    fun isStringReference() = this is ReferenceValue && this.value is StringValue
    fun asList() = (this as? ReferenceValue)?.asListValue()
    fun isListReference() = this is ReferenceValue && this.value is ListValue
    fun asObject() = (this as? ReferenceValue)?.asObjectValue()
    fun isObjectReference() = this is ReferenceValue && this.value is ObjectValue
    fun asClosure() = this as? ClosureValue
    abstract fun debugStr(level: Int): String
    abstract fun printStr(level: Int): String
    override fun toString() = debugStr(0)
    open fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        throw OperationRuntimeException("Invalid operation: ${if (!inverse) this else that} + ${if (!inverse) that else this}")
    }
    operator fun plus(that: NodeValue): NodeValue {
        return exchangeablePlus(that, false)
    }
    open fun addAssign(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this += $that")
    open fun subAssign(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this -= $that")
    open fun mulAssign(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this *= $that")
    open fun divAssign(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this /= $that")
    open fun modAssign(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this %= $that")

    open operator fun minus(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this - $that")
    open operator fun times(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this * $that")
    open operator fun div(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this / $that")
    open operator fun rem(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this % $that")
    open operator fun unaryMinus(): NodeValue = throw OperationRuntimeException("Invalid operation: -$this")

    operator fun not(): NodeValue {
        return toBoolean().not().toBooleanValue()
    }

    open operator fun contains(that: NodeValue): Boolean = throw OperationRuntimeException("Invalid operation: $that in $this")
    override operator fun compareTo(other: NodeValue): Int {
        return if (this is IntegerValue && other is IntegerValue) {
            this.value.compareTo(other.value)
        } else if (this is BooleanValue && other is BooleanValue) {
            this.value.compareTo(other.value)
        } else if (this is FloatValue && other is FloatValue) {
            this.value.compareTo(other.value)
        } else if (this is NullValue && other is NullValue) {
            0
        } else if (this is ArithmeticValue && other is ArithmeticValue) {
            this.coercedTo(FloatValue::class).value.compareTo(other.coercedTo(FloatValue::class).value)
        } else {
            throw OperationRuntimeException("Invalid operation: $this <=> $other")
        }
    }

}

interface PrimitivePointingObject {
    fun pointeeCollection(): CollectionPoolPointer
    fun repointedTo(newPointee: CollectionPoolPointer): NodeValue
}

interface MemoryDependent {
    fun bindMemory(memory: Memory)
}

@Serializable
sealed class CollectionValue : Iterable<NodeValue>, MemoryDependent {
    @Transient protected lateinit var memory: Memory
        private set
    private var address: CollectionPoolPointer? = null
    @Transient lateinit var reference: ReferenceValue
        private set
    override fun bindMemory(memory: Memory) {
        this.memory = memory
        if (address != null) {
            reference = ReferenceValue(address!!, memory)
        }
    }
    protected fun solidify(memory: Memory) {
        bindMemory(memory)
        address = memory.putToPool(this)
        reference = ReferenceValue(address!!, memory)
    }
    fun moveThisToNewLocation(newAddress: CollectionPoolPointer) {
        address = newAddress
        reference = ReferenceValue(address!!, memory)
    }
    abstract fun transformPointeePrimitives(transform: (Pointer) -> Pointer)
    abstract fun isNotEmpty(): Boolean
    abstract fun debugStr(level: Int): String
    abstract fun printStr(level: Int): String
    abstract operator fun contains(that: NodeValue): Boolean
    open fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        @Suppress("IMPLICIT_CAST_TO_ANY")
        throw OperationRuntimeException("Invalid operation: ${if (!inverse) this else that} + ${if (!inverse) that else this}")
    }
    open operator fun times(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this * $that")
    open fun addAssign(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this += $that")
    open fun mulAssign(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this *= $that")
    open operator fun compareTo(other: CollectionValue): Int = throw OperationRuntimeException("Invalid operation: $this <=> $other")
}

@Serializable
data class ReferenceValue(val address: CollectionPoolPointer) : NodeValue(), PrimitivePointingObject, Iterable<NodeValue>, MemoryDependent {
    @Transient private lateinit var memory: Memory
    override fun bindMemory(memory: Memory) {
        this.memory = memory
    }
    constructor(address: CollectionPoolPointer, memory: Memory) : this(address) {
        bindMemory(memory)
    }
    val value: CollectionValue
        get() = memory.getFromPool(address)
    override fun toBoolean(): Boolean = value.isNotEmpty()
    override fun debugStr(level: Int): String = value.debugStr(level)
    override fun printStr(level: Int): String = value.printStr(level)
    override fun toString(): String = debugStr(0) // "Collection(${address.toString(16)})"
    override operator fun contains(that: NodeValue): Boolean = that in value
    override fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue = value.exchangeablePlus(that, inverse)
    override operator fun times(that: NodeValue): NodeValue = value.times(that)
    override fun addAssign(that: NodeValue): NodeValue {
        return value.addAssign(that)
    }
    override fun mulAssign(that: NodeValue): NodeValue {
        return value.mulAssign(that)
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReferenceValue) return false
        return this.value == other.value
    }
    override fun hashCode(): Int {
        return value.hashCode()
    }
    override operator fun compareTo(other: NodeValue): Int {
        return if (other is ReferenceValue) {
            return value.compareTo(other.value)
        } else {
            super.compareTo(other)
        }
    }
    fun asStringValue() = value as? StringValue
    fun asListValue() = value as? ListValue
    fun asObjectValue() = value as? ObjectValue
    override fun iterator(): Iterator<NodeValue> = value.iterator()
    override fun pointeeCollection(): CollectionPoolPointer = address
    override fun repointedTo(newPointee: CollectionPoolPointer): ReferenceValue {
        return ReferenceValue(newPointee, memory)
    }
}