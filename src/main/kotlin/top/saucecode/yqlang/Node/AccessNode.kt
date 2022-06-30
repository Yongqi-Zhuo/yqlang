package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Op
import top.saucecode.yqlang.Runtime.Pointer
import top.saucecode.yqlang.Runtime.YqlangRuntimeException

sealed class AccessNode(scope: Scope, val parent: ExprNode) : ExprNode(scope) {
    override fun generateCode(buffer: CodegenContext) {
        constructView(buffer)
        when (codeGenExprType) {
            CodeGenExprType.PRODUCE_VALUE -> {
                buffer.add(Op.ACCESS_GET)
            }
            CodeGenExprType.PRODUCE_REFERENCE -> {
                buffer.add(Op.ACCESS_GET_REF)
            }
            CodeGenExprType.CONSUME -> {
                buffer.add(Op.ACCESS_SET)
            }
        }
    }
    open fun constructView(buffer: CodegenContext) {
        if (parent !is AccessNode) {
            parent.generateCode(buffer)
            buffer.add(Op.PUSH_ACCESS_VIEW)
        } else {
            parent.constructView(buffer)
        }
    }
    override fun prepareProduce(isReference: Boolean) {
        parent.declareProduce(true) // need to access itself, not its copy!
    }
    override fun prepareConsume(allBinds: Boolean) {
        if (allBinds) throw CompileException("Cannot bind a value to an access node.")
        if (parent !is AccessNode) {
            parent.declareProduce(true)
        } else {
            parent.declareConsume(false)
        }
    }
}

class AttributeAccessNode(scope: Scope, parent: ExprNode, val name: String) : AccessNode(scope, parent) {
    override fun constructView(buffer: CodegenContext) {
        super.constructView(buffer)
        StringNode(scope, name).generateCode(buffer)
        buffer.add(Op.EXTEND_ACCESS_VIEW)
    }
    // no need to override prepare, because attribute access does not capture
}
class SubscriptAccessNode(scope: Scope, parent: ExprNode, private val subscript: SubscriptNode) : AccessNode(scope, parent) {
    override fun constructView(buffer: CodegenContext) {
        super.constructView(buffer)
        subscript.generateCode(buffer)
        buffer.add(Op.EXTEND_ACCESS_VIEW)
    }
    override fun prepareProduce(isReference: Boolean) {
        super.prepareProduce(isReference)
        subscript.declareProduce(false)
    }
    override fun prepareConsume(allBinds: Boolean) {
        super.prepareConsume(allBinds)
        subscript.declareProduce(false)
    }
}

class IndexOutOfRangeRuntimeException(index: Any?, msg: String? = null) :
    YqlangRuntimeException("Index${index?.let { " $it" } ?: ""} out of range${msg?.let { ": $it" } ?: ""}")

sealed class AccessView(protected val parent: AccessView?, protected val memory: Memory) {
    enum class AccessState {
        NONE, SLICE, INDEX
    }
    abstract fun exec(isReference: Boolean): Pointer
    abstract fun assign(src: Pointer)
    abstract fun subscript(accessor: SubscriptValue): AccessView

    companion object {
        fun create(value: CollectionValue, parent: AccessView?, memory: Memory): AccessView {
            return when (value) {
                is ListValue -> ListAccessView(value, parent, memory)
                is StringValue -> StringAccessView(value, parent, memory)
                is ObjectValue -> ObjectAccessView(value, parent, memory)
            }
        }
    }
}

class NullAccessView(private val nullType: NullType, parent: AccessView?, memory: Memory) : AccessView(parent, memory) {
    enum class NullType {
        NULL, EMPTY_LIST, EMPTY_STRING
    }
    override fun exec(isReference: Boolean): Pointer {
        return when (nullType) {
            NullType.NULL -> memory.allocate(NullValue)
            NullType.EMPTY_LIST -> memory.allocate(ListValue(mutableListOf(), memory).reference)
            NullType.EMPTY_STRING -> memory.allocate(StringValue("", memory).reference)
        }
    }
    override fun assign(src: Pointer) = throw YqlangRuntimeException("Cannot assign to null")
    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IndexOutOfRangeRuntimeException(accessor, "failed to subscript a nonexistent child of $parent")
}

class NonCollectionAccessView(private val self: Pointer, parent: AccessView?, memory: Memory) : AccessView(parent, memory) {
    override fun exec(isReference: Boolean) = if (isReference) self else memory.copy(self)
    override fun assign(src: Pointer) = parent!!.assign(src)
    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IndexOutOfRangeRuntimeException("failed to subscript a non-collection $self")
}

