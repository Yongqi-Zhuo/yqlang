package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.*

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

    override fun testPattern(allBinds: Boolean): Boolean = true
    override fun declarePattern(allBinds: Boolean) {
        if (!allBinds && scope.testName(name)) {
            scope.acquireExistingName(name)
        } else {
            scope.declareLocalName(name)
        }
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

    override fun testPattern(allBinds: Boolean): Boolean = true
    override fun declarePattern(allBinds: Boolean) {
        return
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

    override fun testPattern(allBinds: Boolean): Boolean = true
    override fun declarePattern(allBinds: Boolean) {
        return
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
        return StringValue(value, context.memory).reference
    }

    override fun testPattern(allBinds: Boolean): Boolean = true
    override fun declarePattern(allBinds: Boolean) {
        return
    }

    override fun toPattern(context: ExecutionContext): ConstantAssignablePattern {
        return ConstantAssignablePattern(exec(context))
    }

    override fun toString(): String {
        return "str(\"$value\")"
    }
}

class ListNode(scope: Scope, val items: List<Node>) : Node(scope), ConvertibleToAssignablePattern {
    override fun exec(context: ExecutionContext): NodeValue {
        // create new instance on heap
        return ListValue(items.mapTo(mutableListOf()) {
            context.memory.allocate(it.exec(context))
        }, context.memory).reference
    }

    override fun testPattern(allBinds: Boolean): Boolean = items.all { it.testPattern(allBinds) }
    override fun declarePattern(allBinds: Boolean) = items.forEach { it.declarePattern(allBinds) }

    override fun toPattern(context: ExecutionContext): AssignablePattern {
        return ListAssignablePattern(items.map { node ->
            if (node is ConvertibleToAssignablePattern) {
                node.toPattern(context)
            } else {
                throw TypeMismatchRuntimeException(listOf(ConvertibleToAssignablePattern::class.java), node)
            }
        })
    }

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

class ObjectNode(scope: Scope, private val items: List<Pair<String, Node>>) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        return ObjectValue(items.associateTo(mutableMapOf()) { (key, value) ->
            key to context.memory.allocate(value.exec(context))
        }, context.memory).reference
    }

    override fun toString(): String {
        return "{${items.joinToString(", ") { (key, value) -> "$key: $value" }}}"
    }
}

class BooleanNode(scope: Scope, private val value: Boolean) : Node(scope) {
    constructor(scope: Scope, token: Token) : this(scope, token.value.toBooleanStrict())
    override fun exec(context: ExecutionContext): NodeValue {
        return BooleanValue(value)
    }
    override fun toString(): String {
        return "true"
    }
}
class NullNode(scope: Scope) : Node(scope) {
    constructor(scope: Scope, token: Token) : this(scope)
    override fun exec(context: ExecutionContext): NodeValue {
        return NullValue
    }
    override fun toString(): String {
        return "null"
    }
}

class ClosureNode(scope: Scope, private val name: String, private val params: ListNode, private val body: Node) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        TODO("not implemented")
    }

    override fun toString(): String {
        return "decl($name, $params, $body)"
    }
}

// TODO: add NullNode to represent null self
class NamedCallNode(scope: Scope, private val func: String, private val caller: Node, private val args: ListNode) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        TODO("not implemented")
    }

    override fun toString(): String {
        return "call($func, $caller, $args)"
    }
}

// TODO: make sure the func produces a BoundClosureValue(caller: Pointer, label: Int)
class DynamicCallNode(scope: Scope, private val func: Node, private val args: ListNode) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        TODO("not implemented")
    }

    override fun toString(): String {
        return "invoke($func, $args)"
    }
}