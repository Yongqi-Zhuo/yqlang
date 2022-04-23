package top.saucecode

import top.saucecode.Node.*
import top.saucecode.NodeValue.NodeProcedureValue
import top.saucecode.NodeValue.NodeValue
import top.saucecode.NodeValue.ProcedureValue
import top.saucecode.NodeValue.toNodeValue

class UnexpectedTokenException(val token: Token, private val expected: TokenType? = null) : Exception() {
    override fun toString(): String {
        return if (expected != null) "Unexpected token $token, expected $expected" else "Unexpected token $token"
    }

    override val message: String
        get() = toString()
}

class Parser(private val tokens: List<Token>) {
    private var current = 0

    private fun consume(type: TokenType): Token {
        if (isAtEnd()) {
            throw IllegalStateException("Unexpected end of input")
        }
        if (peek().type != type) {
            throw UnexpectedTokenException(peek(), type)
        }
        return tokens[current++]
    }

    private fun consumeLineBreak() {
        while (true) {
            when (peek().type) {
                TokenType.SEMICOLON -> consume(TokenType.SEMICOLON)
                TokenType.NEWLINE -> consume(TokenType.NEWLINE)
                else -> return
            }
        }
    }

    private fun isAtEnd() = current >= tokens.size
    private fun peek() = tokens[current]
    private fun peekNext() = if (current < tokens.size - 1) tokens[current + 1] else null
    private fun peekNextNext() = if (current < tokens.size - 2) tokens[current + 2] else null

    private fun parseIdentifier() = IdentifierNode(consume(TokenType.IDENTIFIER))
    private fun parseNumber() = NumberNode(consume(TokenType.NUMBER))
    private fun parseString() = StringNode(consume(TokenType.STRING))

    private fun parseStmtList(newScope: Boolean): StmtListNode {
        val stmts = mutableListOf<Node>()
        consumeLineBreak()
        while (peek().type != TokenType.EOF && peek().type != TokenType.BRACE_CLOSE) {
            stmts.add(parseStmt())
        }
        return StmtListNode(stmts, newScope)
    }

