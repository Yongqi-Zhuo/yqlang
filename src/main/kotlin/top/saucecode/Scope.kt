package top.saucecode

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import top.saucecode.Node.ListNode
import top.saucecode.NodeValue.ListValue
import top.saucecode.NodeValue.NodeValue
import top.saucecode.NodeValue.toNodeValue

class Scope(val symbols: MutableMap<String, NodeValue>, val args: ListValue = ListValue(mutableListOf())) {
    operator fun get(name: String): NodeValue? {
        return symbols[name]
    }

    operator fun set(name: String, value: NodeValue) {
        symbols[name] = value
    }

    fun remove(name: String) {
        symbols.remove(name)
    }

    override fun toString(): String {
        return "Scope(symbols=$symbols, args=$args)"
    }

    companion object {
        fun createRoot(defs: Map<String, NodeValue> = mapOf()): Scope {
            val builtins = defs.toMutableMap()
            return Scope(builtins)
        }

        fun deserialize(input: String): Scope {
            val dict = Json.decodeFromString<MutableMap<String, String>>(serializer(), input)
            val reconstructed = mutableMapOf<String, NodeValue>()
            try {
                dict.forEach { (key, value) ->
                    reconstructed[key] = Json.decodeFromString(NodeValue.serializer(), value)
                }
            } catch (_: Exception) {
            }
            return Scope(reconstructed)
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

class RecursionTooDeepException(private val depth: Int) : Exception() {
    override fun toString(): String {
        return "Recursion too deep: $depth"
    }

    override val message: String = toString()
}

class Stack(rootScope: Scope) {
    private val scopes: MutableList<Scope>

    private var depth = 0

    init {
        scopes = mutableListOf(rootScope)
    }

    // The first argument must be the value of "this"
    fun push(args: ListValue = emptyList<NodeValue>().toNodeValue()) {
        scopes.add(Scope(mutableMapOf(), args))
        depth++
        if (depth > 300) {
            throw RecursionTooDeepException(depth)
        }
    }

    fun pop() {
        depth--
        scopes.removeAt(scopes.lastIndex)
    }

    private val args: ListValue
        get() = scopes.last().args

    fun nameArgs(context: ExecutionContext, params: ListNode, self: NodeValue?) {
        local = true
        params.assign(context, args)
        this["\$"] = args
        args.forEachIndexed { index, nodeValue -> this["\$$index"] = nodeValue }
        if (self != null) {
            this["this"] = self
        }
        local = false
    }

    operator fun get(name: String): NodeValue? {
        for (scope in scopes.reversed()) {
            val value = scope[name]
            if (value != null) {
                return value
            }
        }
        return Constants.builtinSymbols[name] ?: Constants.builtinProcedures[name]
    }

    private var local: Boolean = false
    operator fun set(name: String, value: NodeValue) {
        if (local) {
            scopes.last()[name] = value
            return
        }
        if (name[0] == '$') {
            scopes.last()[name] = value
        } else {
            for (scope in scopes.reversed()) {
                if (scope[name] != null) {
                    scope[name] = value
                    return
                }
            }
            scopes.last()[name] = value
        }
    }

    fun declare(name: String, value: NodeValue) {
        local = true
        this[name] = value
        local = false
    }
}