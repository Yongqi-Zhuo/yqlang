enum class TokenType {
    BRACE_OPEN, BRACE_CLOSE, PAREN_OPEN, PAREN_CLOSE, BRACKET_OPEN, BRACKET_CLOSE, SEMICOLON,
    ASSIGN, DOT, COMMA, PLUS, MINUS, MULTIPLY, DIVIDE, AND, OR, EQUAL, NOT_EQUAL,
    IF, ACTION,
    IDENTIFIER, BUILTIN,
    NUMBER, STRING,
    EOF
}

data class Token(val type: TokenType, val value: String) {
    override fun toString(): String {
        return if (type == TokenType.STRING) "$type: \"$value\"" else "$type: $value"
    }
}

class Tokenizer(private val input: String) {
    private var index = 0
    private var currentChar = input[index]

    private fun advance() {
        index++
        if (index < input.length) {
            currentChar = input[index]
        }
    }

    fun scan(): List<Token> {
        val tokens = mutableListOf<Token>()
        fun pushAndAdvance(token: Token) {
            tokens.add(token)
            advance()
        }
        while (index < input.length) {
            when {
                currentChar == '{' -> pushAndAdvance(Token(TokenType.BRACE_OPEN, "{"))
                currentChar == '}' -> pushAndAdvance(Token(TokenType.BRACE_CLOSE, "}"))
                currentChar == '(' -> pushAndAdvance(Token(TokenType.PAREN_OPEN, "("))
                currentChar == ')' -> pushAndAdvance(Token(TokenType.PAREN_CLOSE, ")"))
                currentChar == '[' -> pushAndAdvance(Token(TokenType.BRACKET_OPEN, "["))
                currentChar == ']' -> pushAndAdvance(Token(TokenType.BRACKET_CLOSE, "]"))
                currentChar == ';' -> pushAndAdvance(Token(TokenType.SEMICOLON, ";"))
                currentChar == '.' -> pushAndAdvance(Token(TokenType.DOT, "."))
                currentChar == ',' -> pushAndAdvance(Token(TokenType.COMMA, ","))
                currentChar == '+' -> pushAndAdvance(Token(TokenType.PLUS, "+"))
                currentChar == '-' -> pushAndAdvance(Token(TokenType.MINUS, "-"))
                currentChar == '*' -> pushAndAdvance(Token(TokenType.MULTIPLY, "*"))
                currentChar == '/' -> pushAndAdvance(Token(TokenType.DIVIDE, "/"))
                currentChar == '&' -> {
                    if (index < input.length - 1 && input[index + 1] == '&') {
                        tokens.add(Token(TokenType.AND, "&&"))
                        advance()
                        advance()
                    } else {
                        throw IllegalArgumentException("Unexpected character: $currentChar")
                    }
                }
                currentChar == '|' -> {
                    if (index < input.length - 1 && input[index + 1] == '|') {
                        tokens.add(Token(TokenType.OR, "||"))
                        advance()
                        advance()
                    } else {
                        throw IllegalArgumentException("Unexpected character: $currentChar")
                    }
                }
                currentChar == '=' -> {
                    if (index < input.length - 1 && input[index + 1] == '=') {
                        tokens.add(Token(TokenType.EQUAL, "=="))
                        advance()
                        advance()
                    } else {
                        pushAndAdvance(Token(TokenType.ASSIGN, "="))
                    }
                }
                currentChar == '!' -> {
                    if (index < input.length - 1 && input[index + 1] == '=') {
                        tokens.add(Token(TokenType.NOT_EQUAL, "!="))
                        advance()
                        advance()
                    } else {
                        throw IllegalArgumentException("Unexpected character: $currentChar")
                    }
                }
                currentChar == '"' -> {
                    val start = index
                    do {
                        advance()
                    } while (currentChar != '"' && index < input.length)
                    val value = input.substring(start + 1, index)
                    tokens.add(Token(TokenType.STRING, value))
                    advance()
                }
                currentChar.isDigit() -> {
                    val start = index
                    do {
                        advance()
                    } while (currentChar.isDigit() && index < input.length)
                    val value = input.substring(start, index)
                    tokens.add(Token(TokenType.NUMBER, value))
                }
                currentChar.isLetter() -> {
                    val start = index
                    do {
                        advance()
                    } while (currentChar.isLetterOrDigit() && index < input.length)
                    when (val value = input.substring(start, index)) {
                        "if" -> tokens.add(Token(TokenType.IF, "if"))
                        "say" -> tokens.add(Token(TokenType.ACTION, "say"))
                        "split" -> tokens.add(Token(TokenType.BUILTIN, "split"))
                        "join" -> tokens.add(Token(TokenType.BUILTIN, "join"))
                        "text" -> tokens.add(Token(TokenType.IDENTIFIER, "text")) // events are special identifiers
                        else -> tokens.add(Token(TokenType.IDENTIFIER, value))
                    }
                }
                else -> {
                    advance()
                }
            }
        }
        tokens.add(Token(TokenType.EOF, "EOF"))
        return tokens
    }
}

