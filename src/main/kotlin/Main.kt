enum class TokenType {
    BRACE_OPEN, BRACE_CLOSE, PAREN_OPEN, PAREN_CLOSE, BRACKET_OPEN, BRACKET_CLOSE, SEMICOLON,
    ASSIGN, DOT, COMMA,
    ADD_OP, // PLUS, MINUS,
    MULT_OP, // MULTIPLY, DIVIDE, MODULO
    LOGIC_OP, //AND, OR, EQUAL, NOT_EQUAL, GREATER, LESS, GREATER_EQUAL, LESS_EQUAL
    NOT,
    IF, ELSE, ACTION,
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

        fun handleTwoCharOp(type: TokenType, value: String, single: TokenType? = null) {
            if (index < input.length - 1 && input[index + 1] == value[1]) {
                tokens.add((Token(type, value)))
                advance()
                advance()
            } else if (single != null) {
                pushAndAdvance(Token(single, value[0].toString()))
            } else {
                throw IllegalArgumentException("Unexpected character: $currentChar")
            }
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
                currentChar == '+' -> handleTwoCharOp(TokenType.ASSIGN, "+=", TokenType.ADD_OP)
                currentChar == '-' -> handleTwoCharOp(TokenType.ASSIGN, "-=", TokenType.ADD_OP)
                currentChar == '*' -> handleTwoCharOp(TokenType.ASSIGN, "*=", TokenType.MULT_OP)
                currentChar == '/' -> handleTwoCharOp(TokenType.ASSIGN, "/=", TokenType.MULT_OP)
                currentChar == '%' -> handleTwoCharOp(TokenType.ASSIGN, "%=", TokenType.MULT_OP)
                currentChar == '&' -> handleTwoCharOp(TokenType.LOGIC_OP, "&&")
                currentChar == '|' -> handleTwoCharOp(TokenType.LOGIC_OP, "||")
                currentChar == '=' -> handleTwoCharOp(TokenType.LOGIC_OP, "==", TokenType.ASSIGN)
                currentChar == '!' -> handleTwoCharOp(TokenType.LOGIC_OP, "!=", TokenType.NOT)
                currentChar == '>' -> handleTwoCharOp(TokenType.LOGIC_OP, ">=", TokenType.LOGIC_OP)
                currentChar == '<' -> handleTwoCharOp(TokenType.LOGIC_OP, "<=", TokenType.LOGIC_OP)
                currentChar == '"' -> {
                    var str = ""
                    var escape = false
                    advance()
                    while (index < input.length) {
                        if (escape) {
                            escape = false
                            when (currentChar) {
                                'n' -> str += '\n'
                                'r' -> str += '\r'
                                't' -> str += '\t'
                                '\\' -> str += '\\'
                                '"' -> str += '"'
                                else -> str += "\\$currentChar"
                            }
                        } else if (currentChar == '\\') {
                            escape = true
                        } else if (currentChar == '"') {
                            break
                        } else {
                            str += currentChar
                        }
                        advance()
                    }
                    tokens.add(Token(TokenType.STRING, str))
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
                        "else" -> tokens.add(Token(TokenType.ELSE, "else"))
                        "say" -> tokens.add(Token(TokenType.ACTION, "say"))
                        "split" -> tokens.add(Token(TokenType.BUILTIN, "split"))
                        "join" -> tokens.add(Token(TokenType.BUILTIN, "join"))
                        "slice" -> tokens.add(Token(TokenType.BUILTIN, "slice"))
                        "toList" -> tokens.add(Token(TokenType.BUILTIN, "toList"))
                        "find" -> tokens.add(Token(TokenType.BUILTIN, "find"))
                        "contains" -> tokens.add(Token(TokenType.BUILTIN, "contains"))
                        "length" -> tokens.add(Token(TokenType.BUILTIN, "length"))
                        "defined" -> tokens.add(Token(TokenType.BUILTIN, "defined"))
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

class SymbolTable(definedSymbols: Map<String, NodeValue>? = null) {
    private val table = mutableMapOf<String, NodeValue>()

    init {
        definedSymbols?.forEach { table[it.key] = it.value }
    }

    fun get(name: String): NodeValue? {
        return table[name]
    }

    fun set(name: String, value: NodeValue) {
        table[name] = value
    }

    fun unset(name: String) {
        table.remove(name)
    }
}

interface Node {
    fun exec(context: ExecutionContext): NodeValue
}

class IdentifierNode(token: Token) : Node {
    val name: String

    init {
        if (token.type != TokenType.IDENTIFIER) {
            throw IllegalArgumentException("Expected IDENTIFIER, got ${token.type}")
        }
        name = token.value
    }

    override fun exec(context: ExecutionContext): NodeValue {
        return context.table.get(name) ?: NullValue()
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

    override fun exec(context: ExecutionContext): NodeValue {
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

    override fun exec(context: ExecutionContext): NodeValue {
        return StringValue(value)
    }

    override fun toString(): String {
        return "str(\"$value\")"
    }
}

class ParamListNode(val params: List<ExprNode>) : Node {
    override fun exec(context: ExecutionContext): NodeValue {
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

    override fun exec(context: ExecutionContext): NodeValue {
        return when (func) {
            "split" -> {
                ListValue((expr.exec(context) as StringValue).value.split((args.params[0].exec(context) as StringValue).value))
            }
            "join" -> {
                StringValue((expr.exec(context) as ListValue).value.joinToString((args.params[0].exec(context) as StringValue).value))
            }
            "slice" -> {
                val col = expr.exec(context)
                val start = args.params[0].exec(context) as NumberValue
                val end = args.params[1].exec(context) as NumberValue
                when (col) {
                    is StringValue -> StringValue(col.value.slice(start.value until end.value))
                    is ListValue -> ListValue(col.value.slice(start.value until end.value))
                    else -> throw IllegalArgumentException("Expected StringValue or ListValue, got ${col.javaClass.simpleName}")
                }
            }
            "toList" -> {
                when (val what = expr.exec(context)) {
                    is StringValue -> ListValue(listOf(what.value))
                    is NumberValue -> ListValue(listOf(what.value.toString()))
                    else -> throw IllegalArgumentException("Expected StringValue or NumberValue, got ${what.javaClass.simpleName}")
                }
            }
            "find" -> {
                NumberValue((expr.exec(context) as StringValue).value.indexOf((args.params[0].exec(context) as StringValue).value))
            }
            "contains" -> {
                NumberValue(if ((expr.exec(context) as ListValue).value.contains((args.params[0].exec(context) as StringValue).value)) 1 else 0)
            }
            "length" -> {
                when (val what = expr.exec(context)) {
                    is StringValue -> NumberValue(what.value.length)
                    is ListValue -> NumberValue(what.value.size)
                    else -> throw IllegalArgumentException("Expected StringValue or ListValue, got ${what.javaClass.simpleName}")
                }
            }
            "defined" -> {
                val idName = (expr as IdentifierNode).name
                NumberValue(if (context.table.get(idName) != null) 1 else 0)
            }
            else -> throw IllegalArgumentException("Unknown builtin function $func")
        }
    }

    override fun toString(): String {
        return "func($func, $expr, $args)"
    }
}

class UnitSubscriptNode(private val unit: Node, private val subscript: ExprNode) : Node {
    override fun exec(context: ExecutionContext): NodeValue {
        val value = unit.exec(context)
        val index = (subscript.exec(context) as NumberValue).value
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

class FactorNode(private val units: List<Node>, private val ops: List<String>, private val prefix: FactorPrefix) :
    Node {
    enum class FactorPrefix {
        NONE,
        NOT,
        NEGATIVE
    }

    override fun exec(context: ExecutionContext): NodeValue {
        val values = units.toMutableList()
        val ops = ops.toMutableList()
        var res = values.removeAt(0).exec(context)
        while (values.size > 0) {
            val next = values.removeAt(0).exec(context)
            val op = ops.removeAt(0)
            res = if (res is NumberValue && next is NumberValue) {
                when (op) {
                    "*" -> NumberValue(res.value * next.value)
                    "/" -> NumberValue(res.value / next.value)
                    "%" -> NumberValue(res.value % next.value)
                    else -> throw IllegalArgumentException("Unknown operator $op")
                }
            } else if (res is StringValue && next is NumberValue && op == "*") {
                StringValue(res.value.repeat(next.value))
            } else if (res is ListValue && next is NumberValue && op == "*") {
                val sz = res.value.size
                ListValue(List(next.value * sz) { index -> (res as ListValue).value[index % sz] })
            } else {
                throw IllegalArgumentException("Invalid operation: $res $op $next")
            }
        }
        when (prefix) {
            FactorPrefix.NONE -> return res
            FactorPrefix.NOT -> return NumberValue(if (res.toBoolean()) 0 else 1)
            FactorPrefix.NEGATIVE -> {
                if (res is NumberValue) {
                    res = NumberValue(-res.value)
                } else {
                    throw IllegalArgumentException("Invalid operation: -$res")
                }
            }
        }
        return res
    }

    override fun toString(): String {
        var str = ""
        for (i in units.indices) {
            str += units[i].toString()
            if (i < ops.size) {
                str += ops[i]
            }
        }
        return "factor($str)"
    }
}

class TermNode(private val factors: List<Node>, private val ops: List<String>) : Node {
    override fun exec(context: ExecutionContext): NodeValue {
        val values = factors.toMutableList()
        val ops = ops.toMutableList()
        var res = values.removeAt(0).exec(context)
        while (values.size > 0) {
            val next = values.removeAt(0).exec(context)
            val op = ops.removeAt(0)
            if (res is NumberValue && next is NumberValue) {
                res = when (op) {
                    "+" -> NumberValue(res.value + next.value)
                    "-" -> NumberValue(res.value - next.value)
                    else -> throw IllegalArgumentException("Unknown operator $op")
                }
            } else if (op == "+") {
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
                str += ops[i]
            }
        }
        return "term($str)"
    }
}

class ExprNode(private val terms: List<Node>, private val ops: List<String>) : Node {
    override fun exec(context: ExecutionContext): NodeValue {
        val values = terms.toMutableList()
        val ops = ops.toMutableList()
        var res = values.removeAt(0).exec(context)
        while (values.size > 0) {
            if (res is NullValue) res = NumberValue(0)
            var next = values.removeAt(0).exec(context)
            next = if (next is NullValue) NumberValue(0) else next
            when (val op = ops.removeAt(0)) {
                "==" -> {
                    res = if (res is NumberValue && next is NumberValue) {
                        NumberValue(if (res.value == next.value) 1 else 0)
                    } else if (res is StringValue && next is StringValue) {
                        NumberValue(if (res.value == next.value) 1 else 0)
                    } else if (res is ListValue && next is ListValue) {
                        NumberValue(if (res.value == next.value) 1 else 0)
                    } else {
                        throw IllegalArgumentException("Invalid operation: $res == $next")
                    }
                }
                "!=" -> {
                    res = if (res is NumberValue && next is NumberValue) {
                        NumberValue(if (res.value != next.value) 1 else 0)
                    } else if (res is StringValue && next is StringValue) {
                        NumberValue(if (res.value != next.value) 1 else 0)
                    } else if (res is ListValue && next is ListValue) {
                        NumberValue(if (res.value != next.value) 1 else 0)
                    } else {
                        NumberValue(1)
                    }
                }
                "&&" -> res = NumberValue(if (res.toBoolean() && next.toBoolean()) 1 else 0)
                "||" -> res = NumberValue(if (res.toBoolean() || next.toBoolean()) 1 else 0)
                ">" -> res = if (res is NumberValue && next is NumberValue) {
                    NumberValue(if (res.value > next.value) 1 else 0)
                } else {
                    throw IllegalArgumentException("Invalid operation: $res > $next")
                }
                "<" -> res = if (res is NumberValue && next is NumberValue) {
                    NumberValue(if (res.value < next.value) 1 else 0)
                } else {
                    throw IllegalArgumentException("Invalid operation: $res < $next")
                }
                ">=" -> res = if (res is NumberValue && next is NumberValue) {
                    NumberValue(if (res.value >= next.value) 1 else 0)
                } else {
                    throw IllegalArgumentException("Invalid operation: $res >= $next")
                }
                "<=" -> res = if (res is NumberValue && next is NumberValue) {
                    NumberValue(if (res.value <= next.value) 1 else 0)
                } else {
                    throw IllegalArgumentException("Invalid operation: $res <= $next")
                }
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
                str += ops[i]
            }
        }
        return "expr($str)"
    }
}

class StmtAssignNode(private val identifier: IdentifierNode, private val expr: Node) : Node {
    override fun exec(context: ExecutionContext): NodeValue {
        val value = expr.exec(context)
        context.table.set(identifier.name, value)
        return NullValue()
    }

    override fun toString(): String {
        return "assign($identifier, $expr)"
    }
}

class StmtActionNode(private val action: Token, private val expr: ExprNode) : Node {
    override fun exec(context: ExecutionContext): NodeValue {
        if (action.type != TokenType.ACTION) {
            throw IllegalArgumentException("Expected ACTION, got ${action.type}")
        }
        val value = expr.exec(context)
        when (action.value) {
            "say" -> {
                context.say(value.toString())
            }
            else -> throw IllegalArgumentException("Unknown action ${action.value}")
        }
        return NullValue()
    }

    override fun toString(): String {
        return "action(${action.value}, $expr)"
    }
}

class StmtIfNode(
    private val condition: Node,
    private val ifBody: StmtListNode,
    private val elseBody: StmtListNode? = null
) : Node {
    override fun exec(context: ExecutionContext): NodeValue {
        if (condition.exec(context).toBoolean()) {
            ifBody.exec(context)
        } else {
            elseBody?.exec(context)
        }
        return NullValue()
    }

    override fun toString(): String {
        val elseText = if (elseBody == null) "" else ", else($elseBody)"
        return "if($condition, body($ifBody)$elseText)"
    }
}

class StmtListNode(private val stmts: List<Node>) : Node {
    override fun exec(context: ExecutionContext): NodeValue {
        for (node in stmts) {
            node.exec(context)
        }
        return NullValue()
    }

    override fun toString(): String {
        return "stmts(${stmts.joinToString(", ")})"
    }
}

class Parser(private val tokens: List<Token>) {
    private var current = 0

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

    private fun parseIdentifier() = IdentifierNode(consume(TokenType.IDENTIFIER))
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
                val assignToken = consume(TokenType.ASSIGN)
                val stmt = when (assignToken.value) {
                    "=" -> StmtAssignNode(identifier, parseExpr())
                    "+=" -> StmtAssignNode(identifier, TermNode(listOf(identifier, parseExpr()), listOf("+")))
                    "-=" -> StmtAssignNode(identifier, TermNode(listOf(identifier, parseExpr()), listOf("-")))
                    "*=" -> StmtAssignNode(
                        identifier,
                        FactorNode(listOf(identifier, parseExpr()), listOf("*"), FactorNode.FactorPrefix.NONE)
                    )
                    "/=" -> StmtAssignNode(
                        identifier,
                        FactorNode(listOf(identifier, parseExpr()), listOf("/"), FactorNode.FactorPrefix.NONE)
                    )
                    "%=" -> StmtAssignNode(
                        identifier,
                        FactorNode(listOf(identifier, parseExpr()), listOf("%"), FactorNode.FactorPrefix.NONE)
                    )
                    else -> throw IllegalStateException("Unexpected token $assignToken")
                }
                consume(TokenType.SEMICOLON)
                return stmt
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
                val ifBody = parseIfBody()
                if (peek().type == TokenType.ELSE) {
                    consume(TokenType.ELSE)
                    val elseBody = parseIfBody()
                    return StmtIfNode(condition, ifBody, elseBody)
                }
                return StmtIfNode(condition, ifBody)
            }
            else -> throw IllegalStateException("Unexpected token ${token.value}")
        }
    }

    private fun parseIfBody(): StmtListNode {
        val token = peek()
        return when (token.type) {
            TokenType.BRACE_OPEN -> {
                consume(TokenType.BRACE_OPEN)
                val stmts = parseStmtList()
                consume(TokenType.BRACE_CLOSE)
                stmts
            }
            else -> {
                val stmt = parseStmt()
                StmtListNode(listOf(stmt))
            }
        }
    }

    private fun parseExpr(): ExprNode {
        val terms = mutableListOf(parseTerm())
        val ops = mutableListOf<String>()
        while (true) {
            val token = peek()
            when (token.type) {
                TokenType.LOGIC_OP -> {
                    ops.add(consume(TokenType.LOGIC_OP).value)
                    terms.add(parseTerm())
                }
                else -> return ExprNode(terms, ops)
            }
        }
    }

    private fun parseTerm(): TermNode {
        val factors = mutableListOf(parseFactor())
        val ops = mutableListOf<String>()
        while (true) {
            val token = peek()
            when (token.type) {
                TokenType.ADD_OP -> {
                    ops.add(consume(TokenType.ADD_OP).value)
                    factors.add(parseFactor())
                }
                else -> return TermNode(factors, ops)
            }
        }
    }

    private fun parseFactor(): FactorNode {
        var prefix = FactorNode.FactorPrefix.NONE
        if (peek().type == TokenType.ADD_OP && peek().value == "-") {
            consume(TokenType.ADD_OP)
            prefix = FactorNode.FactorPrefix.NEGATIVE
        } else if (peek().type == TokenType.NOT) {
            consume(TokenType.NOT)
            prefix = FactorNode.FactorPrefix.NOT
        }
        val units = mutableListOf(parseUnit())
        val ops = mutableListOf<String>()
        while (true) {
            val token = peek()
            when (token.type) {
                TokenType.MULT_OP -> {
                    ops.add(consume(TokenType.MULT_OP).value)
                    units.add(parseUnit())
                }
                else -> return FactorNode(units, ops, prefix)
            }
        }
    }

    private fun parseUnitHead(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.IDENTIFIER -> parseIdentifier()
            TokenType.NUMBER -> parseNumber()
            TokenType.STRING -> parseString()
            TokenType.PAREN_OPEN -> {
                consume(TokenType.PAREN_OPEN)
                val expr = parseExpr()
                consume(TokenType.PAREN_CLOSE)
                expr
            }
            else -> throw IllegalStateException("Unexpected token ${token.value}")
        }
    }

    private fun parseParamList(): ParamListNode {
        val params = mutableListOf<ExprNode>()
        if (peek().type != TokenType.PAREN_CLOSE) {
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
        var unit = parseUnitHead()
        while (peek().type == TokenType.DOT || peek().type == TokenType.BRACKET_OPEN) {
            unit = parseUnitTail(unit)
        }
        return unit
    }

    fun parse(): Node {
        return parseStmtList()
    }
}

interface ExecutionContext {
    val table: SymbolTable
    fun say(text: String)
}

class ConsoleContext(st: SymbolTable? = null) : ExecutionContext {
    override val table: SymbolTable

    init {
        this.table = st ?: SymbolTable()
    }

    override fun say(text: String) {
        println(text)
    }
}

class Interpreter(source: String) {
    private val ast: Node

    init {
        val tokens = Tokenizer(source).scan()
        val parser = Parser(tokens)
        ast = parser.parse()
    }

    fun run(context: ExecutionContext) {
        ast.exec(context)
    }
}

fun main() {
    val inputs = mutableListOf<String>()
    while (true) {
        print("> ")
        val line = readLine() ?: break
        inputs.add(line)
    }
    val input = inputs.joinToString("\n")
    println(input)
    val context = ConsoleContext(SymbolTable(mapOf("text" to StringValue("this is a brand new world the world of parsing"))))
//    println("\nTokenizing...")
//    val tokens = Tokenizer(input).scan()
//    println(tokens.joinToString(", "))
//    println("\nParsing...")
//    val ast = Parser(tokens).parse()
//    println(ast)
//    println("\nExecuting...")
//    ast.exec(context)
    val interpreter = Interpreter(input)
    interpreter.run(context)
    println("\nDone!")
}