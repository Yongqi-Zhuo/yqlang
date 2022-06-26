package top.saucecode.yqlang.Node

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer
import top.saucecode.yqlang.Token
import top.saucecode.yqlang.TokenType

class TypeMismatchRuntimeException(expected: List<Class<*>>, got: Any) :
    InterpretationRuntimeException("Type mismatch, expected one of ${
        expected.joinToString(", ") { it.simpleName }
    }, got ${got.javaClass.simpleName}")

@Serializable
class IdentifierNode(val name: String) : Node(), ConvertibleToAssignablePattern {

    constructor(token: Token) : this(token.value)

    override fun exec(context: ExecutionContext): NodeValue {
        return context.referenceEnvironment.getName(name)?.let {
            context.memory[it]
        } ?: NullValue
    }

    override fun toPattern(context: ExecutionContext): AssignablePattern {
        val ptr = context.referenceEnvironment.getName(name)
        return if (ptr == null) {
            val addr = context.memory.allocate(NullValue)
            context.referenceEnvironment.setLocalName(name, addr)
            AddressAssignablePattern(addr)
        } else {
            AddressAssignablePattern(ptr)
        }
    }

    override fun toString(): String {
        return "id($name)"
    }

}

@Serializable
class IntegerNode(private val value: Long) : Node(), ConvertibleToAssignablePattern {

    constructor(token: Token) : this(token.value.toLong())

    override fun exec(context: ExecutionContext): NodeValue {
        return value.toNodeValue()
    }

    override fun toPattern(context: ExecutionContext): ConstantAssignablePattern {
        return ConstantAssignablePattern(value.toNodeValue())
    }

    override fun toString(): String {
        return "integer($value)"
    }
}

@Serializable
class FloatNode(private val value: Double) : Node(), ConvertibleToAssignablePattern {

    constructor(token: Token) : this(token.value.toDouble())

    override fun exec(context: ExecutionContext): NodeValue {
        return value.toNodeValue()
    }

    override fun toPattern(context: ExecutionContext): ConstantAssignablePattern {
        return ConstantAssignablePattern(value.toNodeValue())
    }

    override fun toString(): String {
        return "float($value)"
    }
}

@Serializable
class StringNode(private val value: String) : Node(), ConvertibleToAssignablePattern {

    constructor(token: Token) : this(token.value)

    override fun exec(context: ExecutionContext): NodeValue {
        return StringValue(value, context.memory)
    }

    override fun toPattern(context: ExecutionContext): ConstantAssignablePattern {
        return ConstantAssignablePattern(exec(context))
    }

    override fun toString(): String {
        return "str(\"$value\")"
    }
}

@Serializable
class ListNode(val items: List<Node>) : Node(), ConvertibleToAssignablePattern {
    override fun exec(context: ExecutionContext): ListValue {
        // create new instance on heap
        return ListValue(items.mapTo(mutableListOf()) {
            context.memory.createReference(it.exec(context))
        }, context.memory)
    }

    override fun toPattern(context: ExecutionContext): AssignablePattern {
        return ListAssignablePattern(items.map { node ->
            if (node is ConvertibleToAssignablePattern) {
                node.toPattern(context)
            } else {
                throw TypeMismatchRuntimeException(listOf(ConvertibleToAssignablePattern::class.java), node)
            }
        })
    }

    constructor(vararg items: String) : this(items.map { IdentifierNode(Token(TokenType.IDENTIFIER, it)) })

    override fun toString(): String {
        return "[${items.joinToString(", ")}]"
    }
}

@Serializable
class SubscriptNode(private val begin: Node, private val extended: Boolean, private val end: Node? = null) : Node() {
    override fun exec(context: ExecutionContext): SubscriptValue {
        return when (val begin = begin.exec(context)) {
            is IntegerValue -> IntegerSubscriptValue(
                begin.value.toInt(), extended, end?.exec(context)?.asInteger()?.toInt()
            )
            is StringValue -> KeySubscriptValue(begin.value)
            else -> throw TypeMismatchRuntimeException(listOf(IntegerValue::class.java, StringValue::class.java), begin)
        }
    }

    override fun toString(): String {
        return if (end != null) "subscript($begin, $end)" else "subscript($begin)"
    }
}

@Serializable
class ObjectNode(private val items: List<Pair<IdentifierNode, Node>>) : Node() {
    override fun exec(context: ExecutionContext): ObjectValue {
        return ObjectValue(items.associateTo(mutableMapOf()) { (key, value) ->
            key.name to context.memory.createReference(value.exec(context))
        }, context.memory)
    }

    override fun toString(): String {
        return "{${items.joinToString(", ") { (key, value) -> "$key: $value" }}}"
    }
}

@Serializable
class ClosureNode(private val label: Int) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        return context.memory[Pointer(Memory.Location.STATIC, label)]
    }

    override fun toString(): String {
        return "closure($label)"
    }
}

@Serializable
class ProcedureCallNode(private val func: Node, private val args: ListNode) : Node() {
    override fun exec(context: ExecutionContext): NodeValue {
        val procedure = func.exec(context) as ConvertibleToCallableProcedure
        return procedure.call(context, 0, args.items.map { it.exec(context) })
    }

    override fun toString(): String {
        return "$func($args)"
    }
}