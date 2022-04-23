package top.saucecode.NodeValue

import kotlinx.serialization.Serializable
import top.saucecode.Node.ValueNode

@Serializable
sealed class NodeValue : Comparable<NodeValue> {
    abstract fun toBoolean(): Boolean
    fun asString() = (this as? StringValue)?.value
    fun asNumber() = (this as? NumberValue)?.value
    fun asList() = (this as? ListValue)?.value
    fun asObject() = this as? ObjectValue
    fun asProcedure() = (this as? ProcedureValue)
    fun toNode() = ValueNode(this)
    operator fun plus(that: NodeValue): NodeValue {
        return when (this) {
            is BooleanValue -> {
                when (that) {
                    is BooleanValue -> NumberValue(this.value.toLong() + that.value.toLong())
                    is NumberValue -> NumberValue(this.value.toLong() + that.value)
                    is StringValue -> StringValue(this.value.toString() + that.value)
                    is ListValue -> ListValue(mutableListOf<NodeValue>(this).apply { addAll(that.value) })
                    else -> throw IllegalArgumentException("Invalid operation: $this + $that")
                }
            }
            is NumberValue -> {
                when (that) {
                    is BooleanValue -> NumberValue(this.value + that.value.toLong())
                    is NumberValue -> NumberValue(this.value + that.value)
                    is StringValue -> StringValue(this.value.toString() + that.value)
                    is ListValue -> ListValue(mutableListOf<NodeValue>(this).apply { addAll(that.value) })
                    else -> throw IllegalArgumentException("Invalid operation: $this + $that")
                }
            }
            is StringValue -> {
                when (that) {
                    is BooleanValue -> StringValue(this.value + that.value.toString())
                    is NumberValue -> StringValue(this.value + that.value.toString())
                    is StringValue -> StringValue(this.value + that.value)
                    is ListValue -> ListValue(mutableListOf<NodeValue>(this).apply { addAll(that.value) })
                    is ObjectValue -> StringValue(this.value + that.toString())
                    else -> throw IllegalArgumentException("Invalid operation: $this + $that")
                }
            }
            is ListValue -> {
                when (that) {
                    is BooleanValue -> ListValue(this.value.toMutableList().apply { add(that) })
                    is NumberValue -> ListValue(this.value.toMutableList().apply { add(that) })
                    is StringValue -> ListValue(this.value.toMutableList().apply { add(that) })
                    is ListValue -> ListValue(this.value.toMutableList().apply { addAll(that.value) })
                    is ObjectValue -> ListValue(this.value.toMutableList().apply { add(that) })
                    else -> throw IllegalArgumentException("Invalid operation: $this + $that")
                }
            }
            is ObjectValue -> {
                when (that) {
                    is StringValue -> StringValue(toString() + that.value)
                    is ListValue -> ListValue(mutableListOf<NodeValue>(this).apply { addAll(that.value) })
                    else -> throw IllegalArgumentException("Invalid operation: $this + $that")
                }
            }
            else -> throw IllegalArgumentException("Invalid operation: $this + $that")
        }
    }

    operator fun minus(that: NodeValue): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        val other = if (that is BooleanValue) NumberValue(that.value.toLong()) else that
        if (expr is NumberValue && other is NumberValue) {
            return NumberValue(expr.value - other.value)
        } else {
            throw IllegalArgumentException("Invalid operation: $this - $that")
        }
    }

    operator fun times(that: NodeValue): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        val other = if (that is BooleanValue) NumberValue(that.value.toLong()) else that
        if (expr is NumberValue || other is NumberValue) {
            val (num, otherExpr) = if (expr is NumberValue) Pair(expr.asNumber()!!, other) else Pair(
                other.asNumber()!!, expr
            )
            return when (otherExpr) {
                is NumberValue -> NumberValue(num * otherExpr.value)
                is StringValue -> otherExpr.value.repeat(num.toInt()).toNodeValue()
                is ListValue -> {
                    val sz = otherExpr.value.size
                    val cnt = num.toInt()
                    val list = otherExpr.asList()!!
                    List(cnt * sz) { index -> list[index % sz] }.toNodeValue()
                }
                else -> throw IllegalArgumentException("Invalid operation: $this * $that")
            }
        } else {
            throw IllegalArgumentException("Invalid operation: $this * $that")
        }
    }

    operator fun div(that: NodeValue): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        val other = if (that is BooleanValue) NumberValue(that.value.toLong()) else that
        if (expr is NumberValue && other is NumberValue) {
            return NumberValue(expr.value / other.value)
        } else {
            throw IllegalArgumentException("Invalid operation: $this / $that")
        }
    }

    operator fun rem(that: NodeValue): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        val other = if (that is BooleanValue) NumberValue(that.value.toLong()) else that
        if (expr is NumberValue && other is NumberValue) {
            return NumberValue(expr.value % other.value)
        } else {
            throw IllegalArgumentException("Invalid operation: $this % $that")
        }
    }

    operator fun unaryMinus(): NodeValue {
        val expr = if (this is BooleanValue) NumberValue(this.value.toLong()) else this
        if (expr is NumberValue) {
            return NumberValue(-expr.value)
        } else {
            throw IllegalArgumentException("Invalid operation: -$this")
        }
    }

    operator fun not(): NodeValue {
        return toBoolean().not().toNodeValue()
    }

    operator fun contains(that: NodeValue): Boolean {
        return if (this is StringValue && that is StringValue) {
            this.value.contains(that.value)
        } else if (this is Iterable<*>) {
            (this as Iterable<*>).contains(that)
        } else {
            throw IllegalArgumentException("Invalid operation: $that in $this")
        }
    }

    override operator fun compareTo(other: NodeValue): Int {
        return if (this is NumberValue && other is NumberValue) {
            this.value.compareTo(other.value)
        } else if (this is StringValue && other is StringValue) {
            this.value.compareTo(other.value)
        } else if (this is BooleanValue && other is BooleanValue) {
            this.value.compareTo(other.value)
        } else if (this is NullValue && other is NullValue) {
            0
        } else {
            throw IllegalArgumentException("Invalid operation: $this <=> $other")
        }
    }

}