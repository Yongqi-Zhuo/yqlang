package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.Pointer

sealed class AccessNode(scope: Scope, val parent: Node) : Node(scope) {
    override fun exec(context: ExecutionContext): NodeValue {
        TODO("Not yet implemented")
    }
    override fun testPattern(allBinds: Boolean): Boolean = !allBinds
    override fun declarePattern(allBinds: Boolean) {
        if (!testPattern(allBinds)) super.declarePattern(allBinds)
    }
}

class AttributeAccessNode(scope: Scope, parent: Node, val name: String) : AccessNode(scope, parent) {
    // calls DynamicAccessNode to generate code
}

class SubscriptAccessNode(scope: Scope, parent: Node, val subscript: SubscriptNode) : AccessNode(scope, parent) {
    // calls DynamicAccessNode to generate code
}

class IndexOutOfRangeRuntimeException(index: Any?, msg: String? = null) :
    InterpretationRuntimeException("Index${index?.let { " $it" } ?: ""} out of range${msg?.let { ": $it" } ?: ""}")

sealed class AccessView(protected val parent: AccessView?, protected val context: ExecutionContext) {
    enum class AccessState {
        NONE, SLICE, INDEX
    }

    abstract fun exec(): Pointer
    open fun toPattern(): AssignablePattern? = null
    abstract fun subscript(accessor: SubscriptValue): AccessView

    companion object {
        fun create(value: Pointer, parent: AccessView?, context: ExecutionContext): AccessView {
            return when (context.memory[value]) {
                is ListValue -> ListAccessView(value, parent, context)
                is StringValue -> StringAccessView(value, parent, context)
                is ObjectValue -> ObjectAccessView(value, parent, context)
                else -> NonCollectionAccessView(value, parent, context)
            }
        }
    }
}

class NullAccessView(parent: AccessView?, context: ExecutionContext) : AccessView(parent, context) {
    override fun exec() = context.memory.allocate(NullValue)

    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IndexOutOfRangeRuntimeException(accessor, "failed to subscript a nonexistent child of $parent")
}

class NonCollectionAccessView(private val self: Pointer, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    override fun exec() = self
    override fun toPattern(): AssignablePattern? = parent?.toPattern()

    override fun subscript(accessor: SubscriptValue): AccessView {
        if (accessor is KeySubscriptValue) {
            return MethodCallAccessView(accessor.key, this, context)
        } else throw IndexOutOfRangeRuntimeException(accessor, "cannot access a child of a non-collection")
    }
}

class MethodCallAccessView(private val funcName: String, parent: AccessView, context: ExecutionContext) :
    AccessView(parent, context) {
    override fun exec(): Pointer {
        return context.referenceEnvironment.getName(funcName)?.let {
            return context.memory.allocate(BoundProcedureValue(it, parent!!.exec()))
        } ?: throw IndexOutOfRangeRuntimeException(funcName, "$parent has no such method as $funcName")
    }
    override fun toPattern(): AssignablePattern? = parent?.toPattern()

    override fun subscript(accessor: SubscriptValue): AccessView =
        throw IndexOutOfRangeRuntimeException(accessor, "cannot access a child of a method")
}

class ListAccessView(private val list: Pointer, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    private var accessState: AccessState = AccessState.NONE
    private var range: IntRange? = null
    private var index: Int? = null
    override fun exec(): Pointer {
        val storedList = context.memory[list].asList()!!
        return when (accessState) {
            AccessState.NONE -> list
            AccessState.SLICE -> ListValue(storedList.slice(range!!).toMutableList(), context.memory).address!!
            AccessState.INDEX -> storedList[index!!]
        }
    }
    override fun toPattern(): AssignablePattern? {
        val storedList = context.memory[list].asList()!!
        return when (accessState) {
            AccessState.NONE -> parent?.toPattern()
            AccessState.SLICE -> {
                ListAssignablePattern(storedList.subList(range!!.first, range!!.last + 1).map { AddressAssignablePattern(it) })
            }
            AccessState.INDEX -> AddressAssignablePattern(storedList[index!!])
        }
    }

    override fun subscript(accessor: SubscriptValue): AccessView {
        val storedList = context.memory[list].asList()!!
        when (accessor) {
            is KeySubscriptValue -> {
                return MethodCallAccessView(accessor.key, this, context)
            }
            is IntegerSubscriptValue -> {
                if (accessState == AccessState.NONE) {
                    accessState = AccessState.SLICE
                    range = 0 until storedList.size
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
                        create(storedList[index], this, context)
                    } else {
                        NullAccessView(this, context)
                    }
                }
            }
        }
    }
}

