import top.saucecode.yqlang.*
import top.saucecode.yqlang.Runtime.VirtualMachine
import kotlin.system.measureTimeMillis

//import top.saucecode.yqlang.REPL

fun main() {
//    val memoryInputs = mutableListOf<String>()
//    while (true) {
//        val line = readLine() ?: break
//        if (line == "EOF") break
//        memoryInputs.add(line)
//    }
//    val memoryInput = memoryInputs.joinToString("\n")
//    val oldMemory = Memory.deserialize(memoryInput)

    val inputs = mutableListOf<String>()
    while (true) {
        val line = readLine() ?: break
        if (line == "EOF") break
        inputs.add(line)
    }
    val input = inputs.joinToString("\n")

//    println(input)
    println("Tokenizing...")
    val tokens = Tokenizer(input).scan()
//    println(tokens.joinToString(", "))
    val parser = Parser()
    println("Parsing...")
    val ast = parser.parse(tokens)
//    println(ast)
    println("Generating...")
    val memory = CodeGenerator().generate(ast)
//    memory.updateFrom(oldMemory)
    println(memory.assemblyText())
    println("Executing...")
    val context = ConsoleContext()
    val time = measureTimeMillis {
        VirtualMachine(context, memory).execute()
    }
    println("Executed in $time ms. Heap size: ${memory.heapSize}. Now GC.")
    val gcTime = measureTimeMillis {
        memory.gc()
    }
    println("GCed in $gcTime ms. Heap size: ${memory.heapSize}.")

//    while (true) {
//        val text = readLine() ?: break
//        if (text == "EOF") break
//        val context = ConsoleContext(listOf(TextEvent(text)))
//        val time = measureTimeMillis {
//            VirtualMachine(context, memory).execute()
//        }
//        println("Executed in $time ms. Heap size: ${memory.heapSize}. Now GC.")
//        val gcTime = measureTimeMillis {
//            memory.gc()
//        }
//        println("GCed in $gcTime ms. Heap size: ${memory.heapSize}.")
////        println("Memory:")
////        println(memory.memoryDump())
//        println("Serialized memory:")
//        println(memory.serialize())
//    }

//    val interpreter = RestrictedInterpreter(input)
//    val st = SymbolTable.createRoot(mapOf("text" to StringValue("this is a brand-new world the world of parsing")))
//    st.remove("unknown")
//    val context = ControlledContext(st, true, mapOf())
//    runBlocking {
//        interpreter.run(context, reduced = true).collect { reducedOutput ->
//            val reduced = reducedOutput as Output.Reduced
//            if (reduced.text != null) {
//                println(reduced.text)
//            }
//        }
//    }

//    val repl = REPL(debug = true)
//    repl.run()
//    println(repl.rootRuntimeScope.serialize())

//    while(true) {
//        val line = readLine() ?: break
//        println(Scope.deserialize(line))
//    }
}