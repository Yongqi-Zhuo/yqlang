package top.saucecode.yqlang

import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.NullValue
import top.saucecode.yqlang.Runtime.*

object UniqueID {
    private var id = 0
    fun get(): Int {
        return id++
    }
}

enum class NameType {
    GLOBAL, CAPTURE, LOCAL, RESERVED, BUILTIN, EXTERNAL
}

open class CompileException(message: String) : YqlangException(message)

class Frame(private val parent: Frame?) {
    private val isRoot: Boolean get() = parent == null
    private val root: Frame get() = if (isRoot) this else parent!!.root
    // captured values from parent frames
    val captures: MutableList<String> = mutableListOf() // empty for root
    // args are treated as special locals to support pattern matching in function prototypes
    // val arguments: MutableList<String> = mutableListOf() // empty for root
    val locals: MutableList<String> = mutableListOf()
    // only handles: global, local, capture
    fun acquireNonSpecialName(mangledName: String): NameType {
        if (isRoot) {
            return if (mangledName in locals) NameType.GLOBAL
            else throw CompileException("Undefined name: $mangledName")
        } else {
            when (mangledName) {
                in locals -> return NameType.LOCAL
                in captures -> return NameType.CAPTURE
                else -> {
                    // need to do capture or find global
                    when (parent!!.acquireNonSpecialName(mangledName)) {
                        NameType.GLOBAL -> return NameType.GLOBAL
                        else -> { // parent has captured this for us
                            captures.add(mangledName)
                        }
                    }
                    return NameType.CAPTURE
                }
            }
        }
    }
    fun declareLocalName(name: String): NameType {
        locals.add(name)
        return if (isRoot) NameType.GLOBAL else NameType.LOCAL
    }
    fun tryGettingReservedArgIndex(name: String): Int? {
        return if (name.startsWith("$")) name.substring(1).toIntOrNull() else null
    }
    fun getReservedMemoryLayout(name: String): Int {
        if (isRoot) throw CompileException("$name not available in global scope!")
        return when (name) {
            "this" -> VirtualMachine.callerOffset
            "$" -> VirtualMachine.argsOffset
            else -> throw CompileException("Do not use getLocalMemoryLayout($name), handle it yourself!")
        }
    }
    fun getLocalMemoryLayout(mangledName: String): Int {
        var offset = captures.indexOf(mangledName)
        if (offset == -1) {
            offset = locals.indexOf(mangledName)
            assert(offset != -1)
            offset += captures.size
        }
        return VirtualMachine.paramsAndCaptureBase + offset
    }
    fun getParentLocalMemoryLayout(name: String): Int {
        return parent!!.getLocalMemoryLayout(name)
    }
    fun getGlobalMemoryLayout(name: String): Int {
        val index = root.locals.indexOf(name)
        return StaticPointer(index)
    }
    fun reserveGlobals(): List<NodeValue> {
        return root.locals.map { NullValue }
    }
    companion object {
        fun isReserved(name: String): Boolean {
            return name == "this" || name.startsWith("$")
        }
        fun isBuiltin(name: String): Boolean {
            return name in BuiltinProcedures.names
        }
        fun isExternal(name: String): Boolean {
            return name in Event.list
        }
    }
}

class Scope(private val parent: Scope?, frame: Frame?) {
    val currentFrame: Frame
    private val currentFrameScope: Scope
    private val locals: MutableMap<String, String> = mutableMapOf()
    init {
        if (frame == null) {
            currentFrame = parent!!.currentFrame
            currentFrameScope = parent.currentFrameScope
        } else {
            currentFrame = frame
            currentFrameScope = this
        }
    }
    private fun acquireNonSpecialName(name: String): Pair<NameType, String>? {
        // possibly global, local, capture. go all the way up the scope tree
        locals[name]?.let { return currentFrame.acquireNonSpecialName(it) to it }
        parent?.acquireNonSpecialName(name)?.let { return it }
        return null
    }
    // returns name type and mangled name
    fun acquireName(name: String, considerBuiltin: Boolean): Pair<NameType, String>? {
        when {
            Frame.isExternal(name) -> return NameType.EXTERNAL to name// external
            Frame.isReserved(name) -> return NameType.RESERVED to name// reserved
        }
        acquireNonSpecialName(name)?.let { return it }
        // only remaining case is builtin
        if (considerBuiltin && Frame.isBuiltin(name)) return NameType.BUILTIN to name
        return null
    }
    // returns mangled name
    fun declareScopeName(name: String): Pair<NameType, String> {
        if (Frame.isExternal(name) || Frame.isReserved(name)) {
            throw CompileException("Cannot declare reserved name $name")
        }
        val mangled = "$name@${UniqueID.get()}"
        locals[name] = mangled
        val nameType = currentFrame.declareLocalName(mangled)
        return nameType to mangled
    }
    // returns mangled name
    fun declareLocalName(name: String): Pair<NameType, String> {
        return currentFrameScope.declareScopeName(name)
    }

    fun exportSymbolTable(): SymbolTable {
        return locals.map { it.key to currentFrame.getGlobalMemoryLayout(it.value) }.toMap().toMutableMap()
    }

}

typealias SymbolTable = MutableMap<String, Pointer>