class StringAccessView(private val string: Pointer, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    private var accessState: AccessState = AccessState.NONE
    private var range: IntRange? = null
    private var index: Int? = null
    override fun exec(): Pointer {
        val storedString = context.memory[string].asString()!!
        return when (accessState) {
            AccessState.NONE -> string
            AccessState.SLICE -> StringValue(storedString.substring(range!!), context.memory).address!!
            AccessState.INDEX -> StringValue(storedString[index!!].toString(), context.memory).address!!
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
        val storedString = context.memory[string].asString()!!
        when (accessor) {
            is KeySubscriptValue -> {
                return MethodCallAccessView(accessor.key, this, context)
            }
            is IntegerSubscriptValue -> {
                if (accessState == AccessState.NONE) {
                    accessState = AccessState.SLICE
                    range = storedString.indices
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

class ObjectAccessView(private val objectValue: Pointer, parent: AccessView?, context: ExecutionContext) :
    AccessView(parent, context) {
    private var accessed: Boolean = false
    private var key: String? = null
    override fun exec(): Pointer = if (accessed) {
        val storedObject = context.memory[objectValue].asObject()!!
        storedObject[key!!] ?:
            throw IndexOutOfRangeRuntimeException(key, "object $objectValue has no such key as $key")
    } else objectValue
    override fun toPattern(): AssignablePattern? {
        return if (accessed) {
            val storedObject = context.memory[objectValue].asObject()!!
            (storedObject[key!!] ?: context.memory.allocate(NullValue).also {
                storedObject.directSet(key!!, it)
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
                val storedObject = context.memory[objectValue].asObject()!!
                val attr = storedObject[accessor.key]
                if (attr == null) {
                    MethodCallAccessView(accessor.key, this, context)
                } else {
                    create(attr, this, context)
                }
            }
            is IntegerSubscriptValue -> {
                NullAccessView(this, context)
            }
        }
    }
}

class DynamicAccessNode(scope: Scope, private val list: Node, private val subscripts: List<SubscriptNode>) : Node(scope), ConvertibleToAssignablePattern {
    constructor(
        scope: Scope, existing: Node, subscript: SubscriptNode
    ) : this(
        scope,
        if (existing is DynamicAccessNode) existing.list else existing,
        if (existing is DynamicAccessNode) existing.subscripts + subscript else listOf(subscript)
    )

    private fun getAccessor(context: ExecutionContext): AccessView {
        // TODO: distinguishing lvalues should be done at compile time!
        val what = if (list is IdentifierNode) {
            context.referenceEnvironment.getName(list.name)!!
        } else {
            context.memory.createReference(list.exec(context))
        }
        var accessor = AccessView.create(what, null, context)
        for (subscript in subscripts) {
            accessor = accessor.subscript(subscript.exec(context))
        }
        return accessor
    }

    override fun exec(context: ExecutionContext): NodeValue {
        return context.memory[getAccessor(context).exec()]
    }

    override fun toPattern(context: ExecutionContext): AssignablePattern {
        return getAccessor(context).toPattern()
            ?: throw TypeMismatchRuntimeException(listOf(ConstantAssignablePattern::class.java), this)
    }

    override fun toString(): String {
        return "AccessView($list${subscripts.joinToString("") { "[$it]" }})"
    }
}