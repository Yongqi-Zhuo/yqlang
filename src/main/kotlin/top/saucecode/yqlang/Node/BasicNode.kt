package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.ImmediateCode
import top.saucecode.yqlang.Runtime.Op
import top.saucecode.yqlang.Runtime.YqlangRuntimeException
import kotlin.reflect.KClass

class TypeMismatchRuntimeException(expected: List<KClass<*>>, got: Any) :
    YqlangRuntimeException("Type mismatch, expected one of ${
        expected.joinToString(", ") { it.simpleName ?: it.toString() }
    }, got ${got.javaClass.simpleName}")

class IdentifierNode(scope: Scope, val name: String) : ExprNode(scope) {
    constructor(scope: Scope, token: Token) : this(scope, token.value)
    override fun generateCode(buffer: CodegenContext) {
        val nameType = scope.queryName(name)
        if (nameType != NameType.GLOBAL) {
            val index = if (name.startsWith("$")) {
                name.substring(1).toIntOrNull()
            } else null
            when (codeGenExprType) {
                CodeGenExprType.PRODUCE_VALUE -> {
                    if (index != null) {
                        buffer.add(Op.GET_NTH_ARG, index)
                    } else {
                        buffer.add(Op.LOAD_LOCAL_PUSH, scope.getLocalLayout(name))
                    }
                }
                CodeGenExprType.PRODUCE_REFERENCE -> {
                    if (index != null) {
                        buffer.add(Op.GET_NTH_ARG_REF, index)
                    } else {
                        buffer.add(Op.LOAD_LOCAL_PUSH_REF, scope.getLocalLayout(name))
                    }
                }
                CodeGenExprType.CONSUME -> {
                    if (Frame.isReserved(name)) throw CompileException("Cannot assign to reserved name $name")
                    buffer.add(Op.POP_SAVE_LOCAL, scope.getLocalLayout(name))
                }
            }
        } else {
            when (codeGenExprType) {
                CodeGenExprType.PRODUCE_VALUE -> {
                    buffer.add(Op.LOAD_PUSH, scope.getGlobalLayout(name)!!)
                }
                CodeGenExprType.PRODUCE_REFERENCE -> {
                    buffer.add(Op.LOAD_PUSH_REF, scope.getGlobalLayout(name)!!)
                }
                CodeGenExprType.CONSUME -> {
                    buffer.add(Op.POP_SAVE, scope.getGlobalLayout(name)!!)
                }
            }
        }
    }
    override fun prepareProduce(isReference: Boolean) {
        scope.acquireExistingName(name)
    }
    override fun prepareConsume(allBinds: Boolean) {
        if (!allBinds && scope.testName(name)) {
            scope.acquireExistingName(name)
        } else {
            scope.declareLocalName(name)
        }
    }
    override fun toString(): String = "id($name)"
}

sealed class ConstantNode(scope: Scope) : ExprNode(scope) {
    protected abstract fun staticValue(buffer: CodegenContext): Int
    protected abstract fun produce(buffer: CodegenContext, staticValue: Int)
    protected abstract fun consume(buffer: CodegenContext, staticValue: Int)
    override fun generateCode(buffer: CodegenContext) {
        val staticValue = staticValue(buffer)
        when (codeGenExprType) {
            CodeGenExprType.PRODUCE_VALUE, CodeGenExprType.PRODUCE_REFERENCE -> {
                produce(buffer, staticValue) // we do not want to modify a constant, so just create a copy
            }
            CodeGenExprType.CONSUME -> {
                consume(buffer, staticValue) // pattern matching
            }
        }
    }
    override fun prepareProduce(isReference: Boolean) {
        // do nothing
    }
    override fun prepareConsume(allBinds: Boolean) {
        // do nothing
    }
}

sealed class NumericNode(scope: Scope) : ConstantNode(scope) {
    override fun produce(buffer: CodegenContext, staticValue: Int) = buffer.add(Op.LOAD_PUSH, staticValue)
    override fun consume(buffer: CodegenContext, staticValue: Int) = buffer.add(Op.POP_ASSERT_EQ, staticValue)
}

