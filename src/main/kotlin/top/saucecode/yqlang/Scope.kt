package top.saucecode.yqlang

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.saucecode.yqlang.NodeValue.NodeValue
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Runtime.Pointer

open class InterpretationRuntimeException(message: String) : YqlangException(message)

class Scope(private val symbols: MutableMap<String, Pointer> = mutableMapOf()) {
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
        fun deserialize(input: String): Scope {
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
            return Scope(mutableMapOf())
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
typealias SymbolTable = Scope

// TODO: implement events
class ReferenceEnvironment(rootScope: Scope, private val events: Map<String, NodeValue>) {
    private val scopes: MutableList<Scope>
    private val frames: MutableList<Int> = mutableListOf(0)
    private val builtins: MutableMap<String, Int> = mutableMapOf()

    init {
        scopes = mutableListOf(rootScope)
        val symbols = Constants.builtinSymbols.toList().map { it.first }
        val procedures = Constants.builtinProcedures.toList().map { it.first }
        val joined = symbols + procedures
        builtins.putAll(joined.mapIndexed { index, s -> s to index })
    }

    fun pushScope() {
        scopes.add(Scope(mutableMapOf()))
    }
    fun pushFrame() {
        scopes.add(Scope(mutableMapOf()))
        frames.add(scopes.lastIndex)
    }

    fun popScope() {
        if (frames.lastOrNull() == scopes.lastIndex) {
            frames.removeLast()
        }
        scopes.removeAt(scopes.lastIndex)
    }

    fun getGlobalName(name: String): Pointer? {
        return scopes.first()[name] ?: builtins[name]?.let { Pointer(Memory.Location.BUILTIN, it) }
    }

    fun getCaptureName(name: String): Pointer? {
        for (i in frames.last() - 1 downTo 1) {
            scopes[i][name]?.let { return it }
        }
        return null
    }

    fun getLocalName(name: String): Pointer? {
        for (i in scopes.lastIndex downTo frames.last()) {
            scopes[i][name]?.let { return it }
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
        scopes.last()[name] = value
    }

    // name = value, implicit declaration upgraded to local frame
    fun setLocalName(name: String, value: Pointer) {
        scopes[frames.last()][name] = value
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