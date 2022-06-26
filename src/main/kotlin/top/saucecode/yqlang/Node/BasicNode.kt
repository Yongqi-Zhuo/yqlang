package top.saucecode.yqlang.Node

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer

class TypeMismatchRuntimeException(expected: List<Class<*>>, got: Any) :
    InterpretationRuntimeException("Type mismatch, expected one of ${
        expected.joinToString(", ") { it.simpleName }
    }, got ${got.javaClass.simpleName}")

class IdentifierNode(scope: Scope, val name: String) : Node(scope), ConvertibleToAssignablePattern {

    constructor(scope: Scope, token: Token) : this(scope, token.value)

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

class IntegerNode(scope: Scope, private val value: Long) : Node(scope), ConvertibleToAssignablePattern {

    constructor(scope: Scope, token: Token) : this(scope, token.value.toLong())

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

class FloatNode(scope: Scope, private val value: Double) : Node(scope), ConvertibleToAssignablePattern {

    constructor(scope: Scope, token: Token) : this(scope, token.value.toDouble())

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

class StringNode(scope: Scope, private val value: String) : Node(scope), ConvertibleToAssignablePattern {

    constructor(scope: Scope, token: Token) : this(scope, token.value)

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

class ListNode(scope: Scope, val items: List<Node>) : Node(scope), ConvertibleToAssignablePattern {
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

class SubscriptNode(scope: Scope, private val begin: Node, private val extended: Boolean, private val end: Node? = null) : Node(scope) {
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

class ObjectNode(scope: Scope, private val items: List<Pair<IdentifierNode, Node>>) : Node(scope) {
    override fun exec(context: ExecutionContext): ObjectValue {
        return ObjectValue(items.associateTo(mutableMapOf()) { (key, value) ->
            key.name to context.memory.createReference(value.exec(context))
        }, context.memory)
    }

    override fun toString(): String {
        return "{${items.joinToString(", ") { (key, value) -> "$key: $value" }}}"
    }
}

class ClosureNode(scope: Scope, private val label: Int) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        return context.memory[Pointer(Memory.Location.STATIC, label)]
    }

    override fun toString(): String {
        return "closure($label)"
    }
}

class ProcedureCallNode(scope: Scope, private val func: Node, private val args: ListNode) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        val procedure = func.exec(context) as ConvertibleToCallableProcedure
        return procedure.call(context, 0, args.items.map { it.exec(context) })
    }

    override fun toString(): String {
        return "$func($args)"
    }
}