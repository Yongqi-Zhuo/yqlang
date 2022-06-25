package top.saucecode.yqlang.Node

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.StringValue
import top.saucecode.yqlang.Runtime.Pointer
import kotlin.math.min

@Serializable
sealed class Node {
    // return value acts as the return value register
    abstract fun exec(context: ExecutionContext): NodeValue
    // names are not variables: a Node is not supposed to be assignable
//    open fun assign(context: ExecutionContext, value: NodeValue): Unit =
//        throw AssignmentRuntimeException(this, value)
}

interface ConvertibleToAssignablePattern {
    fun toPattern(context: ExecutionContext): AssignablePattern
}

class PatternMatchingException(pattern: AssignablePattern, value: NodeValue, msg: String? = null) :
    InterpretationRuntimeException("Pattern $pattern does not match $value${msg?.let { ": $msg" } ?: ""}")

sealed class AssignablePattern {
    abstract fun assign(context: ExecutionContext, value: Pointer)
    abstract fun assignImmediate(context: ExecutionContext, value: NodeValue)
}

class ConstantAssignablePattern(val value: NodeValue) : AssignablePattern() {
    override fun assign(context: ExecutionContext, value: Pointer) {
        assignImmediate(context, context.memory[value])
    }
    override fun assignImmediate(context: ExecutionContext, value: NodeValue) {
        if (value != this.value)
            throw PatternMatchingException(this, value)
    }
}

class AddressAssignablePattern(private val ptr: Pointer) : AssignablePattern() {
    override fun assign(context: ExecutionContext, value: Pointer) {
        assignImmediate(context, context.memory[value])
    }
    override fun assignImmediate(context: ExecutionContext, value: NodeValue) {
        context.memory[ptr] = value
    }
}

class ListAssignablePattern(private val content: List<AssignablePattern>) : AssignablePattern() {
    override fun assign(context: ExecutionContext, value: Pointer) {
        assignImmediate(context, context.memory[value])
    }

    override fun assignImmediate(context: ExecutionContext, value: NodeValue) {
        val list = value.asList()
            ?: throw PatternMatchingException(this, value, "pattern mismatch: cannot assign a non-ListValue to a list pattern")
//        if (list.size != content.size)
//            throw PatternMatchingException(this, value, "pattern mismatch: list size mismatch")
        val cnt = min(content.size, list.size)
        for (i in 0 until cnt)
            content[i].assign(context, list[i])
    }
}

class StringAssignablePattern(private val string: Pointer, private val begin: Int, private val end: Int) : AssignablePattern() {
    override fun assign(context: ExecutionContext, value: Pointer) {
        assignImmediate(context, context.memory[value])
    }
    override fun assignImmediate(context: ExecutionContext, value: NodeValue) {
        val storedString = context.memory[string] as StringValue
        val first = if (begin > 0) storedString.value.substring(0, begin) else ""
        val second = if (end < storedString.value.length) storedString.value.substring(end) else ""
        storedString.value = first + value.printStr + second
    }
}