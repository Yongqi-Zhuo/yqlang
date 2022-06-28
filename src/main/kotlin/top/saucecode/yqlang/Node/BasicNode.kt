package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.ImmediateCode
import top.saucecode.yqlang.Runtime.Op
import top.saucecode.yqlang.Runtime.YqlangRuntimeException

class TypeMismatchRuntimeException(expected: List<Class<*>>, got: Any) :
    YqlangRuntimeException("Type mismatch, expected one of ${
        expected.joinToString(", ") { it.simpleName }
    }, got ${got.javaClass.simpleName}")

class IdentifierNode(scope: Scope, val name: String) : Node(scope) {

    constructor(scope: Scope, token: Token) : this(scope, token.value)

    override fun generateCode(buffer: CodegenContext) {
        val nameType = scope.queryName(name)
        if (nameType != NameType.GLOBAL) {
            if (!isLvalue) {
                val index = if (name.startsWith("$")) {
                    name.substring(1).toIntOrNull()
                } else null
                if (index != null) {
                    buffer.add(Op.GET_NTH_ARG, index)
                } else {
                    buffer.add(Op.LOAD_LOCAL_PUSH, scope.getLocalLayout(name))
                }
            } else {
                if (Frame.isReserved(name)) throw CompileException("Cannot assign to reserved name $name")
                buffer.add(Op.POP_SAVE_LOCAL, scope.getLocalLayout(name))
            }
        } else {
            if (!isLvalue) {
                buffer.add(Op.COPY_PUSH, scope.getGlobalLayout(name))
            } else {
                buffer.add(Op.POP_SAVE, scope.getGlobalLayout(name))
            }
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
    fun getMangledName(): String? {
        return scope.getMangledName(name)
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
            buffer.add(Op.COPY_PUSH, addr)
        } else {
            buffer.add(Op.POP_ASSERT_EQ, addr)
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
            buffer.add(Op.COPY_PUSH, addr)
        } else {
            buffer.add(Op.POP_ASSERT_EQ, addr)
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
            buffer.add(Op.COPY_PUSH, addr)
        } else {
            buffer.add(Op.POP_ASSERT_EQ, addr)
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
            buffer.add(Op.PUSH_IMM, constCode.code)
        } else {
            buffer.add(Op.POP_ASSERT_EQ_IMM, constCode.code)
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
            buffer.add(Op.PUSH_IMM, ImmediateCode.NULL.code)
        } else {
            buffer.add(Op.POP_ASSERT_EQ_IMM, ImmediateCode.NULL.code)
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
            buffer.add(Op.CONS_PUSH, items.size)
        } else {
            buffer.add(Op.EXTRACT_LIST, items.size)
            items.forEach { it.generateCode(buffer) }
        }
    }
    override fun testPattern(allBinds: Boolean): Boolean {
        super.testPattern(allBinds)
        return items.all { it.testPattern(allBinds) }
    }

    override fun actuallyRvalue() {
        super.actuallyRvalue()
        items.forEach { it.actuallyRvalue() }
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
        end?.generateCode(buffer)
        buffer.add(Op.SUBSCRIPT_PUSH, subscriptType)
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
            buffer.add(Op.COPY_PUSH, addr)
            value.generateCode(buffer)
        }
        buffer.add(Op.CONS_OBJ_PUSH, items.size)
    }

    override fun toString(): String {
        return "{${items.joinToString(", ") { (key, value) -> "$key: $value" }}}"
    }
}

class ClosureNode(scope: Scope, private val name: String, private val params: ListNode, private val body: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        val entry = buffer.requestLabel()
        val end = buffer.requestLabel()
        buffer.add(Op.JUMP, end)

        buffer.putLabel(entry)
        buffer.add(Op.PREPARE_FRAME, scope.currentFrame.locals.size)
        // now assign the parameters
        buffer.add(Op.LOAD_LOCAL_PUSH, scope.getLocalLayout("\$"))
        params.generateCode(buffer)
        // all set. call the function
        body.generateCode(buffer)
        // pop frame and return
        buffer.add(Op.RETURN)
        buffer.putLabel(end)

        val captures = scope.currentFrame.captures
        captures.forEach {
            buffer.add(Op.LOAD_LOCAL_PUSH, scope.currentFrame.getParentLocalLayout(it))
        }
        buffer.add(Op.CONS_PUSH, captures.size)
        buffer.add(Op.CREATE_CLOSURE, entry)
    }

    override fun toString(): String {
        return "decl($name, $params, $body)"
    }
}

class NamedCallNode(scope: Scope, private val func: String, private val caller: Node, private val args: ListNode) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        caller.generateCode(buffer) // not desired! TODO: pass by reference caller
        val funcType = scope.queryName(func)
        if (funcType != NameType.GLOBAL) {
            buffer.add(Op.LOAD_LOCAL_PUSH, scope.getLocalLayout(func))
        } else {
            buffer.add(Op.COPY_PUSH, scope.getGlobalLayout(func))
        }
        args.generateCode(buffer)
        val ret = buffer.requestLabel()
        buffer.add(Op.CALL, ret)
        buffer.putLabel(ret)
    }

    override fun toString(): String {
        return "call($func, $caller, $args)"
    }
}

// Dynamic calls do not have caller! Only calls such as obj.func(args) have caller, which rules out this case.
class DynamicCallNode(scope: Scope, private val func: Node, private val args: ListNode) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        buffer.add(Op.PUSH_IMM, ImmediateCode.NULL.code)
        func.generateCode(buffer)
        args.generateCode(buffer)
        val ret = buffer.requestLabel()
        buffer.add(Op.CALL, ret)
        buffer.putLabel(ret)
    }

    override fun toString(): String {
        return "invoke($func, $args)"
    }
}