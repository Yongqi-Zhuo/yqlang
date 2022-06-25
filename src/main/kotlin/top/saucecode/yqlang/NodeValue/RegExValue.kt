package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class RegExValue(private val pattern: String, private val rawFlags: String) : PassByValueNodeValue() {
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
    @Transient private val flags: Set<Flag> = rawFlags.mapNotNull { Flag.fromChar(it) }.toSet()
    @Transient private val regex: Regex = Regex(pattern, flags.mapNotNull { it.value }.toSet())

//    fun match(context: ExecutionContext, input: StringValue): List<NodeValue> {
//        if (Flag.GLOBAL !in flags) {
//            val matches = regex.find(input.value) ?: return emptyList()
//            return matches.groupValues.map { it.toNodeValue() }
//        } else {
//            val matches = regex.findAll(input.value)
//            return matches.toList().map { it.value.toNodeValue() }
//        }
//    }
//
//    fun matchAll(input: StringValue): List<List<NodeValue>> {
//        val matches = regex.findAll(input.value)
//        return matches.toList().map { each -> each.groupValues.map { group -> group.toNodeValue() } }
//    }
//
//    fun find(input: StringValue): NodeValue {
//        val matches = regex.find(input.value) ?: return IntegerValue(-1)
//        return matches.range.first.toNodeValue()
//    }
//
//    fun findAll(input: StringValue): List<NodeValue> {
//        val matches = regex.findAll(input.value)
//        return matches.toList().map { each -> each.range.first.toNodeValue() }
//    }
//
//    fun contains(input: StringValue): NodeValue {
//        return regex.containsMatchIn(input.value).toNodeValue()
//    }
//
//    fun replace(input: StringValue, replacement: StringValue): NodeValue {
//        return input.value.replace(regex, replacement.value).toNodeValue()
//    }

    fun split(input: StringValue): List<String> {
        return input.value.split(regex)
    }
//
//    fun matchEntire(input: StringValue): NodeValue {
//        val matches = regex.matchEntire(input.value)
//        return (matches != null).toNodeValue()
//    }
}