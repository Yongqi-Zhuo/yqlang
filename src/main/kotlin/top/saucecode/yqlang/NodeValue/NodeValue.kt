package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.Runtime.CollectionPoolPointer
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer

class OperationRuntimeException(message: String) : InterpretationRuntimeException(message)

@Serializable
sealed class NodeValue : Comparable<NodeValue> {
    // Collection values must not be directly assigned, and must be passed by reference
//    abstract val passingScheme: PassingScheme
    abstract fun toBoolean(): Boolean
    fun asInteger() = (this as? IntegerValue)?.value
    fun asArithmetic() = this as? ArithmeticValue
    fun asRegEx() = (this as? RegExValue)
    fun asString() = (this as? StringValue)?.value
    fun asList() = (this as? ListValue)?.value
    fun asObject() = this as? ObjectValue
    abstract val debugStr: String
    abstract val printStr: String
    override fun toString() = debugStr
    open fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        throw OperationRuntimeException("Invalid operation: ${if (!inverse) this else that} + ${if (!inverse) that else this}")
    }
    operator fun plus(that: NodeValue): NodeValue {
        return exchangeablePlus(that, false)
    }

    open operator fun minus(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this - $that")
    open operator fun times(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this * $that")
    open operator fun div(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this / $that")
    open operator fun rem(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this % $that")
    open operator fun unaryMinus(): NodeValue = throw OperationRuntimeException("Invalid operation: -$this")

    operator fun not(): NodeValue {
        return toBoolean().not().toNodeValue()
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
        } else {
            throw OperationRuntimeException("Invalid operation: $this <=> $other")
        }
    }

}

class AccessingUnsolidifiedValueException(subject: Any) : InterpretationRuntimeException("Accessing unsolidified value: $subject")
@Serializable
sealed class CollectionValue {
    @Transient protected var memory: Memory? = null
        private set
    protected var address: CollectionPoolPointer? = null
        private set
    protected var referenceField: ReferenceValue? = null
    val reference: ReferenceValue
        get() = referenceField!!
    fun bindMemory(memory: Memory) {
        this.memory = memory
    }
    protected fun solidify(memory: Memory) {
        bindMemory(memory)
        address = memory.putToPool(this)
        referenceField = ReferenceValue(address!!, memory)
    }
    abstract fun isNotEmpty(): Boolean
    abstract val debugStr: String
    abstract val printStr: String
    abstract operator fun contains(that: NodeValue): Boolean
    open fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue {
        throw OperationRuntimeException("Invalid operation: ${if (!inverse) this else that} + ${if (!inverse) that else this}")
    }
    open operator fun times(that: NodeValue): NodeValue = throw OperationRuntimeException("Invalid operation: $this * $that")
    open operator fun compareTo(other: CollectionValue): Int = throw OperationRuntimeException("Invalid operation: $this <=> $other")
}

@Serializable
data class ReferenceValue(private val address: CollectionPoolPointer) : NodeValue() {
    @Transient private var memory: Memory? = null
    constructor(address: Pointer, memory: Memory) : this(address) {
        bindMemory(memory)
    }
    fun bindMemory(memory: Memory) {
        this.memory = memory
    }
    val value: CollectionValue
        get() = memory!!.getFromPool(address)
    override fun toBoolean(): Boolean = value.isNotEmpty()
    override val debugStr: String = value.debugStr
    override val printStr: String = value.printStr
    override operator fun contains(that: NodeValue): Boolean = that in value
    override fun exchangeablePlus(that: NodeValue, inverse: Boolean): NodeValue = value.exchangeablePlus(that, inverse)
    override operator fun times(that: NodeValue): NodeValue = value.times(that)
    override operator fun compareTo(other: NodeValue): Int {
        return if (other is ReferenceValue) {
            return value.compareTo(other.value)
        } else {
            super.compareTo(other)
        }
    }
}