class ListAccessView(private val list: ListValue, parent: AccessView?, memory: Memory) :
    AccessView(parent, memory) {
    private var accessState: AccessState = AccessState.NONE
    private var range: IntRange? = null
    private var index: Int? = null
    override fun exec(isReference: Boolean): Pointer {
        return when (accessState) {
            AccessState.NONE -> memory.allocate(list.reference) // no need to copy because it is a reference
            AccessState.SLICE -> {
                // creates a new list anyway
                memory.allocate(ListValue(list.value.slice(range!!).mapTo(mutableListOf()) {
                    memory.copy(it) // copy the elements by value!
                }, memory).reference)
            }
            AccessState.INDEX -> list[index!!].let { if (isReference) it else memory.copy(it) }
        }
    }

    override fun assign(src: Pointer) {
        when (accessState) {
            AccessState.NONE -> parent!!.assign(src)
            AccessState.SLICE -> {
                list.value.subList(range!!.first, range!!.last + 1).clear()
                val srcList = memory[src]
                val newList = srcList.asList()
                if (newList != null) {
                    newList.value.reversed().forEach { list.value.add(range!!.first, memory.copy(it)) } // copy by value
                } else {
                    list.value.add(range!!.first, src) // src is passed by value, don't copy twice
                }
            }
            AccessState.INDEX -> list[index!!] = src // same reason
        }
    }

    override fun subscript(accessor: SubscriptValue): AccessView {
        when (accessor) {
            is KeySubscriptValue -> {
                throw IndexOutOfRangeRuntimeException(accessor, "cannot access a named attribute of a list")
            }
            is IntegerSubscriptValue -> {
                if (accessState == AccessState.NONE) {
                    accessState = AccessState.SLICE
                    range = 0 until list.value.size
                }
                if (accessor.extended) {
                    val slice = range!!.safeSlice(accessor.begin, accessor.end)
                    return if (slice != null) {
                        range = slice
                        this
                    } else {
                        NullAccessView(NullAccessView.NullType.EMPTY_LIST, this, memory)
                    }
                } else {
                    val index = range!!.safeSubscript(accessor.begin)
                    return if (index != null) {
                        accessState = AccessState.INDEX
                        range = null
                        this.index = index
                        val child = memory[list[index]]
                        child.asCollection()?.let { create(it, this, memory) } ?: NonCollectionAccessView(list[index], this, memory)
                    } else {
                        NullAccessView(NullAccessView.NullType.NULL, this, memory)
                    }
                }
            }
        }
    }
}

class StringAccessView(private val string: StringValue, parent: AccessView?, memory: Memory) :
    AccessView(parent, memory) {
    private var accessState: AccessState = AccessState.NONE
    private var range: IntRange? = null
    private var index: Int? = null
    override fun exec(isReference: Boolean): Pointer {
        return when (accessState) {
            AccessState.NONE -> memory.allocate(string.reference)
            AccessState.SLICE -> memory.allocate(StringValue(string.value.substring(range!!), memory).reference)
            AccessState.INDEX -> memory.allocate(StringValue(string.value[index!!].toString(), memory).reference)
        }
    }

    override fun assign(src: Pointer) {
        when (accessState) {
            AccessState.NONE -> parent!!.assign(src)
            AccessState.SLICE -> {
                val begin = range!!.first
                val end = range!!.last + 1
                val first = if (begin > 0) string.value.substring(0, begin) else ""
                val second = if (end < string.value.length) string.value.substring(end) else ""
                string.value = first + memory[src].printStr(0) + second
            }
            AccessState.INDEX -> {
                val first = if (index!! > 0) string.value.substring(0, index!!) else ""
                val second = if (index!! + 1 < string.value.length) string.value.substring(index!! + 1) else ""
                string.value = first + memory[src].printStr(0) + second
            }
        }
    }

    override fun subscript(accessor: SubscriptValue): AccessView {
        when (accessor) {
            is KeySubscriptValue -> {
                throw IndexOutOfRangeRuntimeException(accessor, "cannot access a named attribute of a string")
            }
            is IntegerSubscriptValue -> {
                if (accessState == AccessState.NONE) {
                    accessState = AccessState.SLICE
                    range = string.value.indices
                }
                if (accessor.extended) {
                    val slice = range!!.safeSlice(accessor.begin, accessor.end)
                    return if (slice != null) {
                        range = slice
                        this
                    } else {
                        NullAccessView(NullAccessView.NullType.EMPTY_STRING, this, memory)
                    }
                } else {
                    val index = range!!.safeSubscript(accessor.begin)
                    return if (index != null) {
                        accessState = AccessState.INDEX
                        this.index = index
                        range = null
                        this
                    } else {
                        NullAccessView(NullAccessView.NullType.NULL, this, memory)
                    }
                }
            }
        }
    }
}

class ObjectAccessView(private val obj: ObjectValue, parent: AccessView?, memory: Memory) :
    AccessView(parent, memory) {
    private var accessed: Boolean = false
    private var key: String? = null
    override fun exec(isReference: Boolean): Pointer = if (accessed) {
        obj[key!!]?.let { if (isReference) it else memory.copy(it) } ?: memory.allocate(NullValue)
    } else memory.allocate(obj.reference)

    override fun assign(src: Pointer) {
        if (accessed) {
            obj[key!!] = src
        } else {
            parent!!.assign(src)
        }
    }

    override fun subscript(accessor: SubscriptValue): AccessView {
        return when (accessor) {
            is KeySubscriptValue -> {
                if (!accessed) accessed = true
                key = accessor.key
                val attr = obj[accessor.key]
                if (attr == null) {
                    NullAccessView(NullAccessView.NullType.NULL, this, memory)
                } else {
                    val child = memory[attr]
                    child.asCollection()?.let { create(it, this, memory) } ?: NonCollectionAccessView(attr, this, memory)
                }
            }
            is IntegerSubscriptValue -> {
                throw IndexOutOfRangeRuntimeException(accessor, "cannot access an indexed attribute of an object")
            }
        }
    }
}
