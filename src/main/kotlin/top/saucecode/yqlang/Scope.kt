package top.saucecode.yqlang

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.saucecode.yqlang.Node.IdentifierNode
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer

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
    companion object {
        fun isReserved(name: String): Boolean {
            return name == "this" || name.startsWith("$")
        }
        fun isBuiltin(name: String): Boolean {
            return name in Constants.builtinProcedures.keys
        }
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
    private fun getMangledName(name: String): String? {
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
}

open class InterpretationRuntimeException(message: String) : YqlangException(message)

class RuntimeScope(private val symbols: MutableMap<String, Pointer> = mutableMapOf()) {
    // get pointer from symbol
    operator fun get(name: String): Pointer? {
        return symbols[name]
    }

    // set pointer to symbol
    operator fun set(name: String, value: Pointer) {
        symbols[name] = value
    }

    override fun toString(): String {
        return "Scope(symbols=$symbols)"
    }

    // TODO: supply memory to it
    fun displaySymbols(): String {
        return symbols.toString()
    }

    companion object {
        fun deserialize(input: String): RuntimeScope {
//            val dict = Json.decodeFromString<MutableMap<String, String>>(serializer(), input)
//            val reconstructed = mutableMapOf<String, NodeValue>()
//            try {
//                dict.forEach { (key, value) ->
//                    reconstructed[key] = Json.decodeFromString(NodeValue.serializer(), value)
//                }
//            } catch (_: Exception) {
//            }
//            return Scope(reconstructed)
            // TODO: implement
            return RuntimeScope(mutableMapOf())
        }
    }

    fun serialize(): String {
        val filteredSymbols = mutableMapOf<String, String>()
        for ((key, value) in symbols) {
            try {
                val jsonValue = Json.encodeToString(value)
                filteredSymbols[key] = jsonValue
            } catch (e: Exception) {
                println(e.message)
            }
        }
        return Json.encodeToString(filteredSymbols)
    }
}
typealias SymbolTable = RuntimeScope

// TODO: implement events
class ReferenceEnvironment(rootRuntimeScope: RuntimeScope, private val events: Map<String, NodeValue>) {
    private val runtimeScopes: MutableList<RuntimeScope>
    private val frames: MutableList<Int> = mutableListOf(0)
    private val builtins: MutableMap<String, Int> = mutableMapOf()

    init {
        runtimeScopes = mutableListOf(rootRuntimeScope)
        val symbols = Constants.builtinSymbols.toList().map { it.first }
        val procedures = Constants.builtinProcedures.toList().map { it.first }
        val joined = symbols + procedures
        builtins.putAll(joined.mapIndexed { index, s -> s to index })
    }

    fun pushScope() {
        runtimeScopes.add(RuntimeScope(mutableMapOf()))
    }
    fun pushFrame() {
        runtimeScopes.add(RuntimeScope(mutableMapOf()))
        frames.add(runtimeScopes.lastIndex)
    }

    fun popScope() {
        if (frames.lastOrNull() == runtimeScopes.lastIndex) {
            frames.removeLast()
        }
        runtimeScopes.removeAt(runtimeScopes.lastIndex)
    }

    fun getGlobalName(name: String): Pointer? {
        return runtimeScopes.first()[name] ?: builtins[name]?.let { Pointer(Memory.Location.BUILTIN, it) }
    }

    fun getCaptureName(name: String): Pointer? {
        for (i in frames.last() - 1 downTo 1) {
            runtimeScopes[i][name]?.let { return it }
        }
        return null
    }

    fun getLocalName(name: String): Pointer? {
        for (i in runtimeScopes.lastIndex downTo frames.last()) {
            runtimeScopes[i][name]?.let { return it }
        }
        return null
    }

    fun getName(name: String): Pointer? {
        if (name.length >= 2 && name[0] == '$') {
            name.substring(1).toIntOrNull()?.let {
                return Pointer.arg(it)
            }
        } else if (name == "$") {
            return Pointer.args
        } else if (name == "this") {
            return Pointer.caller
        }
        return getLocalName(name) ?: getCaptureName(name) ?: getGlobalName(name)
    }

    // let name = value
    fun setScopeName(name: String, value: Pointer) {
        runtimeScopes.last()[name] = value
    }

    // name = value, implicit declaration upgraded to local frame
    fun setLocalName(name: String, value: Pointer) {
        runtimeScopes[frames.last()][name] = value
    }

//    private var local: Boolean = false
//    private fun withLocal(block: () -> Unit) {
//        try {
//            local = true
//            block()
//        } finally {
//            local = false
//        }
//    }
//
//    fun nameArgs(context: ExecutionContext, params: ListNode, self: NodeValue?) {
//        withLocal {
//            params.assign(context, args)
//            this["\$"] = args
//            args.forEachIndexed { index, nodeValue -> this["\$$index"] = nodeValue }
//            if (self != null) {
//                this["this"] = self
//            }
//        }
//    }
//
//    operator fun get(name: String): NodeValue? {
//        events[name]?.let { return it }
//        for (scope in scopes.reversed()) {
//            val value = scope[name]
//            if (value != null) {
//                return value
//            }
//        }
//        return Constants.builtinSymbols[name] ?: Constants.builtinProcedures[name]
//    }
//
//    operator fun set(name: String, value: NodeValue) {
//        if (local) {
//            scopes.last()[name] = value
//            return
//        }
//        if (name[0] == '$') {
//            scopes.last()[name] = value
//        } else {
//            for (scope in scopes.reversed()) {
//                if (scope[name] != null) {
//                    scope[name] = value
//                    return
//                }
//            }
//            scopes.last()[name] = value
//        }
//    }
//
//    fun declare(name: String, value: NodeValue) {
//        withLocal {
//            this[name] = value
//        }
//    }
}