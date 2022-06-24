package top.saucecode.yqlang.Node

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.InterpretationRuntimeException
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.safeSlice
import top.saucecode.yqlang.safeSubscript

class IndexOutOfRangeRuntimeException(index: Any?, msg: String? = null) :
    InterpretationRuntimeException("Index${index?.let { " $it" } ?: ""} out of range${msg?.let { ": $it" } ?: ""}")

sealed class AccessView(protected val parent: AccessView?, protected val context: ExecutionContext) {
    enum class AccessState {
        NONE, SLICE, INDEX
    }

    abstract fun exec(): NodeValue
    open fun toPattern(): AssignablePattern? = null
    abstract fun subscript(accessor: SubscriptValue): AccessView

    companion object {
        fun create(value: NodeValue, parent: AccessView?, context: ExecutionContext): AccessView {
            return when (value) {
                is ListValue -> ListAccessView(value, parent, context)
                is StringValue -> StringAccessView(value, parent, context)
                is ObjectValue -> ObjectAccessView(value, parent, context)
                else -> NonCollectionAccessView(value, parent, context)
            }
        }
    }
}

class NullAccessView(parent: AccessView?, context: ExecutionContext) : AccessView(parent, context) {
    override fun exec(): NodeValue = NullValue

    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IndexOutOfRangeRuntimeException(accessor, "failed to subscript a nonexistent child of $parent")
}

class NonCollectionAccessView(private val self: NodeValue, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    override fun exec(): NodeValue = self
    override fun toPattern(): AssignablePattern? = parent?.toPattern()

    override fun subscript(accessor: SubscriptValue): AccessView {
        if (accessor is KeySubscriptValue) {
            return MethodCallAccessView(accessor.key, this, context)
        } else throw IndexOutOfRangeRuntimeException(accessor, "cannot access a child of a non-collection")
    }
}

class MethodCallAccessView(private val funcName: String, parent: AccessView, context: ExecutionContext) :
    AccessView(parent, context) {
    override fun exec(): NodeValue {
        return context.referenceEnvironment.getName(funcName)?.let {
            return BoundProcedureValue(it, context.memory.createReference(parent!!.exec()))
        } ?: throw IndexOutOfRangeRuntimeException(funcName, "$parent has no such method as $funcName")
    }
    override fun toPattern(): AssignablePattern? = parent?.toPattern()

    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IndexOutOfRangeRuntimeException(accessor, "cannot access a child of a method")
}

class ListAccessView(private val list: ListValue, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    private var accessState: AccessState = AccessState.NONE
    private var range: IntRange? = null
    private var index: Int? = null
    override fun exec(): NodeValue {
        return when (accessState) {
            AccessState.NONE -> list
            AccessState.SLICE -> ListValue(list.value.slice(range!!).toMutableList()).apply { solidify(context.memory) }
            AccessState.INDEX -> context.memory[list[index!!]]
        }
    }
    override fun toPattern(): AssignablePattern? {
        return when (accessState) {
            AccessState.NONE -> parent?.toPattern()
            AccessState.SLICE -> {
                ListAssignablePattern(list.value.subList(range!!.first, range!!.last + 1).map { AddressAssignablePattern(it) })
            }
            AccessState.INDEX -> AddressAssignablePattern(list[index!!])
        }
    }

    override fun subscript(accessor: SubscriptValue): AccessView {
        when (accessor) {
            is KeySubscriptValue -> {
                return MethodCallAccessView(accessor.key, this, context)
            }
            is IntegerSubscriptValue -> {
                if (accessState == AccessState.NONE) {
                    accessState = AccessState.SLICE
                    range = 0 until list.size
                }
                if (accessor.extended) {
                    val slice = range!!.safeSlice(accessor.begin, accessor.end)
                    return if (slice != null) {
                        range = slice
                        this
                    } else {
                        NullAccessView(this, context)
                    }
                } else {
                    val index = range!!.safeSubscript(accessor.begin)
                    return if (index != null) {
                        accessState = AccessState.INDEX
                        range = null
                        this.index = index
                        create(context.memory[list[index]], this, context)
                    } else {
                        NullAccessView(this, context)
                    }
                }
            }
        }
    }
}

