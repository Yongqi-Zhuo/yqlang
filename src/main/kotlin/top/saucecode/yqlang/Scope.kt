package top.saucecode.yqlang

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val captures: MutableSet<String> = mutableSetOf() // empty for root
    // variables that need to be forwarded to next frame
    val forwards: MutableMap<String, MutableSet<String>> = mutableMapOf() // empty for root, because root vars are global
    // args are treated as special locals to support pattern matching in function prototypes
    // val arguments: MutableList<String> = mutableListOf() // empty for root
    val locals: MutableList<String> = mutableListOf()
    fun getName(name: String): NameType? {
        if (isRoot) {
            return if (name in locals) NameType.GLOBAL
            else null
        } else {
            when (name) {
                in locals -> return NameType.LOCAL
                in captures -> return NameType.CAPTURE
                else -> {
                    // need to do capture or find global
                    val res = parent!!.getName(name) ?: return null
                    when (res) {
                        NameType.GLOBAL -> return NameType.GLOBAL
                        else -> { // parent has captured this for us
                            parent.forwards.getOrPut(this.name) { mutableSetOf() }.add(name)
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
}
class Scope(val parent: Scope?, val frame: Frame?) {
    val currentFrame: Frame
    val currentFrameScope: Scope
    val locals: MutableMap<String, String> = mutableMapOf()
    init {
        if (frame == null) {
            currentFrame = parent!!.currentFrame
            currentFrameScope = parent.currentFrameScope
        } else {
            currentFrame = frame
            currentFrameScope = this
        }
    }
    fun declareScopeName(name: String): String {
        val mangled = if (parent == null) name else "$name@${UniqueID.get()}"
        locals[name] = mangled
        currentFrame.declareLocalName(mangled)
        return mangled
    }
    fun declareLocalName(name: String): String {
        return currentFrameScope.declareScopeName(name)
    }
    // Identifier nodes get their mangled name
    private fun getMangledName(name: String): String? {
        return locals[name] ?: parent?.getMangledName(name)
    }
    fun getName(name: String): NameType? {
        if (name.startsWith("$")) {
            return NameType.LOCAL
        }
        val mangled = getMangledName(name) ?: return null
        return currentFrame.getName(mangled)
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