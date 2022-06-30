package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class RegExValue(private val pattern: String, private val rawFlags: String) : NodeValue() {
    override fun debugStr(level: Int): String = "/$pattern/$rawFlags"
    override fun printStr(level: Int): String = debugStr(0)
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

    fun match(input: String): List<String> {
        if (Flag.GLOBAL !in flags) {
            val matches = regex.find(input) ?: return emptyList()
            return matches.groupValues
        } else {
            val matches = regex.findAll(input)
            return matches.toList().map { it.value }
        }
    }

    fun matchAll(input: String): List<List<String>> {
        val matches = regex.findAll(input)
        return matches.toList().map { each -> each.groupValues }
    }

    fun find(input: String): NodeValue {
        val matches = regex.find(input) ?: return IntegerValue(-1)
        return matches.range.first.toIntegerValue()
    }

    fun findAll(input: String): List<NodeValue> {
        val matches = regex.findAll(input)
        return matches.toList().map { each -> each.range.first.toIntegerValue() }
    }

    fun contains(input: String): NodeValue {
        return regex.containsMatchIn(input).toBooleanValue()
    }

    fun replace(input: String, replacement: String): String {
        return input.replace(regex, replacement)
    }

    fun split(input: String): List<String> {
        return input.split(regex)
    }

    fun matchEntire(input: String): NodeValue {
        val matches = regex.matchEntire(input)
        return (matches != null).toBooleanValue()
    }
}