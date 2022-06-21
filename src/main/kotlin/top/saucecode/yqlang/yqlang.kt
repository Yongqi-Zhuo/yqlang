package top.saucecode.yqlang

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.saucecode.yqlang.Node.Node
import top.saucecode.yqlang.NodeValue.NodeValue
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

open class YqlangException(message: String) : Exception(message)

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
    private var futureTasks: MutableList<CompletableFuture<*>> = mutableListOf()

    init {
        val tokens = Tokenizer(source).scan()
        val parser = Parser(tokens)
        val res = parser.parse()
        ast = res
    }

    // why we need quantum? to avoid sending message too fast.
    suspend fun run(
        context: ControlledContext,
        quantum: Long = 100,
        allowance: Long = 800,
        totalAllowance: Long = 60 * 60 * 1000,
        maximumInstances: Int = 10
    ): Flow<List<Output>> = flow {
        var task: CompletableFuture<*>? = null
        try {
            var uptime: Long = 0
            task = synchronized(futureTasks) {
                if (futureTasks.size >= maximumInstances) {
                    throw YqlangException("Too many instances of the same script")
                }
                val newTask = CompletableFuture.runAsync {
                    ast.exec(context)
                }
                futureTasks.add(newTask)
                newTask
            }
            do {
                delay(quantum)
                uptime += quantum
                emit(context.dumpOutput())
            } while (uptime < allowance + context.sleepTime && uptime < totalAllowance && !task.isDone)
            if (!task.isDone) {
                task.cancel(true)
            }
            try {
                task.get()
            } catch (e: CancellationException) {
                throw YqlangException("Interpretation cancelled")
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        } finally {
            synchronized(futureTasks) {
                futureTasks.remove(task)
            }
            if (task?.isDone == false) {
                task.cancel(true)
            }
        }
    }

    fun cancelAllRunningTasks() {
        synchronized(futureTasks) {
            futureTasks.forEach { it.cancel(true) }
            futureTasks.clear()
        }
    }
}

class REPL(private val debug: Boolean = false) {
    val rootScope = Scope.createRoot()

    fun run() {
        val inputs = mutableListOf<String>()
        while (true) {
            print("> ")
            val input = readLine() ?: break
            if (input.isEmpty()) continue
            if (input == "exit" || input == "stop") break
            inputs.add(input)
            val ast: Node
            val compileTime: Long
            try {
                compileTime = measureTimeMillis {
                    ast = Parser(Tokenizer(inputs.joinToString("\n")).scan()).parse()
                }
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
            var runTime: Long? = null
            try {
                val res: NodeValue
                runTime = measureTimeMillis {
                    res = ast.exec(context)
                }
                val output = context.dumpOutput()
                if (output.isNotEmpty()) {
                    output.forEach { println(it) }
                } else {
                    println(res)
                }
                if (debug) {
                    println("Serialized: ${Json.encodeToString(res)}")
                }
            } catch (e: Exception) {
                println("Runtime Error: ${e.message}")
            }
            if (debug) {
                println("Compile: $compileTime ms${runTime?.let { ", Run: $runTime ms" } ?: ""}")
            }
        }
    }
}