class IntegerNode(scope: Scope, private val value: Long) : NumericNode(scope) {
    constructor(scope: Scope, token: Token) : this(scope, token.value.toLong())
    override fun staticValue(buffer: CodegenContext): Int = buffer.addStaticValue(IntegerValue(value))
    override fun toString(): String = "integer($value)"
}

class FloatNode(scope: Scope, private val value: Double) : NumericNode(scope) {
    constructor(scope: Scope, token: Token) : this(scope, token.value.toDouble())
    override fun staticValue(buffer: CodegenContext): Int = buffer.addStaticValue(FloatValue(value))
    override fun toString(): String = "float($value)"
}

class StringNode(scope: Scope, private val value: String) : NumericNode(scope) {
    constructor(scope: Scope, token: Token) : this(scope, token.value)
    override fun staticValue(buffer: CodegenContext): Int = buffer.addStaticString(value)
    override fun toString(): String = "str(\"$value\")"
}

sealed class ImmediateNode(scope: Scope) : ConstantNode(scope) {
    override fun produce(buffer: CodegenContext, staticValue: Int) = buffer.add(Op.PUSH_IMM, staticValue)
    override fun consume(buffer: CodegenContext, staticValue: Int) = buffer.add(Op.POP_ASSERT_EQ_IMM, staticValue)
}

class BooleanNode(scope: Scope, private val value: Boolean) : ImmediateNode(scope) {
    constructor(scope: Scope, token: Token) : this(scope, token.value.toBooleanStrict())
    override fun staticValue(buffer: CodegenContext): Int = if (value) ImmediateCode.TRUE.code else ImmediateCode.FALSE.code
    override fun toString(): String = "boolean($value)"
}

class NullNode(scope: Scope) : ImmediateNode(scope) {
    constructor(scope: Scope, token: Token) : this(scope)
    override fun staticValue(buffer: CodegenContext): Int = ImmediateCode.NULL.code
    override fun toString(): String = "null"
}

class ListNode(scope: Scope, private val items: List<ExprNode>) : ExprNode(scope) {
    override fun generateCode(buffer: CodegenContext) {
        when (codeGenExprType) {
            CodeGenExprType.PRODUCE_VALUE, CodeGenExprType.PRODUCE_REFERENCE -> {
                items.forEach { it.generateCode(buffer) }
                buffer.add(Op.CONS_PUSH, items.size)
            }
            CodeGenExprType.CONSUME -> {
                buffer.add(Op.EXTRACT_LIST, items.size)
                items.forEach { it.generateCode(buffer) }
            }
        }
    }
    override fun prepareProduce(isReference: Boolean) {
        items.forEach { it.declareProduce(false) } // inner needs not to be a reference
    }
    override fun prepareConsume(allBinds: Boolean) {
        items.forEach { it.declareConsume(allBinds) }
    }
    override fun toString(): String = "[${items.joinToString(", ")}]"
}

sealed class ValueExprNode(scope: Scope) : ExprNode(scope) {
    abstract fun prepareProduceValue()
    fun declareProduce() {
        declareProduce(false)
    }
    override fun prepareProduce(isReference: Boolean) {
//        if (isReference) throw ExpressionCannotBeReferencedException(this)
        // actually if it does not want to be a reference, it can be used directly, because ValueExprNode always produces a new value.
        prepareProduceValue()
    }
    override fun prepareConsume(allBinds: Boolean) = throw ExpressionCannotBeAssignedException(this)
}

// subscript type: 0 => not extended, 1 => extended but upper bound is null, 2 => extended and upper bound is not null
// only 2 has 2nd element
class SubscriptNode(scope: Scope, private val begin: ExprNode, private val extended: Boolean, private val end: ExprNode? = null) : ValueExprNode(scope) {
    override fun generateCode(buffer: CodegenContext) {
        val subscriptType = (if (extended) 1 else 0) + (if (end != null) 1 else 0)
        begin.generateCode(buffer)
        end?.generateCode(buffer)
        buffer.add(Op.SUBSCRIPT_PUSH, subscriptType)
    }
    override fun prepareProduceValue() {
        begin.declareProduce(false)
        end?.declareProduce(false)
    }
    override fun toString(): String = if (end != null) "subscript($begin, $end)" else "subscript($begin)"
}