interface NodeValue {
    fun toBoolean(): Boolean
}

class StringValue(val value: String) : NodeValue {
    override fun toString() = value
    override fun toBoolean(): Boolean = value.isNotEmpty()
}

class ListValue(val value: List<String>) : NodeValue {
    override fun toString() = "[${value.joinToString(", ")}]"
    override fun toBoolean(): Boolean = value.isNotEmpty()
}

class NumberValue(val value: Int) : NodeValue {
    override fun toString() = value.toString()
    override fun toBoolean(): Boolean = value != 0
}

class NullValue : NodeValue {
    override fun toString() = "null"
    override fun toBoolean(): Boolean = false
}

class SymbolTable {
    private val table = mutableMapOf<String, NodeValue>()

    fun get(name: String): NodeValue? {
        return table[name]
    }

    fun set(name: String, value: NodeValue) {
        table[name] = value
    }
}

interface Node {
    fun exec(): NodeValue
}

class IdentifierNode(token: Token, private val table: SymbolTable) : Node {
    private val name: String

    init {
        if (token.type != TokenType.IDENTIFIER) {
            throw IllegalArgumentException("Expected IDENTIFIER, got ${token.type}")
        }
        name = token.value
    }

    override fun exec(): NodeValue {
        return table.get(name) ?: NullValue()
    }

    fun hasDefinition(): Boolean {
        return table.get(name) != null
    }

    fun assign(value: NodeValue) {
        table.set(name, value)
    }

    override fun toString(): String {
        return "id($name)"
    }

}

class NumberNode(token: Token) : Node {
    private val value: Int

    init {
        if (token.type != TokenType.NUMBER) {
            throw IllegalArgumentException("Expected NUMBER, got ${token.type}")
        }
        value = token.value.toInt()
    }

    override fun exec(): NodeValue {
        return NumberValue(value)
    }

    override fun toString(): String {
        return "num($value)"
    }
}

class StringNode(token: Token) : Node {
    private val value: String

    init {
        if (token.type != TokenType.STRING) {
            throw IllegalArgumentException("Expected STRING, got ${token.type}")
        }
        value = token.value
    }

    override fun exec(): NodeValue {
        return StringValue(value)
    }

    override fun toString(): String {
        return "str(\"$value\")"
    }
}

class ParamListNode(val params: List<ExprNode>) : Node {
    override fun exec(): NodeValue {
        return NullValue()
    }

    override fun toString(): String {
        return "params(${params.joinToString(", ")})"
    }
}

class UnitCallNode(private val expr: Node, func: Token, private val args: ParamListNode) : Node {
    private val func: String

    init {
        if (func.type != TokenType.BUILTIN) {
            throw IllegalArgumentException("Expected BUILTIN, got ${func.type}")
        }
        this.func = func.value
    }

    override fun exec(): NodeValue {
        return when (func) {
            "split" -> {
                ListValue((expr.exec() as StringValue).value.split((args.params[0].exec() as StringValue).value))
            }
            "join" -> {
                StringValue((expr.exec() as ListValue).value.joinToString((args.params[0].exec() as StringValue).value))
            }
            "len" -> {
                NumberValue((expr.exec() as ListValue).value.size)
            }
            "defined" -> {
                NumberValue(if((expr as IdentifierNode).hasDefinition()) 1 else 0)
            }
            else -> throw IllegalArgumentException("Unknown builtin function $func")
        }
    }

    override fun toString(): String {
        return "func($func, $expr, $args)"
    }
}

class UnitSubscriptNode(private val unit: Node, private val subscript: ExprNode) : Node {
    override fun exec(): NodeValue {
        val value = unit.exec()
        val index = (subscript.exec() as NumberValue).value
        return when (value) {
            is ListValue -> {
                val list = value.value
                if (index < 0 || index >= list.size) {
                    throw IllegalArgumentException("Index out of bounds: $index")
                }
                StringValue(list[index])
            }
            is StringValue -> {
                val str = value.value
                StringValue(str[index].toString())
            }
            else -> throw IllegalArgumentException("Expected list or string, got ${value.javaClass}")
        }
    }

