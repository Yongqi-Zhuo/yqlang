package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import top.saucecode.yqlang.Runtime.Memory

// TODO: Remove all Iterable<*> and implement iterator in VM.
@Serializable
sealed class RangeValue<T : NodeValue> : NodeValue(), Iterable<T> {
    override fun toBoolean() = true
    abstract override operator fun contains(that: NodeValue): Boolean
    abstract fun random(): T
    abstract val size: Long
}

@Serializable
data class IntegerRangeValue(private val begin: Long, private val end: Long, private val inclusive: Boolean) :
    RangeValue<IntegerValue>() {
    override fun debugStr(level: Int): String = if (inclusive) {
        "[$begin, $end]"
    } else {
        "[$begin, $end)"
    }
    override fun printStr(level: Int): String = debugStr(0)
    override fun toString(): String = debugStr(0)
    override fun iterator(): Iterator<IntegerValue> {
        return object : Iterator<IntegerValue> {
            var current = begin
            override fun hasNext(): Boolean {
                return if (inclusive) current <= end else current < end
            }

            override fun next(): IntegerValue {
                val result = current
                current += 1
                return IntegerValue(result)
            }
        }
    }

    override operator fun contains(that: NodeValue): Boolean {
        if (that !is IntegerValue) return false
        return if (inclusive) {
            that.value in (begin..end)
        } else {
            that.value in (begin until end)
        }
    }

    override fun random(): IntegerValue {
        return if (inclusive) {
            IntegerValue((begin..end).random())
        } else {
            IntegerValue((begin until end).random())
        }
    }

    override val size: Long = if (inclusive) {
        end - begin + 1
    } else {
        end - begin
    }
}

data class CharRangeValue(private val begin: Char, private val end: Char, private val inclusive: Boolean) :
    RangeValue<ReferenceValue>(), MemoryDependent {
    @Transient lateinit var memory: Memory
    override fun bindMemory(memory: Memory) {
        this.memory = memory
    }
    constructor(begin: Char, end: Char, inclusive: Boolean, memory: Memory) : this(begin, end, inclusive) {
        bindMemory(memory)
    }
    override fun debugStr(level: Int): String = if (inclusive) {
            "['$begin', '$end']"
        } else {
            "['$begin', '$end')"
        }
    override fun printStr(level: Int): String = debugStr(0)
    override fun toString(): String = debugStr(0)
    override fun iterator(): Iterator<ReferenceValue> {
        return object : Iterator<ReferenceValue> {
            var current = begin
            override fun hasNext(): Boolean {
                return if (inclusive) current <= end else current < end
            }

            override fun next(): ReferenceValue {
                val result = current
                current++
                return StringValue(result.toString(), memory).reference
            }
        }
    }
    override operator fun contains(that: NodeValue): Boolean {
        if (!that.isStringReference()) return false
        val str = that.asString()!!.value
        if (str.length != 1) return false
        return if (inclusive) {
            str[0] in (begin..end)
        } else {
            str[0] in (begin until end)
        }
    }

    override fun random(): ReferenceValue {
        return if (inclusive) {
            StringValue((begin..end).random().toString(), memory).reference
        } else {
            StringValue((begin until end).random().toString(), memory).reference
        }
    }

    override val size: Long
        get() = if (inclusive) {
            end.code - begin.code + 1
        } else {
            end.code - begin.code
        }.toLong()
}