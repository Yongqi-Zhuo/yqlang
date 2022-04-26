package top.saucecode.NodeValue

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@Serializable(with = RegExValue.Serializer::class)
data class RegExValue(private val pattern: String) : NodeValue() {
    override fun toBoolean(): Boolean = pattern.isNotEmpty()
    private val regex: Regex
    init {
        regex = Regex(pattern)
    }

    fun match(input: StringValue): NodeValue {
        val matches = regex.find(input.value) ?: return NullValue
        return matches.groupValues.map { it.toNodeValue() }.toNodeValue()
    }

    fun matchAll(input: StringValue): NodeValue {
        val matches = regex.findAll(input.value)
        return matches.toList().map { each -> each.groupValues.map { group -> group.toNodeValue() }.toNodeValue() }.toNodeValue()
    }

    fun find(input: StringValue): NodeValue {
        val matches = regex.find(input.value) ?: return NumberValue(-1)
        return matches.range.first.toNodeValue()
    }

    fun findAll(input: StringValue): NodeValue {
        val matches = regex.findAll(input.value)
        return matches.toList().map { each -> each.range.first.toNodeValue() }.toNodeValue()
    }

    fun contains(input: StringValue): NodeValue {
        return regex.containsMatchIn(input.value).toNodeValue()
    }

    fun replace(input: StringValue, replacement: StringValue): NodeValue {
        return input.value.replace(regex, replacement.value).toNodeValue()
    }

    fun split(input: StringValue): NodeValue {
        return input.value.split(regex).map { it.toNodeValue() }.toNodeValue()
    }

    fun matchEntire(input: StringValue): NodeValue {
        val matches = regex.matchEntire(input.value)
        return (matches != null).toNodeValue()
    }

    class Serializer : KSerializer<RegExValue> {
        override val descriptor = buildClassSerialDescriptor("top.saucecode.NodeValue.RegExValue") {
            element<String>("pattern")
        }
        override fun deserialize(decoder: Decoder): RegExValue = decoder.decodeStructure(descriptor) {
            RegExValue(decodeStringElement(descriptor, 0))
        }
        override fun serialize(encoder: Encoder, value: RegExValue) = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.pattern)
        }
    }

    override fun toString(): String = "regex($pattern)"
}