package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.ByteCode
import top.saucecode.yqlang.Runtime.ImmediateCode
import top.saucecode.yqlang.Runtime.Op

class TypeMismatchRuntimeException(expected: List<Class<*>>, got: Any) :
    InterpretationRuntimeException("Type mismatch, expected one of ${
        expected.joinToString(", ") { it.simpleName }
    }, got ${got.javaClass.simpleName}")

class IdentifierNode(scope: Scope, val name: String) : Node(scope) {

    constructor(scope: Scope, token: Token) : this(scope, token.value)

    override fun generateCode(buffer: CodegenContext) {
        if (!isLvalue) {
            buffer.add(ByteCode(Op.LOAD_LOCAL_PUSH.code, scope.getMemoryLayout(name)))
        } else {
            buffer.add(ByteCode(Op.POP_SAVE_LOCAL.code, scope.getMemoryLayout(name)))
        }
    }
    override fun testPattern(allBinds: Boolean): Boolean {
        super.testPattern(allBinds)
        return true
    }
    override fun declarePattern(allBinds: Boolean) {
        if (!allBinds && scope.testName(name)) {
            scope.acquireExistingName(name)
        } else {
            scope.declareLocalName(name)
        }
    }

    override fun toString(): String {
        return "id($name)"
    }

}

class IntegerNode(scope: Scope, private val value: Long) : Node(scope) {

    constructor(scope: Scope, token: Token) : this(scope, token.value.toLong())

    override fun generateCode(buffer: CodegenContext) {
        val addr = buffer.addStaticValue(IntegerValue(value))
        if (!isLvalue) {
            buffer.add(ByteCode(Op.COPY_PUSH.code, addr))
        } else {
            buffer.add(ByteCode(Op.POP_ASSERT_EQ.code, addr))
        }
    }
    override fun testPattern(allBinds: Boolean): Boolean {
        super.testPattern(allBinds)
        return true
    }
    override fun declarePattern(allBinds: Boolean) {
        return
    }

    override fun toString(): String {
        return "integer($value)"
    }
}

class FloatNode(scope: Scope, private val value: Double) : Node(scope) {

    constructor(scope: Scope, token: Token) : this(scope, token.value.toDouble())

    override fun generateCode(buffer: CodegenContext) {
        val addr = buffer.addStaticValue(FloatValue(value))
        if (!isLvalue) {
            buffer.add(ByteCode(Op.COPY_PUSH.code, addr))
        } else {
            buffer.add(ByteCode(Op.POP_ASSERT_EQ.code, addr))
        }
    }
    override fun testPattern(allBinds: Boolean): Boolean {
        super.testPattern(allBinds)
        return true
    }
    override fun declarePattern(allBinds: Boolean) {
        return
    }

    override fun toString(): String {
        return "float($value)"
    }
}

class StringNode(scope: Scope, private val value: String) : Node(scope) {

    constructor(scope: Scope, token: Token) : this(scope, token.value)

    override fun generateCode(buffer: CodegenContext) {
        val addr = buffer.addStaticString(value)
        if (!isLvalue) {
            buffer.add(ByteCode(Op.COPY_PUSH.code, addr))
        } else {
            buffer.add(ByteCode(Op.POP_ASSERT_EQ.code, addr))
        }
    }
    override fun testPattern(allBinds: Boolean): Boolean {
        super.testPattern(allBinds)
        return true
    }
    override fun declarePattern(allBinds: Boolean) {
        return
    }

    override fun toString(): String {
        return "str(\"$value\")"
    }
}

