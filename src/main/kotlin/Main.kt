import kotlinx.coroutines.runBlocking
import top.saucecode.*
import top.saucecode.NodeValue.StringValue

fun main() {
//    val inputs = mutableListOf<String>()
//    while (true) {
////        print("> ")
//        val line = readLine() ?: break
//        inputs.add(line)
//    }
//    val input = inputs.joinToString("\n")

//    println(input)
//    println("\nTokenizing...")
//    val tokens = Tokenizer(input).scan()
//    println(tokens.joinToString(", "))
//    println("\nParsing...")
//    val ast = Parser(tokens).parse()
//    println(ast)
//    println("\nExecuting...")
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

    val repl = REPL()
    repl.run()
    println(repl.rootScope.serialize())

//    while(true) {
//        val line = readLine() ?: break
//        println(Scope.deserialize(line))
//    }

    println("\nDone!")
}