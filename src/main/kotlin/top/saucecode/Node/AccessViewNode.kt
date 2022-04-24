package top.saucecode.Node

import kotlinx.serialization.Serializable
import top.saucecode.ExecutionContext
import top.saucecode.NodeValue.*
import top.saucecode.safeSlice
import top.saucecode.safeSubscript

sealed class AccessView(protected val parent: AccessView?, protected val context: ExecutionContext) {
    enum class AccessState {
        NONE, SLICE, INDEX
    }

    abstract var value: NodeValue
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
    override var value: NodeValue
        get() = NullValue
        set(_) = throw IllegalArgumentException("Failed subscription")

    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IllegalArgumentException("Cannot subscript null")
}

class NonCollectionAccessView(private val self: NodeValue, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    override var value: NodeValue
        get() = self
        set(newValue) {
            parent!!.value = newValue
        }

    override fun subscript(accessor: SubscriptValue): AccessView {
        if (accessor is KeySubscriptValue) {
            return MethodCallAccessView(accessor.key, this, context)
        } else throw IllegalArgumentException("Cannot subscript non-collection")
    }
}

class MethodCallAccessView(private val funcName: String, parent: AccessView, context: ExecutionContext) :
    AccessView(parent, context) {
    override var value: NodeValue
        get() {
            val func = context.stack.getProcedure(funcName)!!.copy()
            return func.bind(parent!!.value)
        }
        set(newValue) {
            parent!!.value = newValue
        }

    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IllegalArgumentException("MethodCall cannot be subscripted")
}

class ListAccessView(private val list: ListValue, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    private var accessState: AccessState = AccessState.NONE
    private var range: IntRange? = null
    private var index: Int? = null
    override var value: NodeValue
        get() = when (accessState) {
            AccessState.NONE -> list
            AccessState.SLICE -> list.value.slice(range!!).toNodeValue()
            AccessState.INDEX -> list[index!!]
        }
        set(value) {
            when (accessState) {
                AccessState.NONE -> parent!!.value = value
                AccessState.SLICE -> {
                    list.value.subList(range!!.first, range!!.last + 1).clear()
                    when (value) {
                        is ListValue -> value.value.reversed().forEach { list.value.add(range!!.first, it) }
                        else -> list.value.add(range!!.first, value)
                    }
                }
                AccessState.INDEX -> list[index!!] = value
            }
        }

    override fun subscript(accessor: SubscriptValue): AccessView {
        when (accessor) {
            is KeySubscriptValue -> {
                return MethodCallAccessView(accessor.key, this, context)
            }
            is NumberSubscriptValue -> {
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
                        create(list[index], this, context)
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
    override var value: NodeValue
        get() = when (accessState) {
            AccessState.NONE -> string
            AccessState.SLICE -> string.value.substring(range!!).toNodeValue()
            AccessState.INDEX -> string.value[index!!].toString().toNodeValue()
        }
        set(value) {
            when (accessState) {
                AccessState.NONE -> parent!!.value = value
                AccessState.SLICE -> {
                    val begin = range!!.first
                    val end = range!!.last + 1
                    val first = if (begin > 0) string.value.substring(0, begin) else ""
                    val second = if (end < string.value.length) string.value.substring(end) else ""
                    parent!!.value = (first + value.toString() + second).toNodeValue()
                }
                AccessState.INDEX -> {
                    val first = if (index!! > 0) string.value.substring(0, index!!) else ""
                    val second = if (index!! + 1 < string.value.length) string.value.substring(index!! + 1) else ""
                    parent!!.value = (first + value.toString() + second).toNodeValue()
                }
            }
        }

    override fun subscript(accessor: SubscriptValue): AccessView {
        when (accessor) {
            is KeySubscriptValue -> {
                return MethodCallAccessView(accessor.key, this, context)
            }
            is NumberSubscriptValue -> {
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
    override var value: NodeValue
        get() = if (accessed) objectValue[key!!]!! else objectValue
        set(value) {
            if (accessed) {
                if (value is ProcedureValue) {
                    objectValue[key!!] = value.copy().bind(objectValue)
                } else {
                    objectValue[key!!] = value
                }
            } else {
                parent!!.value = value
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
                    create(objectValue[accessor.key]!!, this, context)
                }
            }
            is NumberSubscriptValue -> {
                NullAccessView(this, context)
            }
        }
    }
}

@Serializable
class AccessViewNode(private val list: Node, private val subscripts: List<SubscriptNode>) : Node() {
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
        return accessor.value
    }

    override fun assign(context: ExecutionContext, value: NodeValue) {
        var accessor = AccessView.create(list.exec(context), null, context)
        for (subscript in subscripts) {
            accessor = accessor.subscript(subscript.exec(context))
        }
        accessor.value = value
    }

    override fun toString(): String {
        return "AccessView($list${subscripts.joinToString("") { "[$it]" }})"
    }
}