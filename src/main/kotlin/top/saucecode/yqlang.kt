package top.saucecode

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import top.saucecode.Node.Node
import top.saucecode.NodeValue.NodeValue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class ExecutionContext(rootScope: Scope, val firstRun: Boolean, events: Map<String, NodeValue>) {
    val stack: Stack
    var sleepTime: Long = 0
        private set

    init {
        stack = Stack(rootScope,  events)
    }

    abstract fun say(text: String)
    abstract fun nudge(target: Long)
    abstract fun picSave(picId: String)
    abstract fun picSend(picId: String)
    abstract fun nickname(id: Long): String
    fun sleep(time: Long) {
        sleepTime += time
        try {
            TimeUnit.MILLISECONDS.sleep(time)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}

class ConsoleContext(rootScope: Scope? = null, events: Map<String, NodeValue>) : ExecutionContext(rootScope ?: Scope.createRoot(), true, events) {
    override fun say(text: String) {
        println(text)
    }

    override fun nudge(target: Long) {
        println("Nudge $target")
    }

    override fun picSave(picId: String) {
        println("Save $picId")
    }

    override fun picSend(picId: String) {
        println("Picture $picId")
    }

    override fun nickname(id: Long): String {
        return "$id"
    }
}

sealed class Output {
    data class Text(val text: String) : Output() {
        override fun toString(): String {
            return text
        }
    }

    data class PicSave(val picId: String) : Output() {
        override fun toString(): String {
            return "Save $picId"
        }
    }

    data class PicSend(val picId: String) : Output() {
        override fun toString(): String {
            return "Picture $picId"
        }
    }

    data class Nudge(val target: Long) : Output() {
        override fun toString(): String {
            return "Nudge $target"
        }
    }

//    data class Reduced(val text: String?, val nudge: Long?) : Output() {
//        override fun toString(): String {
//            return "Reduced $text $nudge"
//        }
//    }
//
//    companion object {
//        fun reduce(list: List<Output>): Reduced {
//            val text = list.filterIsInstance<Text>().ifEmpty { null }?.joinToString("\n") { it.text }
//            val nudge = list.filterIsInstance<Nudge>().lastOrNull()?.target
//            return Reduced(text, nudge)
//        }
//    }
}

open class ControlledContext(rootScope: Scope, firstRun: Boolean, events: Map<String, NodeValue>) : ExecutionContext(rootScope, firstRun, events) {
    private val record = mutableListOf<Output>()
    override fun say(text: String) {
        synchronized(record) {
            record.add(Output.Text(text))
        }
    }

    override fun nudge(target: Long) {
        synchronized(record) {
            record.add(Output.Nudge(target))
        }
    }

    override fun picSave(picId: String) {
        synchronized(record) {
            record.add(Output.PicSave(picId))
        }
    }

    override fun picSend(picId: String) {
        synchronized(record) {
            record.add(Output.PicSend(picId))
        }
    }

    override fun nickname(id: Long): String {
        return "$id"
    }

    fun dumpOutput(): List<Output> {
        val res = synchronized(record) {
            val dump = record.toList()
            record.clear()
            dump
        }
        return res
    }
}

class Interpreter(source: String) {
    private val ast: Node

    init {
        val tokens = Tokenizer(source).scan()
        val parser = Parser(tokens)
        val res = parser.parse()
        ast = res
    }

    fun run(context: ExecutionContext) {
        ast.exec(context)
    }
}

class RestrictedInterpreter(source: String) {
    private val ast: Node

    init {
        val tokens = Tokenizer(source).scan()
        val parser = Parser(tokens)
        val res = parser.parse()
        ast = res
    }

    suspend fun run(
        context: ControlledContext,
        quantum: Long = 100,
        allowance: Long = 800,
        totalAllowance: Long = 60 * 60 * 1000
    ): Flow<List<Output>> = flow {
        var uptime: Long = 0
        val task = CompletableFuture.runAsync {
            ast.exec(context)
        }
        do {
            delay(quantum)
            uptime += quantum
            emit(context.dumpOutput())
        } while (uptime < allowance + context.sleepTime && uptime < totalAllowance && !task.isDone)
        if (!task.isDone) {
            task.cancel(true)
        }
        task.get()
    }
}

class REPL {
    val rootScope = Scope.createRoot()

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
            val context = ControlledContext(rootScope, true, mapOf())
            try {
                val res = ast.exec(context)
                val output = context.dumpOutput()
                if (output.isNotEmpty()) {
                    output.forEach { println(it) }
                } else {
                    println(res)
                }
            } catch (e: Exception) {
                println("Runtime Error: ${e.message}")
            }
        }
    }
}