    override fun toString(): String {
        return "subscript($unit, $subscript)"
    }
}

class FactorNode(private val units: List<Node>, private val ops: List<Token>): Node {
    override fun exec(): NodeValue {
        val values = units.map { it.exec() }.toMutableList()
        val ops = ops.map { it.type }.toMutableList()
        var res = values.removeAt(0)
        while (values.size > 0) {
            val next = values.removeAt(0)
            val op = ops.removeAt(0)
            if(res is NumberValue && next is NumberValue) {
                res = when (op) {
                    TokenType.MULTIPLY -> NumberValue(res.value * next.value)
                    TokenType.DIVIDE -> NumberValue(res.value / next.value)
                    else -> throw IllegalArgumentException("Unknown operator $op")
                }
            } else {
                throw IllegalArgumentException("Invalid operation: $res $op $next")
            }
        }
        return res
    }

    override fun toString(): String {
        var str = ""
        for (i in units.indices) {
            str += units[i].toString()
            if (i < ops.size) {
                str += ops[i].value
            }
        }
        return "factor($str)"
    }
}

class TermNode(private val factors: List<FactorNode>, private val ops: List<Token>) : Node {
    override fun exec(): NodeValue {
        val values = factors.map { it.exec() }.toMutableList()
        val ops = ops.map { it.type }.toMutableList()
        var res = values.removeAt(0)
        while(values.size > 0) {
            val next = values.removeAt(0)
            val op = ops.removeAt(0)
            if(res is NumberValue && next is NumberValue) {
                res = when(op) {
                    TokenType.PLUS -> NumberValue(res.value + next.value)
                    TokenType.MINUS -> NumberValue(res.value - next.value)
                    else -> throw IllegalArgumentException("Unknown operator $op")
                }
            } else if (op == TokenType.PLUS) {
                res = when (res) {
                    is NumberValue -> {
                        when (next) {
                            is StringValue -> StringValue(res.value.toString() + next.value)
                            is ListValue -> ListValue(listOf(res.value.toString()) + next.value)
                            else -> throw IllegalArgumentException("Invalid operation: $res + $next")
                        }
                    }
                    is StringValue -> {
                        when (next) {
                            is NumberValue -> StringValue(res.value + next.value.toString())
                            is StringValue -> StringValue(res.value + next.value)
                            is ListValue -> ListValue(listOf(res.value) + next.value)
                            else -> throw IllegalArgumentException("Invalid operation: $res + $next")
                        }
                    }
                    is ListValue -> {
                        when (next) {
                            is NumberValue -> ListValue(res.value + listOf(next.value.toString()))
                            is StringValue -> ListValue(res.value + listOf(next.value))
                            is ListValue -> ListValue(res.value + next.value)
                            else -> throw IllegalArgumentException("Invalid operation: $res + $next")
                        }
                    }
                    else -> throw IllegalArgumentException("Invalid operation: $res + $next")
                }
            } else {
                throw IllegalArgumentException("Invalid operation: $res $op $next")
            }
        }
        return res
    }

    override fun toString(): String {
        var str = ""
        for (i in factors.indices) {
            str += factors[i].toString()
            if (i < ops.size) {
                str += ops[i].value
            }
        }
        return "term($str)"
    }
}

class ExprNode(private val terms: List<TermNode>, private val ops: List<Token>): Node {
    override fun exec(): NodeValue {
        val values = terms.map { it.exec() }.toMutableList()
        val ops = ops.map { it.type }.toMutableList()
        var res = values.removeAt(0)
        while(values.size > 0) {
            if(res is NullValue) res = NumberValue(0)
            var next = values.removeAt(0)
            next = if(next is NullValue) NumberValue(0) else next
            when(val op = ops.removeAt(0)) {
                TokenType.EQUAL -> {
                    res = if(res is NumberValue && next is NumberValue) {
                        NumberValue(if(res.value == next.value) 1 else 0)
                    } else if (res is StringValue && next is StringValue) {
                        NumberValue(if(res.value == next.value) 1 else 0)
                    } else if (res is ListValue && next is ListValue) {
                        NumberValue(if(res.value == next.value) 1 else 0)
                    } else {
                        throw IllegalArgumentException("Invalid operation: $res == $next")
                    }
                }
                TokenType.NOT_EQUAL -> {
                    res = if(res is NumberValue && next is NumberValue) {
                        NumberValue(if(res.value != next.value) 1 else 0)
                    } else if (res is StringValue && next is StringValue) {
                        NumberValue(if(res.value != next.value) 1 else 0)
                    } else if (res is ListValue && next is ListValue) {
                        NumberValue(if(res.value != next.value) 1 else 0)
                    } else {
                        NumberValue(1)
                    }
                }
                TokenType.AND -> res = NumberValue(if(res.toBoolean() && next.toBoolean()) 1 else 0)
                TokenType.OR -> res = NumberValue(if(res.toBoolean() || next.toBoolean()) 1 else 0)
                else -> throw IllegalArgumentException("Invalid operation: $res $op $next")
            }
        }
        return res
    }

