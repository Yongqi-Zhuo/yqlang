import top.saucecode.yqlang.ConsoleContext
import top.saucecode.yqlang.Parser
import top.saucecode.yqlang.REPL
import top.saucecode.yqlang.Runtime.Memory
import top.saucecode.yqlang.Tokenizer

fun main() {
//    val inputs = mutableListOf<String>()
//    while (true) {
////        print("> ")
//        val line = readLine() ?: break
//        inputs.add(line)
//    }
//    val input = inputs.joinToString("\n")
//
////    println(input)
//    println("Tokenizing...")
//    val tokens = Tokenizer(input).scan()
////    println(tokens.joinToString(", "))
//    val parser = Parser(tokens)
//    println("Parsing...")
//    val ast = parser.parse()
////    println(ast)
//    println("Executing...")
//    val memory = Memory()
//    memory.addStatics(parser.text)
//    val context = ConsoleContext(memory)
//    ast.exec(context)

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

    val repl = REPL(debug = true)
    repl.run()
    println(repl.rootScope.serialize())

//    while(true) {
//        val line = readLine() ?: break
//        println(Scope.deserialize(line))
//    }

    println("\nDone!")
}