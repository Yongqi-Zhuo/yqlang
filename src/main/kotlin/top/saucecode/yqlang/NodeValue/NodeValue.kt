package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.Node.ValueNode

class OperationRuntimeException(message: String) : InterpretationRuntimeException(message)

@Serializable
sealed class NodeValue : Comparable<NodeValue> {
    abstract fun toBoolean(): Boolean
    fun asString() = (this as? StringValue)?.value
    fun asInteger() = (this as? IntegerValue)?.value
    fun asArithmetic() = this as? ArithmeticValue
    fun asList() = (this as? ListValue)?.value
    fun asObject() = this as? ObjectValue
    fun asProcedure() = (this as? ProcedureValue)
    fun asRegEx() = (this as? RegExValue)
    fun toNode() = ValueNode(this)
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

    operator fun contains(that: NodeValue): Boolean {
        return if (this is StringValue && that is StringValue) {
            this.value.contains(that.value)
        } else if (this is Iterable<*>) {
            (this as Iterable<*>).contains(that)
        } else {
            throw OperationRuntimeException("Invalid operation: $that in $this")
        }
    }
    override operator fun compareTo(other: NodeValue): Int {
        return if (this is IntegerValue && other is IntegerValue) {
            this.value.compareTo(other.value)
        } else if (this is StringValue && other is StringValue) {
            this.value.compareTo(other.value)
        } else if (this is BooleanValue && other is BooleanValue) {
            this.value.compareTo(other.value)
        } else if (this is NullValue && other is NullValue) {
            0
        } else {
            throw OperationRuntimeException("Invalid operation: $this <=> $other")
        }
    }

}