    override fun toString(): String {
        var str = ""
        for (i in terms.indices) {
            str += terms[i].toString()
            if (i < ops.size) {
                str += ops[i].value
            }
        }
        return "expr($str)"
    }
}

class StmtAssignNode(private val identifier: IdentifierNode, private val expr: ExprNode) : Node {
    override fun exec(): NodeValue {
        val value = expr.exec()
        identifier.assign(value)
        return NullValue()
    }

    override fun toString(): String {
        return "assign($identifier, $expr)"
    }
}

class StmtActionNode(private val action: Token, private val expr: ExprNode) : Node {
    override fun exec(): NodeValue {
        if(action.type != TokenType.ACTION) {
            throw IllegalArgumentException("Expected ACTION, got ${action.type}")
        }
        val value = expr.exec()
        when (action.value) {
            "say" -> {
                println(value)
            }
            else -> throw IllegalArgumentException("Unknown action ${action.value}")
        }
        return NullValue()
    }

    override fun toString(): String {
        return "action(${action.value}, $expr)"
    }
}

class StmtIfNode(private val condition: Node, private val ifBody: StmtListNode) : Node {
    override fun exec(): NodeValue {
        if (condition.exec().toBoolean()) {
            ifBody.exec()
        }
        return NullValue()
    }

    override fun toString(): String {
        return "if($condition, body($ifBody))"
    }
}

class StmtListNode(private val stmts: List<Node>) : Node {
    override fun exec(): NodeValue {
        for (node in stmts) {
            node.exec()
        }
        return NullValue()
    }

    override fun toString(): String {
        return "stmts(${stmts.joinToString(", ")})"
    }
}

class Parser(private val tokens: List<Token>, definedSymbols: List<String>) {
    private var current = 0
    private var symbolTable = SymbolTable()

    init {
        definedSymbols.map { symbolTable.set(it, StringValue("this is a whole new world the world of parsing")) }
    }

    private fun consume(type: TokenType): Token {
        if (isAtEnd()) {
            throw IllegalStateException("Unexpected end of input")
        }
        if (tokens[current].type != type) {
            throw IllegalStateException("Expected $type, got ${tokens[current]}")
        }
        return tokens[current++]
    }
    private fun isAtEnd() = current >= tokens.size
    private fun peek() = tokens[current]
//    private fun peekNext() = tokens[current + 1]

    private fun parseIdentifier() = IdentifierNode(consume(TokenType.IDENTIFIER), symbolTable)
    private fun parseNumber() = NumberNode(consume(TokenType.NUMBER))
    private fun parseString() = StringNode(consume(TokenType.STRING))

    private fun parseStmtList(): StmtListNode {
        val stmts = mutableListOf<Node>()
        while (peek().type != TokenType.EOF && peek().type != TokenType.BRACE_CLOSE) {
            stmts.add(parseStmt())
        }
        return StmtListNode(stmts)
    }

    private fun parseStmt(): Node {
        val token = peek()
        when (token.type) {
            TokenType.IDENTIFIER -> {
                val identifier = parseIdentifier()
                consume(TokenType.ASSIGN)
                val expr = parseExpr()
                consume(TokenType.SEMICOLON)
                return StmtAssignNode(identifier, expr)
            }
            TokenType.ACTION -> {
                val action = consume(TokenType.ACTION)
                val expr = parseExpr()
                consume(TokenType.SEMICOLON)
                return StmtActionNode(action, expr)
            }
            TokenType.IF -> {
                consume(TokenType.IF)
                consume(TokenType.PAREN_OPEN)
                val condition = parseExpr()
                consume(TokenType.PAREN_CLOSE)
                consume(TokenType.BRACE_OPEN)
                val ifBody = parseStmtList()
                consume(TokenType.BRACE_CLOSE)
                return StmtIfNode(condition, ifBody)
            }
            else -> throw IllegalStateException("Unexpected token ${token.value}")
        }
    }

