import top.saucecode.yqlang.ConsoleContext
import top.saucecode.yqlang.Parser
import top.saucecode.yqlang.Runtime.VirtualMachine
import top.saucecode.yqlang.Tokenizer

//import top.saucecode.yqlang.REPL

fun main() {
    val inputs = mutableListOf<String>()
    while (true) {
//        print("> ")
        val line = readLine() ?: break
        inputs.add(line)
    }
    val input = inputs.joinToString("\n")

//    println(input)
    println("Tokenizing...")
    val tokens = Tokenizer(input).scan()
//    println(tokens.joinToString(", "))
    val parser = Parser()
    println("Parsing...")
    val res = parser.parse(tokens)
//    println(res.preloadedMemory.assemblyText())
    println("Executing...")
    val memory = res.preloadedMemory
    val context = ConsoleContext()
    VirtualMachine(context, memory).execute()

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

    println("\nDone!")
}