class StringAccessView(private val string: StringValue, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    private var accessState: AccessState = AccessState.NONE
    private var range: IntRange? = null
    private var index: Int? = null
    override fun exec(): NodeValue {
        return when (accessState) {
            AccessState.NONE -> string
            AccessState.SLICE -> StringValue(string.value.substring(range!!)).apply { solidify(context.memory) }
            AccessState.INDEX -> StringValue(string.value[index!!].toString()).apply { solidify(context.memory) }
        }
    }
    override fun toPattern(): AssignablePattern? {
        return when (accessState) {
            AccessState.NONE -> parent?.toPattern()
            AccessState.SLICE -> {
                return StringAssignablePattern(string, range!!.first, range!!.last + 1)
            }
            AccessState.INDEX -> {
                return StringAssignablePattern(string, index!!, index!! + 1)
            }
        }
    }

    override fun subscript(accessor: SubscriptValue): AccessView {
        when (accessor) {
            is KeySubscriptValue -> {
                return MethodCallAccessView(accessor.key, this, context)
            }
            is IntegerSubscriptValue -> {
                if (accessState == AccessState.NONE) {
                    accessState = AccessState.SLICE
                    range = 0 until string.value.length
                }
                if (accessor.extended) {
                    val slice = range!!.safeSlice(accessor.begin, accessor.end)
                    return if (slice != null) {
                        range = slice
                        this
                    } else {
                        NullAccessView(this, context)
                    }
                } else {
                    val index = range!!.safeSubscript(accessor.begin)
                    return if (index != null) {
                        accessState = AccessState.INDEX
                        this.index = index
                        range = null
                        this
                    } else {
                        NullAccessView(this, context)
                    }
                }
            }
        }
    }
}

class ObjectAccessView(private val objectValue: ObjectValue, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    private var accessed: Boolean = false
    private var key: String? = null
    override fun exec(): NodeValue = if (accessed) {
        objectValue[key!!]?.let { context.memory[it] } ?:
            throw IndexOutOfRangeRuntimeException(key, "object $objectValue has no such key as $key")
    } else objectValue
    override fun toPattern(): AssignablePattern? {
        return if (accessed) {
            (objectValue[key!!] ?: context.memory.allocate(NullValue).also {
                objectValue.directSet(key!!, it)
            }).let { AddressAssignablePattern(it) }
        } else {
            parent?.toPattern()
        }
    }

    override fun subscript(accessor: SubscriptValue): AccessView {
        return when (accessor) {
            is KeySubscriptValue -> {
                if (!accessed) accessed = true
                key = accessor.key
                if (objectValue[accessor.key] == null) {
                    MethodCallAccessView(accessor.key, this, context)
                } else {
                    create(context.memory[objectValue[accessor.key]!!], this, context)
                }
            }
            is IntegerSubscriptValue -> {
                NullAccessView(this, context)
            }
        }
    }
}

@Serializable
class AccessViewNode(private val list: Node, private val subscripts: List<SubscriptNode>) : Node(), ConvertibleToAssignablePattern {
    constructor(
        existing: Node, subscript: SubscriptNode
    ) : this(
        if (existing is AccessViewNode) existing.list else existing,
        if (existing is AccessViewNode) existing.subscripts + subscript else listOf(subscript)
    )

    override fun exec(context: ExecutionContext): NodeValue {
        var accessor = AccessView.create(list.exec(context), null, context)
        for (subscript in subscripts) {
            accessor = accessor.subscript(subscript.exec(context))
        }
        return accessor.exec()
    }

    override fun toPattern(context: ExecutionContext): AssignablePattern {
        var accessor = AccessView.create(list.exec(context), null, context)
        for (subscript in subscripts) {
            accessor = accessor.subscript(subscript.exec(context))
        }
        return accessor.toPattern() ?: throw TypeMismatchRuntimeException(listOf(ConstantAssignablePattern::class.java), this)
    }

    override fun toString(): String {
        return "AccessView($list${subscripts.joinToString("") { "[$it]" }})"
    }
}