class BooleanNode(scope: Scope, private val value: Boolean) : Node(scope) {
    constructor(scope: Scope, token: Token) : this(scope, token.value.toBooleanStrict())
    override fun generateCode(buffer: CodegenContext) {
        val constCode = if (value) ImmediateCode.TRUE else ImmediateCode.FALSE
        if (!isLvalue) {
            buffer.add(ByteCode(Op.PUSH_IMM.code, constCode.code))
        } else {
            buffer.add(ByteCode(Op.POP_ASSERT_EQ_IMM.code, constCode.code))
        }
    }
    override fun testPattern(allBinds: Boolean): Boolean {
        super.testPattern(allBinds)
        return true
    }
    override fun declarePattern(allBinds: Boolean) {
        return
    }
    override fun toString(): String {
        return "$value"
    }
}

class NullNode(scope: Scope) : Node(scope) {
    constructor(scope: Scope, token: Token) : this(scope)
    override fun generateCode(buffer: CodegenContext) {
        if (!isLvalue) {
            buffer.add(ByteCode(Op.PUSH_IMM.code, ImmediateCode.NULL.code))
        } else {
            buffer.add(ByteCode(Op.POP_ASSERT_EQ_IMM.code, ImmediateCode.NULL.code))
        }
    }
    override fun testPattern(allBinds: Boolean): Boolean {
        super.testPattern(allBinds)
        return true
    }
    override fun declarePattern(allBinds: Boolean) {
        return
    }
    override fun toString(): String {
        return "null"
    }
}

class ListNode(scope: Scope, val items: List<Node>) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        if (!isLvalue) {
            items.forEach { it.generateCode(buffer) }
            buffer.add(ByteCode(Op.CONS_PUSH.code, items.size))
        } else {
            buffer.add(ByteCode(Op.EXTRACT_LIST.code, items.size))
            items.forEach { it.generateCode(buffer) }
        }
    }
    override fun testPattern(allBinds: Boolean): Boolean {
        super.testPattern(allBinds)
        return items.all { it.testPattern(allBinds) }
    }
    override fun declarePattern(allBinds: Boolean) = items.forEach { it.declarePattern(allBinds) }

    override fun toString(): String {
        return "[${items.joinToString(", ")}]"
    }
}

// subscript type: 0 => not extended, 1 => extended but upper bound is null, 2 => extended and upper bound is not null
// only 2 has 2nd element
class SubscriptNode(scope: Scope, private val begin: Node, private val extended: Boolean, private val end: Node? = null) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        val subscriptType = (if (extended) 1 else 0) + (if (end != null) 1 else 0)
        begin.generateCode(buffer)
        if (end != null) end.generateCode(buffer)
        buffer.add(ByteCode(Op.SUBSCRIPT_PUSH.code, subscriptType))
    }

    override fun toString(): String {
        return if (end != null) "subscript($begin, $end)" else "subscript($begin)"
    }
}

class ObjectNode(scope: Scope, private val items: List<Pair<String, Node>>) : Node(scope) {
    // maybe add pattern matching in the future?
    override fun generateCode(buffer: CodegenContext) {
        items.forEach { (key, value) ->
            val addr = buffer.addStaticString(key)
            buffer.add(ByteCode(Op.COPY_PUSH.code, addr))
            value.generateCode(buffer)
        }
        buffer.add(ByteCode(Op.CONS_OBJ_PUSH.code, items.size))
    }

    override fun toString(): String {
        return "{${items.joinToString(", ") { (key, value) -> "$key: $value" }}}"
    }
}

class ClosureNode(scope: Scope, private val name: String, private val params: ListNode, private val body: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        TODO("not implemented")
    }

    override fun toString(): String {
        return "decl($name, $params, $body)"
    }
}

class NamedCallNode(scope: Scope, private val func: String, private val caller: Node, private val args: ListNode) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        TODO("not implemented")
    }

    override fun toString(): String {
        return "call($func, $caller, $args)"
    }
}

// TODO: make sure the func produces a BoundClosureValue(caller: Pointer, label: Int)
class DynamicCallNode(scope: Scope, private val func: Node, private val args: ListNode) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        TODO("not implemented")
    }

    override fun toString(): String {
        return "invoke($func, $args)"
    }
}