package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@Serializable
sealed class RangeValue<T : NodeValue>(
    protected val begin: T, protected val end: T, protected val inclusive: Boolean
) : NodeValue(), Iterable<T> {
    override fun toBoolean() = true
    override fun toString() = "range($begin, $end)"
    abstract fun random(): T
    abstract val size: Long
}

@Serializable(with = NumberRangeValue.Serializer::class)
class NumberRangeValue(begin: NumberValue, end: NumberValue, inclusive: Boolean) :
    RangeValue<NumberValue>(begin, end, inclusive) {
    override fun iterator(): Iterator<NumberValue> {
        return object : Iterator<NumberValue> {
            var current = begin
            override fun hasNext(): Boolean {
                return if (inclusive) current <= end else current < end
            }

            override fun next(): NumberValue {
                val result = current
                current = NumberValue(current.value + 1)
                return result
            }
        }
    }

    operator fun contains(value: NumberValue): Boolean {
        return if (inclusive) {
            value.value in (begin.value..end.value)
        } else {
            value.value in (begin.value until end.value)
        }
    }

    override fun random(): NumberValue {
        return if (inclusive) {
            NumberValue((begin.value..end.value).random())
        } else {
            NumberValue((begin.value until end.value).random())
        }
    }

    override val size: Long = if (inclusive) {
        end.value - begin.value + 1
    } else {
        end.value - begin.value
    }

    class Serializer : KSerializer<NumberRangeValue> {
        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor("top.saucecode.yqlang.NodeValue.NumberRangeValue") {
                element<NumberValue>("begin")
                element<NumberValue>("end")
                element<Boolean>("inclusive")
            }

        override fun deserialize(decoder: Decoder): NumberRangeValue = decoder.decodeStructure(descriptor) {
            NumberRangeValue(
                begin = decodeSerializableElement(descriptor, 0, NumberValue.serializer()),
                end = decodeSerializableElement(descriptor, 1, NumberValue.serializer()),
                inclusive = decodeBooleanElement(descriptor, 2)
            )
        }

        override fun serialize(encoder: Encoder, value: NumberRangeValue) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, NumberValue.serializer(), value.begin)
            encodeSerializableElement(descriptor, 1, NumberValue.serializer(), value.end)
            encodeBooleanElement(descriptor, 2, value.inclusive)
        }
    }
}

@Serializable(with = CharRangeValue.Serializer::class)
class CharRangeValue(begin: StringValue, end: StringValue, inclusive: Boolean) :
    RangeValue<StringValue>(begin, end, inclusive) {
    override fun iterator(): Iterator<StringValue> {
        return object : Iterator<StringValue> {
            var current = begin.value[0]
            override fun hasNext(): Boolean {
                return if (inclusive) current <= end.value[0] else current < end.value[0]
            }

            override fun next(): StringValue {
                val result = current
                current++
                return StringValue(result.toString())
            }
        }
    }

    operator fun contains(value: StringValue): Boolean {
        return if (inclusive) {
            value.value[0] in (begin.value[0]..end.value[0])
        } else {
            value.value[0] in (begin.value[0] until end.value[0])
        }
    }

    override fun random(): StringValue {
        return if (inclusive) {
            StringValue((begin.value[0]..end.value[0]).random().toString())
        } else {
            StringValue((begin.value[0] until end.value[0]).random().toString())
        }
    }

    override val size: Long
        get() = if (inclusive) {
            end.value[0] - begin.value[0] + 1
        } else {
            end.value[0] - begin.value[0]
        }.toLong()

    class Serializer : KSerializer<CharRangeValue> {
        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor("top.saucecode.yqlang.NodeValue.CharRangeValue") {
                element<NumberValue>("begin")
                element<NumberValue>("end")
                element<Boolean>("inclusive")
            }

        override fun deserialize(decoder: Decoder): CharRangeValue = decoder.decodeStructure(descriptor) {
            CharRangeValue(
                begin = decodeSerializableElement(descriptor, 0, StringValue.serializer()),
                end = decodeSerializableElement(descriptor, 1, StringValue.serializer()),
                inclusive = decodeBooleanElement(descriptor, 2)
            )
        }

        override fun serialize(encoder: Encoder, value: CharRangeValue) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, StringValue.serializer(), value.begin)
            encodeSerializableElement(descriptor, 1, StringValue.serializer(), value.end)
            encodeBooleanElement(descriptor, 2, value.inclusive)
        }
    }
}