    private fun parseExpr(): ExprNode {
        val terms = mutableListOf(parseTerm())
        val ops = mutableListOf<Token>()
        while (true) {
            val token = peek()
            when (token.type) {
                TokenType.EQUAL -> {
                    ops.add(consume(TokenType.EQUAL))
                    terms.add(parseTerm())
                }
                TokenType.NOT_EQUAL -> {
                    ops.add(consume(TokenType.NOT_EQUAL))
                    terms.add(parseTerm())
                }
                TokenType.AND -> {
                    ops.add(consume(TokenType.AND))
                    terms.add(parseTerm())
                }
                TokenType.OR -> {
                    ops.add(consume(TokenType.OR))
                    terms.add(parseTerm())
                }
                else -> return ExprNode(terms, ops)
            }
        }
    }

    private fun parseTerm(): TermNode {
        val factors = mutableListOf(parseFactor())
        val ops = mutableListOf<Token>()
        while(true) {
            val token = peek()
            when(token.type) {
                TokenType.PLUS -> {
                    ops.add(consume(TokenType.PLUS))
                    factors.add(parseFactor())
                }
                TokenType.MINUS -> {
                    ops.add(consume(TokenType.MINUS))
                    factors.add(parseFactor())
                }
                else -> return TermNode(factors, ops)
            }
        }
    }

    private fun parseFactor(): FactorNode {
        val units = mutableListOf(parseUnit())
        val ops = mutableListOf<Token>()
        while (true) {
            val token = peek()
            when (token.type) {
                TokenType.MULTIPLY -> {
                    ops.add(consume(TokenType.MULTIPLY))
                    units.add(parseUnit())
                }
                TokenType.DIVIDE -> {
                    ops.add(consume(TokenType.DIVIDE))
                    units.add(parseUnit())
                }
                else -> return FactorNode(units, ops)
            }
        }
    }

    private fun parseUnitHead(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.IDENTIFIER -> parseIdentifier()
            TokenType.NUMBER -> parseNumber()
            TokenType.STRING -> parseString()
            else -> throw IllegalStateException("Unexpected token ${token.value}")
        }
    }

    private fun parseParamList(): ParamListNode {
        val params = mutableListOf<ExprNode>()
        if(peek().type != TokenType.PAREN_CLOSE) {
            params.add(parseExpr())
            while (peek().type != TokenType.PAREN_CLOSE) {
                consume(TokenType.COMMA)
                params.add(parseExpr())
            }
        }
        return ParamListNode(params)
    }

    private fun parseUnitTail(unitHead: Node): Node {
        val token = peek()
        return when (token.type) {
            TokenType.DOT -> {
                consume(TokenType.DOT)
                val func = consume(TokenType.BUILTIN)
                consume(TokenType.PAREN_OPEN)
                val params = parseParamList()
                consume(TokenType.PAREN_CLOSE)
                UnitCallNode(unitHead, func, params)
            }
            TokenType.BRACKET_OPEN -> {
                consume(TokenType.BRACKET_OPEN)
                val subscript = parseExpr()
                consume(TokenType.BRACKET_CLOSE)
                UnitSubscriptNode(unitHead, subscript)
            }
            else -> unitHead
        }
    }

    private fun parseUnit(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.PAREN_OPEN -> {
                consume(TokenType.PAREN_OPEN)
                val expr = parseExpr()
                consume(TokenType.PAREN_CLOSE)
                parseUnitTail(expr)
            }
            else -> {
                val unitHead = parseUnitHead()
                parseUnitTail(unitHead)
            }
        }
    }

    fun parse(): Node {
        return parseStmtList()
    }
}

fun main() {
//    val input = """
//        |if (text) {
//        |   arr = text.split(" ");
//        |   say arr.join(", ");
//        |}
//        """.trimMargin()
    val inputs = mutableListOf<String>()
    while (true) {
        print("> ")
        val line = readLine() ?: break
        inputs.add(line)
    }
    val input = inputs.joinToString("\n")
    println(input)
    println("\nTokenizing...")
    val tokens = Tokenizer(input).scan()
    println(tokens.joinToString(", "))
    println("\nParsing...")
    val ast = Parser(tokens, listOf("text")).parse()
    println(ast)
    println("\nExecuting...")
    ast.exec()
    println("\nDone!")
}