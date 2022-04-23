package top.saucecode

import top.saucecode.Node.Node
import top.saucecode.NodeValue.NodeValue
import top.saucecode.NodeValue.ProcedureValue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class ExecutionContext(
    rootScope: Scope, declarations: MutableMap<String, (NodeValue) -> ProcedureValue>, val firstRun: Boolean
) {
    val stack: Stack

    init {
        stack = Stack(rootScope, declarations)
    }

    abstract fun say(text: String)
    abstract fun nudge(target: Long)
    abstract fun nickname(id: Long): String
}

class ConsoleContext(rootScope: Scope? = null, declarations: MutableMap<String, (NodeValue) -> ProcedureValue>) :
    ExecutionContext(rootScope ?: Scope.createRoot(), declarations, true) {
    override fun say(text: String) {
        println(text)
    }

    override fun nudge(target: Long) {
        println("Nudge $target")
    }

    override fun nickname(id: Long): String {
        return "$id"
    }
}

open class ControlledContext(
    rootScope: Scope, declarations: MutableMap<String, (NodeValue) -> ProcedureValue>, firstRun: Boolean
) : ExecutionContext(rootScope, declarations, firstRun) {
    private val record = mutableListOf<String>()
    override fun say(text: String) {
        record.add(text)
    }

    override fun nudge(target: Long) {
        record.add("Nudge $target")
    }

    override fun nickname(id: Long): String {
        return "$id"
    }

    open fun dumpOutput(): String {
        val str = if (record.isEmpty()) "" else record.joinToString("\n")
        record.clear()
        return str
    }
}

class Interpreter(source: String, private val restricted: Boolean) {
    private val ast: Node
    val declarations: MutableMap<String, (NodeValue) -> ProcedureValue>

    init {
        val tokens = Tokenizer(source).scan()
        val parser = Parser(tokens)
        val res = parser.parse()
        ast = res.first
        declarations = res.second
    }

    fun run(context: ExecutionContext) {
        if (restricted) {
            val task = CompletableFuture.runAsync {
                ast.exec(context)
            }.orTimeout(800, TimeUnit.MILLISECONDS)
            task.get()
        } else {
            ast.exec(context)
        }
    }
}

class REPL {
    val rootScope = Scope.createRoot()
    private val declarations = mutableMapOf<String, (NodeValue) -> ProcedureValue>()

    fun run() {
        val inputs = mutableListOf<String>()
        while (true) {
            print("> ")
            val input = readLine() ?: break
            if (input.isEmpty()) continue
            if (input == "exit" || input == "stop") break
            inputs.add(input)
            val ast = try {
                val node = Parser(Tokenizer(inputs.joinToString("\n")).scan()).parse()
                node
            } catch (e: UnexpectedTokenException) {
                if (e.token.type == TokenType.EOF) {
                    continue
                } else {
                    println("Compile Error: ${e.message}")
                    inputs.clear()
                    continue
                }
            } catch (e: Exception) {
                println("Compile Error: ${e.message}")
                inputs.clear()
                continue
            }
            inputs.clear()
            declarations.putAll(ast.second)
            val context = ControlledContext(rootScope, declarations, true)
            try {
                val res = ast.first.exec(context)
                val output = context.dumpOutput()
                if (output.isNotEmpty()) {
                    println(output)
                } else {
                    println(res)
                }
            } catch (e: Exception) {
                println("Runtime Error: ${e.message}")
            }
        }
    }
}