    // calls consumeLineBreak() in the end of this function
    private fun parseStmt(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.ACTION -> {
                parseStmtAction()
            }
            TokenType.IF -> {
                parseStmtIf()
            }
            TokenType.INIT -> {
                consume(TokenType.INIT)
                consumeLineBreak()
                StmtInitNode(parseStmt())
            }
            TokenType.BRACE_OPEN -> {
                consume(TokenType.BRACE_OPEN)
                val stmtList = parseStmtList(true)
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                stmtList
            }
            TokenType.FUNC -> {
                consume(TokenType.FUNC)
                val func = parseIdentifier()
                consume(TokenType.PAREN_OPEN)
                val params = mutableListOf<IdentifierNode>()
                if (peek().type != TokenType.PAREN_CLOSE) {
                    params.add(parseIdentifier())
                    while (peek().type != TokenType.PAREN_CLOSE) {
                        consume(TokenType.COMMA)
                        params.add(parseIdentifier())
                    }
                }
                consume(TokenType.PAREN_CLOSE)
                consumeLineBreak()
                val body = parseStmt()
                declarations[func.name] = { obj -> NodeProcedureValue(StmtFuncNode(body), params.map { it.name }, obj) }
                func
            }
            TokenType.RETURN -> {
                consume(TokenType.RETURN)
                consumeLineBreak()
                StmtReturnNode(parseExpr())
            }
            TokenType.WHILE -> {
                consume(TokenType.WHILE)
                val condition = parseExpr()
                consumeLineBreak()
                val body = parseStmt()
                StmtWhileNode(condition, body)
            }
            TokenType.CONTINUE -> {
                consume(TokenType.CONTINUE)
                consumeLineBreak()
                StmtContinueNode()
            }
            TokenType.BREAK -> {
                consume(TokenType.BREAK)
                consumeLineBreak()
                StmtBreakNode()
            }
            TokenType.FOR -> {
                consume(TokenType.FOR)
                val iterator = parseTerm()
                consume(TokenType.IN)
                val collection = parseExpr()
                consumeLineBreak()
                val body = parseStmt()
                StmtForNode(iterator, collection, body)
            }
            else -> {
                val expr = parseExpr()
                if (peek().type == TokenType.ASSIGN) {
                    parseStmtAssign(expr)
                } else {
                    consumeLineBreak()
                    expr
                }
            }
        }
    }

    private fun parseStmtAssign(lvalue: Node): Node {
        val assignToken = consume(TokenType.ASSIGN)
        val stmt = when (assignToken.value) {
            "=" -> StmtAssignNode(lvalue, parseExpr())
            "+=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.PLUS)))
            "-=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.MINUS)))
            "*=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.MULT)))
            "/=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.DIV)))
            "%=" -> StmtAssignNode(lvalue, BinaryOperatorNode(listOf(lvalue, parseExpr()), listOf(TokenType.MOD)))
            else -> throw UnexpectedTokenException(assignToken)
        }
        consumeLineBreak()
        return stmt
    }

    private fun parseStmtAction(): Node {
        val action = consume(TokenType.ACTION)
        val expr = parseExpr()
        consumeLineBreak()
        return StmtActionNode(action, expr)
    }

    private fun parseStmtIf(): Node {
        consume(TokenType.IF)
        val condition = parseExpr()
        consumeLineBreak()
        val ifBody = parseStmt()
        if (peek().type == TokenType.ELSE) {
            consume(TokenType.ELSE)
            consumeLineBreak()
            val elseBody = parseStmt()
            return StmtIfNode(condition, ifBody, elseBody)
        }
        return StmtIfNode(condition, ifBody)
    }

    private fun parseExpr(): Node {
        if ((peek().type == TokenType.BRACE_OPEN && peekNext()?.type != TokenType.BRACE_CLOSE && peekNextNext()?.type != TokenType.COLON) || peek().type == TokenType.FUNC) {
            return parseLambda()
        }
        return parseOperator()
    }

    private fun parseOperator(precedence: Int = OperatorNode.PrecedenceList.lastIndex): Node {
        if (precedence < 0) {
            return parseTerm()
        }
        val op = OperatorNode.PrecedenceList[precedence]
        return when (op.opType) {
            OperatorNode.OperatorType.UNARY -> {
                if (peek().type in op.operators) {
                    val unaryOp = consume(peek().type)
                    val next = parseOperator(precedence - 1)
                    UnaryOperatorNode(next, unaryOp.type)
                } else {
                    parseOperator(precedence - 1)
                }
            }
            OperatorNode.OperatorType.BINARY -> {
                val nodes = mutableListOf(parseOperator(precedence - 1))
                val ops = mutableListOf<TokenType>()
                while (peek().type in op.operators) {
                    ops.add(consume(peek().type).type)
                    nodes.add(parseOperator(precedence - 1))
                }
                BinaryOperatorNode(nodes, ops)
            }
        }
    }

    private fun parseLambda(): Node {
        return when (peek().type) {
            TokenType.FUNC -> {
                consume(TokenType.FUNC)
                consume(TokenType.PAREN_OPEN)
                val params = mutableListOf<IdentifierNode>()
                if (peek().type != TokenType.PAREN_CLOSE) {
                    params.add(parseIdentifier())
                    while (peek().type != TokenType.PAREN_CLOSE) {
                        consume(TokenType.COMMA)
                        params.add(parseIdentifier())
                    }
                }
                consume(TokenType.PAREN_CLOSE)
                consumeLineBreak()
                val body = parseStmt()
                NodeProcedureValue(
                    StmtFuncNode(body), params.map { it.name }, null
                ).toNode() // have to make sure caller is assigned to self
            }
            TokenType.BRACE_OPEN -> {
                consume(TokenType.BRACE_OPEN)
                val params = mutableListOf<IdentifierNode>()
                if (peek().type == TokenType.IDENTIFIER && (peekNext()?.type == TokenType.COMMA || peekNext()?.type == TokenType.ARROW)) {
                    // lambda with params
                    params.add(parseIdentifier())
                    while (peek().type != TokenType.ARROW) {
                        consume(TokenType.COMMA)
                        params.add(parseIdentifier())
                    }
                    consume(TokenType.ARROW)
                }
                consumeLineBreak()
                val body = parseStmt()
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                NodeProcedureValue(body, params.map { it.name }, null).toNode()
            }
            else -> throw UnexpectedTokenException(peek(), TokenType.FUNC)
        }
    }

    private fun parseTermHead(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.IDENTIFIER -> parseIdentifier()
            TokenType.NUMBER -> parseNumber()
            TokenType.STRING -> parseString()
            TokenType.PAREN_OPEN -> {
                consume(TokenType.PAREN_OPEN)
                val expr = parseOperator()
                consume(TokenType.PAREN_CLOSE)
                expr
            }
            TokenType.BRACKET_OPEN -> { // list literal
                consume(TokenType.BRACKET_OPEN)
                val list = parseParamList()
                consume(TokenType.BRACKET_CLOSE)
                list
            }
            TokenType.BRACE_OPEN -> { // object literal
                consume(TokenType.BRACE_OPEN)
                val obj = if (peek().type == TokenType.BRACE_CLOSE) {
                    ObjectNode(emptyList())
                } else {
                    val k = parseIdentifier()
                    consume(TokenType.COLON)
                    val items = mutableListOf(k to parseExpr())
                    while (peek().type != TokenType.BRACE_CLOSE) {
                        consume(TokenType.COMMA)
                        val key = parseIdentifier()
                        consume(TokenType.COLON)
                        val expr = parseExpr()
                        items.add(key to expr)
                    }
                    ObjectNode(items)
                }
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                obj
            }
            else -> throw UnexpectedTokenException(token)
        }
    }

    private fun parseParamList(): ListNode {
        val params = mutableListOf<Node>()
        if (peek().type != TokenType.PAREN_CLOSE && peek().type != TokenType.BRACKET_CLOSE) {
            params.add(parseExpr())
            while (peek().type != TokenType.PAREN_CLOSE && peek().type != TokenType.BRACKET_CLOSE) {
                consume(TokenType.COMMA)
                params.add(parseExpr())
            }
        }
        return ListNode(params)
    }

    private fun parseTermTail(termHead: Node): Node {
        val token = peek()
        return when (token.type) {
            TokenType.DOT -> {
                consume(TokenType.DOT)
                val attribute = IdentifierNode(consume(TokenType.IDENTIFIER))
                AccessViewNode(termHead, SubscriptNode(attribute.name.toNodeValue().toNode(), false))
            }
            TokenType.BRACKET_OPEN -> {
                consume(TokenType.BRACKET_OPEN)
                val begin = if (peek().type == TokenType.COLON) {
                    0.toNodeValue().toNode()
                } else parseExpr()
                val subscript = if (peek().type == TokenType.COLON) {
                    consume(TokenType.COLON)
                    if (peek().type == TokenType.BRACKET_CLOSE) {
                        SubscriptNode(begin, true, null)
                    } else {
                        SubscriptNode(begin, true, parseExpr())
                    }
                } else {
                    SubscriptNode(begin, false)
                }
                consume(TokenType.BRACKET_CLOSE)
                AccessViewNode(termHead, subscript)
            }
            TokenType.PAREN_OPEN -> {
                consume(TokenType.PAREN_OPEN)
                val params = parseParamList()
                consume(TokenType.PAREN_CLOSE)
                ProcedureNode(termHead, params)
            }
            else -> termHead
        }
    }

    private fun parseTerm(): Node {
        var term = parseTermHead()
        while (peek().type == TokenType.DOT || peek().type == TokenType.BRACKET_OPEN || peek().type == TokenType.PAREN_OPEN) {
            term = parseTermTail(term)
        }
        return term
    }

    private var declarations: MutableMap<String, (NodeValue) -> ProcedureValue> = mutableMapOf()
    fun parse(): Pair<Node, MutableMap<String, (NodeValue) -> ProcedureValue>> {
        declarations = mutableMapOf()
        return Pair(parseStmtList(false), declarations)
    }
}