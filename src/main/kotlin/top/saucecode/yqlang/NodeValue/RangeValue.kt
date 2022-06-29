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

// TODO: Remove all Iterable<*> and implement iterator in VM.
@Serializable
sealed class RangeValue<T : NodeValue>(
    protected val begin: T, protected val end: T, protected val inclusive: Boolean
) : NodeValue(), Iterable<T> {
    override fun toBoolean() = true
    override fun toString() = "range($begin, $end)"
    abstract override operator fun contains(that: NodeValue): Boolean
    abstract fun random(): T
    abstract val size: Long
}

@Serializable(with = IntegerRangeValue.Serializer::class)
class IntegerRangeValue(begin: IntegerValue, end: IntegerValue, inclusive: Boolean) :
    RangeValue<IntegerValue>(begin, end, inclusive) {
    override val debugStr: String
        get() = if (inclusive) {
            "[$begin, $end]"
        } else {
            "[$begin, $end)"
        }
    override val printStr: String
        get() = debugStr
    override fun iterator(): Iterator<IntegerValue> {
        return object : Iterator<IntegerValue> {
            var current = begin
            override fun hasNext(): Boolean {
                return if (inclusive) current <= end else current < end
            }

            override fun next(): IntegerValue {
                val result = current
                current = IntegerValue(current.value + 1)
                return result
            }
        }
    }

    override operator fun contains(that: NodeValue): Boolean {
        if (that !is IntegerValue) return false
        return if (inclusive) {
            that.value in (begin.value..end.value)
        } else {
            that.value in (begin.value until end.value)
        }
    }

    override fun random(): IntegerValue {
        return if (inclusive) {
            IntegerValue((begin.value..end.value).random())
        } else {
            IntegerValue((begin.value until end.value).random())
        }
    }

    override val size: Long = if (inclusive) {
        end.value - begin.value + 1
    } else {
        end.value - begin.value
    }

    class Serializer : KSerializer<IntegerRangeValue> {
        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor("top.saucecode.yqlang.NodeValue.IntegerRangeValue") {
                element<IntegerValue>("begin")
                element<IntegerValue>("end")
                element<Boolean>("inclusive")
            }

        override fun deserialize(decoder: Decoder): IntegerRangeValue = decoder.decodeStructure(descriptor) {
            IntegerRangeValue(
                begin = decodeSerializableElement(descriptor, 0, IntegerValue.serializer()),
                end = decodeSerializableElement(descriptor, 1, IntegerValue.serializer()),
                inclusive = decodeBooleanElement(descriptor, 2)
            )
        }

        override fun serialize(encoder: Encoder, value: IntegerRangeValue) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, IntegerValue.serializer(), value.begin)
            encodeSerializableElement(descriptor, 1, IntegerValue.serializer(), value.end)
            encodeBooleanElement(descriptor, 2, value.inclusive)
        }
    }
}
//
//@Serializable(with = CharRangeValue.Serializer::class)
//class CharRangeValue(begin: StringValue, end: StringValue, inclusive: Boolean) :
//    RangeValue<StringValue>(begin, end, inclusive) {
//    override val debugStr: String
//        get() = if (inclusive) {
//            "[$begin, $end]"
//        } else {
//            "[$begin, $end)"
//        }
//    override val printStr: String
//        get() = debugStr
//    override fun iterator(): Iterator<StringValue> {
//        return object : Iterator<StringValue> {
//            var current = begin.value[0]
//            override fun hasNext(): Boolean {
//                return if (inclusive) current <= end.value[0] else current < end.value[0]
//            }
//
//            override fun next(): StringValue {
//                val result = current
//                current++
//                return StringValue(result.toString())
//            }
//        }
//    }
//
//    operator fun contains(value: StringValue): Boolean {
//        return if (inclusive) {
//            value.value[0] in (begin.value[0]..end.value[0])
//        } else {
//            value.value[0] in (begin.value[0] until end.value[0])
//        }
//    }
//
//    override fun random(): StringValue {
//        return if (inclusive) {
//            StringValue((begin.value[0]..end.value[0]).random().toString())
//        } else {
//            StringValue((begin.value[0] until end.value[0]).random().toString())
//        }
//    }
//
//    override val size: Long
//        get() = if (inclusive) {
//            end.value[0] - begin.value[0] + 1
//        } else {
//            end.value[0] - begin.value[0]
//        }.toLong()
//
//    class Serializer : KSerializer<CharRangeValue> {
//        override val descriptor: SerialDescriptor =
//            buildClassSerialDescriptor("top.saucecode.yqlang.NodeValue.CharRangeValue") {
//                element<IntegerValue>("begin")
//                element<IntegerValue>("end")
//                element<Boolean>("inclusive")
//            }
//
//        override fun deserialize(decoder: Decoder): CharRangeValue = decoder.decodeStructure(descriptor) {
//            CharRangeValue(
//                begin = decodeSerializableElement(descriptor, 0, StringValue.serializer()),
//                end = decodeSerializableElement(descriptor, 1, StringValue.serializer()),
//                inclusive = decodeBooleanElement(descriptor, 2)
//            )
//        }
//
//        override fun serialize(encoder: Encoder, value: CharRangeValue) = encoder.encodeStructure(descriptor) {
//            encodeSerializableElement(descriptor, 0, StringValue.serializer(), value.begin)
//            encodeSerializableElement(descriptor, 1, StringValue.serializer(), value.end)
//            encodeBooleanElement(descriptor, 2, value.inclusive)
//        }
//    }
//}