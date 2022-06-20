package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@Serializable(with = RegExValue.Serializer::class)
class RegExValue(private val pattern: String, private val rawFlags: String) : NodeValue() {
    override val debugStr: String
        get() = "/$pattern/$rawFlags"
    override val printStr: String
        get() = debugStr
    enum class Flag(val value: RegexOption?) {
        GLOBAL(null),
        IGNORE_CASE(RegexOption.IGNORE_CASE),
        MULTILINE(RegexOption.MULTILINE),
        DOT_MATCHES_ALL(RegexOption.DOT_MATCHES_ALL);
        companion object {
            fun fromChar(c: Char): Flag? {
                return when (c) {
                    'g' -> GLOBAL
                    'i' -> IGNORE_CASE
                    'm' -> MULTILINE
                    's' -> DOT_MATCHES_ALL
                    else -> null
                }
            }
        }
    }
    override fun toBoolean(): Boolean = pattern.isNotEmpty()
    private val regex: Regex
    private val flags: Set<Flag> = rawFlags.mapNotNull { Flag.fromChar(it) }.toSet()
    init {
        regex = Regex(pattern, flags.mapNotNull { it.value }.toSet())
    }

    fun match(input: StringValue): NodeValue {
        if (Flag.GLOBAL !in flags) {
            val matches = regex.find(input.value) ?: return NullValue
            return matches.groupValues.map { it.toNodeValue() }.toNodeValue()
        } else {
            val matches = regex.findAll(input.value)
            return matches.toList().map { it.value.toNodeValue() }.toNodeValue()
        }
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
        override val descriptor = buildClassSerialDescriptor("top.saucecode.yqlang.NodeValue.RegExValue") {
            element<String>("pattern")
            element<String>("rawFlags")
        }
        override fun deserialize(decoder: Decoder): RegExValue = decoder.decodeStructure(descriptor) {
            RegExValue(decodeStringElement(descriptor, 0), decodeStringElement(descriptor, 1))
        }
        override fun serialize(encoder: Encoder, value: RegExValue) = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.pattern)
            encodeStringElement(descriptor, 1, value.rawFlags)
        }
    }
}