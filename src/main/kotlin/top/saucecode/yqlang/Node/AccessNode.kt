package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Op
import top.saucecode.yqlang.Runtime.Pointer
import top.saucecode.yqlang.Runtime.YqlangRuntimeException

sealed class AccessNode(scope: Scope, val parent: Node) : Node(scope) {
    override fun generateCode(buffer: CodegenContext) {
        constructView(buffer)
        if (!isLvalue) {
            buffer.add(Op.ACCESS_GET)
        } else {
            buffer.add(Op.ACCESS_SET)
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
    override fun testPattern(allBinds: Boolean): Boolean {
        super.testPattern(allBinds)
        if (!allBinds) {
//            if (parent !is AccessNode) {
//                parent.actuallyRvalue() // seems not much effect here...
//            } else {
//                parent.testPattern(allBinds)
//            }
            if (parent is AccessNode) return parent.testPattern(allBinds)
            return true
        } else {
            return false
        }
    }
    override fun declarePattern(allBinds: Boolean) {
        if (!testPattern(allBinds)) super.declarePattern(allBinds) // throws
        if (parent is AccessNode) parent.declarePattern(allBinds)
    }
}

class AttributeAccessNode(scope: Scope, parent: Node, val name: String) : AccessNode(scope, parent) {
    override fun constructView(buffer: CodegenContext) {
        super.constructView(buffer)
        StringNode(scope, name).generateCode(buffer)
        buffer.add(Op.EXTEND_ACCESS_VIEW)
    }
}
class SubscriptAccessNode(scope: Scope, parent: Node, val subscript: SubscriptNode) : AccessNode(scope, parent) {
    override fun constructView(buffer: CodegenContext) {
        super.constructView(buffer)
        subscript.generateCode(buffer)
        buffer.add(Op.EXTEND_ACCESS_VIEW)
    }
}

class IndexOutOfRangeRuntimeException(index: Any?, msg: String? = null) :
    YqlangRuntimeException("Index${index?.let { " $it" } ?: ""} out of range${msg?.let { ": $it" } ?: ""}")

sealed class AccessView(protected val parent: AccessView?, protected val memory: Memory) {
    enum class AccessState {
        NONE, SLICE, INDEX
    }
    abstract fun exec(): Pointer
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

class NullAccessView(parent: AccessView?, memory: Memory) : AccessView(parent, memory) {
    override fun exec() = memory.allocate(NullValue)
    override fun assign(src: Pointer) = throw YqlangRuntimeException("Cannot assign to null")
    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IndexOutOfRangeRuntimeException(accessor, "failed to subscript a nonexistent child of $parent")
}

class NonCollectionAccessView(private val self: Pointer, parent: AccessView?, memory: Memory) : AccessView(parent, memory) {
    override fun exec() = memory.copy(self)
    override fun assign(src: Pointer) = parent!!.assign(src)
    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IndexOutOfRangeRuntimeException("failed to subscript a non-collection $self")
}

class ListAccessView(private val list: ListValue, parent: AccessView?, memory: Memory) :
    AccessView(parent, memory) {
    private var accessState: AccessState = AccessState.NONE
    private var range: IntRange? = null
    private var index: Int? = null
    override fun exec(): Pointer {
        return when (accessState) {
            AccessState.NONE -> memory.allocate(list.reference)
            AccessState.SLICE -> memory.allocate(ListValue(list.value.slice(range!!).toMutableList(), memory).reference)
            AccessState.INDEX -> memory.copy(list[index!!])
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
                    newList.value.reversed().forEach { list.value.add(range!!.first, memory.copy(it)) }
                } else {
                    list.value.add(range!!.first, memory.copy(src))
                }
            }
            AccessState.INDEX -> memory.copyTo(list[index!!], src)
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
                        NullAccessView(this, memory)
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
                        NullAccessView(this, memory)
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
    override fun exec(): Pointer {
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
                string.value = first + memory[src].printStr + second
            }
            AccessState.INDEX -> {
                val first = if (index!! > 0) string.value.substring(0, index!!) else ""
                val second = if (index!! + 1 < string.value.length) string.value.substring(index!! + 1) else ""
                string.value = first + memory[src].printStr + second
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
                        NullAccessView(this, memory)
                    }
                } else {
                    val index = range!!.safeSubscript(accessor.begin)
                    return if (index != null) {
                        accessState = AccessState.INDEX
                        this.index = index
                        range = null
                        this
                    } else {
                        NullAccessView(this, memory)
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
    override fun exec(): Pointer = if (accessed) {
        obj[key!!]?.let { memory.copy(it) } ?: memory.allocate(NullValue)
    } else memory.allocate(obj.reference)

    override fun assign(src: Pointer) {
        if (accessed) {
            obj[key!!] = memory[src]
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
                    NullAccessView(this, memory)
                } else {
                    val child = memory[attr]
                    child.asCollection()?.let { create(it, this, memory) } ?: NonCollectionAccessView(attr, this, memory)
                }
            }
            is IntegerSubscriptValue -> {
                NullAccessView(this, memory)
            }
        }
    }
}
