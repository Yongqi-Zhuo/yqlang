import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import top.saucecode.*

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

//    val interpreter = Interpreter(input, false)
//    val st = SymbolTable.createRoot(mapOf("text" to StringValue("this is a brand-new world the world of parsing")))
//    st.remove("unknown")
//    val context = ConsoleContext(st, interpreter.declarations)
//    interpreter.run(context)

    REPL().run()

    while (true) {
        val line = readLine() ?: break
        println(Json.decodeFromString<NodeValue>(line))
    }
    println("\nDone!")
}