package top.saucecode.yqlang

import top.saucecode.yqlang.Node.IdentifierNode
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.NodeValue.NullValue
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer
import top.saucecode.yqlang.Runtime.StaticPointer

object UniqueID {
    var id = 0
    fun get(): Int {
        return id++
    }
}

enum class NameType {
    GLOBAL, CAPTURE, LOCAL
}
class Frame(val parent: Frame?, val name: String) {
    val isRoot: Boolean get() = parent == null
    val root: Frame get() = if (isRoot) this else parent!!.root
    // captured values from parent frames
    val captures: MutableList<String> = mutableListOf() // empty for root
    // variables that need to be forwarded to next frame
    val forwards: MutableMap<String, MutableList<String>> = mutableMapOf() // empty for root, because root vars are global
    // args are treated as special locals to support pattern matching in function prototypes
    // val arguments: MutableList<String> = mutableListOf() // empty for root
    val locals: MutableList<String> = mutableListOf()
    fun acquireName(name: String): NameType? {
        if (isReserved(name)) return NameType.LOCAL
        if (isRoot) {
            return if (name in locals || isBuiltin(name)) NameType.GLOBAL
            else null
        } else {
            when (name) {
                in locals -> return NameType.LOCAL
                in captures -> return NameType.CAPTURE
                else -> {
                    // need to do capture or find global
                    val res = parent!!.acquireName(name) ?: return null
                    when (res) {
                        NameType.GLOBAL -> return NameType.GLOBAL
                        else -> { // parent has captured this for us
                            parent.forwards.getOrPut(this.name) { mutableListOf() }.add(name)
                            captures.add(name)
                        }
                    }
                    return NameType.CAPTURE
                }
            }
        }
    }
    fun declareLocalName(name: String) {
        locals.add(name)
    }
    fun getLocalMemoryLayout(name: String): Int {
        if (isReserved(name)) {
            return when (name) {
                "this" -> Memory.callerOffset
                "$" -> Memory.argsOffset
                else -> throw CompileException("Do not use getLocalMemoryLayout($name), handle it yourself!")
            }
        }
        var offset = captures.indexOf(name)
        if (offset == -1) {
            offset = locals.indexOf(name)
            assert(offset != -1)
            offset += captures.size
        }
        return Memory.paramsAndCaptureBase + offset
    }
    fun getParentLocalLayout(name: String): Int {
        return parent!!.getLocalMemoryLayout(name)
    }
    companion object {
        fun isReserved(name: String): Boolean {
            return name == "this" || name.startsWith("$")
        }
        fun isBuiltin(name: String): Boolean {
            return name in Constants.builtinProcedures.keys
        }
    }
    fun reserveGlobals(): List<NodeValue> {
        // TODO: add builtins to globals
        // return Constants.builtinProcedures.values() + root.locals.map { NullValue }
        return root.locals.map { NullValue }
    }
    fun getGlobalMemoryLayout(name: String): Int {
        return StaticPointer(root.locals.indexOf(name))
    }
}

class CompileException(message: String) : YqlangException(message)

class Scope(val parent: Scope?, frame: Frame?) {
    val currentFrame: Frame
    val currentFrameScope: Scope
    val locals: MutableMap<String, String> = mutableMapOf()
    private var captureNamesField: Boolean? = null
    val captureNames: Boolean get() = captureNamesField ?: (parent?.captureNames ?: false)
    var tracedIdentifiers: MutableList<MutableList<IdentifierNode>> = mutableListOf()
    fun preserveAndTraceNames(block: () -> Unit): List<IdentifierNode> {
        val list = mutableListOf<IdentifierNode>()
        tracedIdentifiers.add(list)
        val oldCaptureNames = captureNamesField
        captureNamesField = false
        try {
            block()
        } finally {
            captureNamesField = oldCaptureNames
            assert(tracedIdentifiers.remove(list))
        }
//        println("id trace: ${list.joinToString(", ") { it.name }}")
        return list
    }
    fun<T> captureNames(block: () -> T): T {
        val oldCaptureNames = captureNamesField
        captureNamesField = true
        return try {
            block()
        } finally {
            captureNamesField = oldCaptureNames
        }
    }
    init {
        if (frame == null) {
            currentFrame = parent!!.currentFrame
            currentFrameScope = parent.currentFrameScope
        } else {
            currentFrame = frame
            currentFrameScope = this
        }
    }
    fun getMangledName(name: String): String? {
        if (Frame.isReserved(name)) return name
        return locals[name] ?: if (parent != null) {
            parent.getMangledName(name)
        } else if (Frame.isBuiltin(name)) {
            name
        } else {
            null
        }
    }
    fun testName(name: String): Boolean {
        return getMangledName(name) != null
    }
    fun acquireExistingName(name: String) {
        if (Frame.isReserved(name)) {
            return
        }
        val mangled = getMangledName(name) ?: throw CompileException("Name $name is not defined")
        currentFrame.acquireName(mangled) ?: throw CompileException("Name $name cannot be captured. This should not happen.")
    }
    fun queryName(name: String): NameType {
        if (Frame.isReserved(name)) {
            return NameType.LOCAL
        }
        val mangled = getMangledName(name) ?: throw CompileException("Name $name is not defined")
        return currentFrame.acquireName(mangled)!!
    }
    // returns mangled name
    fun declareScopeName(name: String): String {
        if (name.startsWith("$")) {
            throw CompileException("Cannot declare reserved name $name")
        }
        val mangled = if (parent == null) {
            if (name in locals) throw CompileException("Name $name already declared in this scope")
            name
        } else "$name@${UniqueID.get()}"
        locals[name] = mangled
        currentFrame.declareLocalName(mangled)
        return mangled
    }
    // returns mangled name
    fun declareLocalName(name: String): String {
        return currentFrameScope.declareScopeName(name)
    }
    // get stack layout
    fun getLocalLayout(name: String): Int {
        return currentFrame.getLocalMemoryLayout(getMangledName(name)!!)
    }
    fun getGlobalLayout(name: String): Int {
        return currentFrame.getGlobalMemoryLayout(getMangledName(name)!!)
    }

    fun exportSymbolTable(): SymbolTable {
        return currentFrame.locals.mapIndexed { index, s -> s to StaticPointer(index) }.toMap()
    }

}

typealias SymbolTable = Map<String, Pointer>