class ObjectNode(scope: Scope, private val items: List<Pair<String, ExprNode>>) : ValueExprNode(scope) {
    override fun generateCode(buffer: CodegenContext) {
        items.forEach { (key, value) ->
            val addr = buffer.addStaticString(key)
            buffer.add(Op.LOAD_PUSH, addr)
            value.generateCode(buffer)
        }
        buffer.add(Op.CONS_OBJ_PUSH, items.size)
    }
    override fun prepareProduceValue() {
        items.forEach { (_, value) ->
            value.declareProduce(false)
        }
    }
    // maybe add pattern matching in the future?
    // override fun prepareConsume(allBinds: Boolean) = throw ExpressionCannotBeAssignedException(this)
    override fun toString(): String = "{${items.joinToString(", ") { (key, value) -> "$key: $value" }}}"
}

class ClosureNode(scope: Scope, private val params: ListNode, private val body: Node) : ValueExprNode(scope) {
    override fun generateCode(buffer: CodegenContext) {
        val entry = buffer.requestLabel()
        val end = buffer.requestLabel()
        buffer.add(Op.JUMP, end)

        buffer.putLabel(entry)
        buffer.add(Op.PREPARE_FRAME, scope.currentFrame.locals.size)
        // now assign the parameters
        buffer.add(Op.LOAD_LOCAL_PUSH_REF, scope.getLocalLayout("\$")) // since list is reference, ref is enough
        params.generateCode(buffer)
        // all set. call the function
        body.generateCode(buffer)
        // pop frame and return
        buffer.add(Op.RETURN)
        buffer.putLabel(end)

        val captures = scope.currentFrame.captures
        captures.forEach {
            // captures must be passed by reference
            buffer.add(Op.LOAD_LOCAL_PUSH_REF, scope.currentFrame.getParentLocalLayout(it))
        }
        buffer.add(Op.CONS_PUSH, captures.size)
        buffer.add(Op.CREATE_CLOSURE, entry)
    }
    override fun prepareProduceValue() {
        params.declareConsume(true)
    }
    override fun toString(): String = "closure($params, $body)"
}

class NamedCallNode(scope: Scope, private val func: String, private val caller: ExprNode, private val args: ListNode) : ValueExprNode(scope) {
    override fun generateCode(buffer: CodegenContext) {
        caller.generateCode(buffer)
        val funcType = scope.queryName(func)
        if (funcType != NameType.GLOBAL) {
            buffer.add(Op.LOAD_LOCAL_PUSH, scope.getLocalLayout(func))
        } else {
            val globalLayout = scope.getGlobalLayout(func) ?: buffer.includeLibrary(func)
            buffer.add(Op.LOAD_PUSH, globalLayout)
        }
        args.generateCode(buffer)
        val ret = buffer.requestLabel()
        buffer.add(Op.CALL, ret)
        buffer.putLabel(ret)
    }
    override fun prepareProduceValue() {
        caller.declareProduce(true) // caller passed by reference
        args.declareProduce(false)
    }
    override fun toString(): String = "call($func, $caller, $args)"
}

// Dynamic calls do not have caller! Only calls such as obj.func(args) have caller, which rules out this case.
class DynamicCallNode(scope: Scope, private val func: ExprNode, private val args: ListNode) : ValueExprNode(scope) {
    override fun generateCode(buffer: CodegenContext) {
        buffer.add(Op.PUSH_IMM, ImmediateCode.NULL.code)
        func.generateCode(buffer)
        args.generateCode(buffer)
        val ret = buffer.requestLabel()
        buffer.add(Op.CALL, ret)
        buffer.putLabel(ret)
    }
    override fun prepareProduceValue() {
        func.declareProduce(false)
        args.declareProduce(false)
    }
    override fun toString(): String {
        return "invoke($func, $args)"
    }
}