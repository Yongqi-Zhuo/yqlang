package top.saucecode.yqlang

import top.saucecode.yqlang.Node.*

open class ParserException(message: String) : Exception(message)
class UnexpectedTokenException(val token: Token, private val expected: TokenType? = null) :
    ParserException(if (expected != null) "Unexpected token $token, expected $expected" else "Unexpected token $token")

class Parser {
    private var tokens: List<Token> = listOf()
    private var current = 0

    private fun consume(type: TokenType): Token {
        if (isAtEnd()) {
            throw ParserException("Unexpected end of input")
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

    private fun parseIdentifier(scope: Scope): IdentifierNode = IdentifierNode(scope, consume(TokenType.IDENTIFIER))
    private fun parseIdentifierName(scope: Scope): String = IdentifierNode(scope, consume(TokenType.IDENTIFIER)).name
    private fun parseNumber(scope: Scope): ExprNode {
        val numTok = consume(TokenType.NUMBER_LITERAL)
        return if ('.' in numTok.value) {
            FloatNode(scope, numTok)
        } else {
            IntegerNode(scope, numTok)
        }
    }

    private fun parseStmtList(scope: Scope, newScope: Boolean): StmtListNode {
        val stmts = mutableListOf<Node>()
        consumeLineBreak()
        val new = if (newScope) Scope(scope, null) else scope
        while (peek().type != TokenType.EOF && peek().type != TokenType.BRACE_CLOSE) {
            stmts.add(parseStmt(new))
        }
        return StmtListNode(new, stmts)
    }

    // calls consumeLineBreak() in the end of this function
    private fun parseStmt(scope: Scope): Node {
        val token = peek()
        return when (token.type) {
            TokenType.ACTION -> {
                parseStmtAction(scope)
            }
            TokenType.IF -> {
                parseStmtIf(scope)
            }
            TokenType.INIT -> {
                consume(TokenType.INIT)
                consumeLineBreak()
                StmtInitNode(scope, parseStmt(scope))
            }
            TokenType.BRACE_OPEN -> {
                consume(TokenType.BRACE_OPEN)
                val stmtList = parseStmtList(scope, true)
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                stmtList
            }
            TokenType.FUNC -> {
                consume(TokenType.FUNC)
                val func = parseIdentifier(scope)
                func.declareConsume(true)
                consume(TokenType.PAREN_OPEN)
                val newScope = Scope(scope, Frame(scope.currentFrame))
                val params = parseExprList(newScope)
                params.declareConsume(true)
                consume(TokenType.PAREN_CLOSE)
                consumeLineBreak()
                val body = parseStmt(newScope) // func and params may be quoted by body, so must be first declared!
                val closure = ClosureNode(newScope, params, body)
                StmtAssignNode(scope, func, closure)
            }
            TokenType.RETURN -> {
                consume(TokenType.RETURN)
                val retNode = StmtReturnNode(scope, parseExpr(scope))
                consumeLineBreak()
                retNode
            }
            TokenType.WHILE -> {
                consume(TokenType.WHILE)
                val condition = parseExpr(scope)
                consumeLineBreak()
                val body = parseStmt(scope)
                StmtWhileNode(scope, condition, body)
            }
            TokenType.CONTINUE -> {
                consume(TokenType.CONTINUE)
                consumeLineBreak()
                StmtContinueNode(scope)
            }
            TokenType.BREAK -> {
                consume(TokenType.BREAK)
                consumeLineBreak()
                StmtBreakNode(scope)
            }
            TokenType.FOR -> {
                consume(TokenType.FOR)
                val newScope = Scope(scope, null)
                val iterator = parseTerm(newScope)
                iterator.declareConsume(true)
                consume(TokenType.IN)
                val collection = parseExpr(newScope)
                consumeLineBreak()
                val body = parseStmt(newScope)
                StmtForNode(newScope, iterator, collection, body)
            }
            else -> {
                val expr = parseExpr(scope)
                if (peek().type == TokenType.ASSIGN) {
                    parseStmtAssign(scope, expr)
                } else {
                    consumeLineBreak()
                    StmtExprNode(scope, expr)
                }
            }
        }
    }

    private fun parseStmtAssign(scope: Scope, lvalue: ExprNode): Node {
        val assignToken = consume(TokenType.ASSIGN)
        val stmt = when (assignToken.value) {
            "=" -> StmtAssignNode(scope, lvalue, parseExpr(scope))
            "+=" -> StmtOpAssignNode(scope, lvalue, OpAssignCode.ADD_ASSIGN, parseExpr(scope))
            "-=" -> StmtOpAssignNode(scope, lvalue, OpAssignCode.SUB_ASSIGN, parseExpr(scope))
            "*=" -> StmtOpAssignNode(scope, lvalue, OpAssignCode.MUL_ASSIGN, parseExpr(scope))
            "/=" -> StmtOpAssignNode(scope, lvalue, OpAssignCode.DIV_ASSIGN, parseExpr(scope))
            "%=" -> StmtOpAssignNode(scope, lvalue, OpAssignCode.MOD_ASSIGN, parseExpr(scope))
            else -> throw UnexpectedTokenException(assignToken)
        }
        consumeLineBreak()
        return stmt
    }

    private fun parseStmtAction(scope: Scope): Node {
        val action = consume(TokenType.ACTION)
        val expr = parseExpr(scope)
        consumeLineBreak()
        return StmtActionNode(scope, action, expr)
    }

    private fun parseStmtIf(scope: Scope): Node {
        consume(TokenType.IF)
        val condition = parseExpr(scope)
        consumeLineBreak()
        val ifBody = parseStmt(scope)
        if (peek().type == TokenType.ELSE) {
            consume(TokenType.ELSE)
            consumeLineBreak()
            val elseBody = parseStmt(scope)
            return StmtIfNode(scope, condition, ifBody, elseBody)
        }
        return StmtIfNode(scope, condition, ifBody)
    }

    // assume that the brace has not been consumed
    private fun checkObjectLiteral(): Boolean {
        var pointer = current + 1
        var foundKey = false
        while (pointer < tokens.size) {
            val tokenType = tokens[pointer].type
            if (tokenType == TokenType.NEWLINE) {
                pointer++
                continue
            }
            if (!foundKey) {
                if (tokenType == TokenType.IDENTIFIER) {
                    foundKey = true
                } else {
                    return false
                }
            } else {
                return tokenType == TokenType.COLON
            }
            pointer++
        }
        return false
    }

    private fun parseExpr(scope: Scope): ExprNode {
        if ((peek().type == TokenType.BRACE_OPEN && !checkObjectLiteral()) || peek().type == TokenType.FUNC) {
            return parseLambda(scope)
        }
        return parseOperator(scope)
    }

    private fun parseOperator(scope: Scope, precedence: Int = OperatorNode.PrecedenceList.lastIndex): ExprNode {
        if (precedence < 0) {
            return parseTerm(scope)
        }
        val op = OperatorNode.PrecedenceList[precedence]
        return when (op.opType) {
            OperatorNode.OperatorType.UNARY -> {
                if (peek().type in op.operators) {
                    val unaryOp = consume(peek().type)
                    val next = parseOperator(scope, precedence - 1)
                    UnaryOperatorNode(scope, next, unaryOp.type)
                } else {
                    parseOperator(scope, precedence - 1)
                }
            }
            OperatorNode.OperatorType.BINARY -> {
                val nodes = mutableListOf(parseOperator(scope, precedence - 1))
                val ops = mutableListOf<TokenType>()
                while (peek().type in op.operators) {
                    ops.add(consume(peek().type).type)
                    nodes.add(parseOperator(scope, precedence - 1))
                }
                if (ops.size == 0) nodes[0] else {
                    if (op.isAndOr) LogicBinaryOperatorNode(scope, nodes, ops, op.isAnd)
                    else BinaryOperatorNode(scope, nodes, ops)
                }
            }
        }
    }

    // Assume that the brace has been consumed
    private fun checkLambdaParams(): Boolean {
        var pointer = current
        var braceDepth = 0
        while (pointer < tokens.size) {
            when (tokens[pointer].type) {
                TokenType.BRACE_OPEN -> braceDepth++
                TokenType.BRACE_CLOSE -> {
                    braceDepth--
                    if (braceDepth < 0) {
                        return false
                    }
                }
                TokenType.ARROW -> {
                    if (braceDepth == 0) {
                        return true
                    }
                }
                else -> {}
            }
            pointer++
        }
        return false
    }

    private fun parseLambda(scope: Scope): ClosureNode {
        return when (peek().type) {
            TokenType.FUNC -> {
                consume(TokenType.FUNC)
                val newScope = Scope(scope, Frame(scope.currentFrame))
                val params = if (peek().type == TokenType.PAREN_OPEN) {
                    consume(TokenType.PAREN_OPEN)
                    val ls = parseExprList(newScope)
                    consume(TokenType.PAREN_CLOSE)
                    ls
                } else {
                    ListNode(newScope, listOf())
                }
                params.declareConsume(true)
                consumeLineBreak()
                val body = parseStmt(newScope)
                ClosureNode(newScope, params, body)
            }
            TokenType.BRACE_OPEN -> {
                val newScope = Scope(scope, Frame(scope.currentFrame))
                consume(TokenType.BRACE_OPEN)
                val params = if (checkLambdaParams()) {
                    // lambda with params
                    val paramList = parseExprList(newScope)
                    consume(TokenType.ARROW)
                    paramList
                } else ListNode(newScope, emptyList())
                params.declareConsume(true)
                consumeLineBreak()
                val body = parseStmt(newScope)
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                ClosureNode(newScope, params, body)
            }
            else -> throw UnexpectedTokenException(peek(), TokenType.FUNC)
        }
    }

    private fun parseTermHead(scope: Scope): ExprNode {
        val token = peek()
        return when (token.type) {
            TokenType.IDENTIFIER -> parseIdentifier(scope)
            TokenType.NUMBER_LITERAL -> parseNumber(scope)
            TokenType.STRING_LITERAL -> StringNode(scope, consume(TokenType.STRING_LITERAL))
            TokenType.BOOLEAN_LITERAL -> BooleanNode(scope, consume(TokenType.BOOLEAN_LITERAL))
            TokenType.NULL -> NullNode(scope, consume(TokenType.NULL))
            TokenType.PAREN_OPEN -> {
                consume(TokenType.PAREN_OPEN)
                val expr = parseOperator(scope)
                consume(TokenType.PAREN_CLOSE)
                expr
            }
            TokenType.BRACKET_OPEN -> { // list literal
                consume(TokenType.BRACKET_OPEN)
                val list = parseExprList(scope)
                consume(TokenType.BRACKET_CLOSE)
                list
            }
            TokenType.BRACE_OPEN -> { // object literal
                consume(TokenType.BRACE_OPEN)
                consumeLineBreak()
                val obj = if (peek().type == TokenType.BRACE_CLOSE) {
                    ObjectNode(scope, emptyList())
                } else {
                    val k = parseIdentifierName(scope)
                    consume(TokenType.COLON)
                    val items = mutableListOf(k to parseExpr(scope))
                    while (peek().type != TokenType.BRACE_CLOSE) {
                        consume(TokenType.COMMA)
                        val key = parseIdentifierName(scope)
                        consume(TokenType.COLON)
                        val expr = parseExpr(scope)
                        items.add(key to expr)
                    }
                    ObjectNode(scope, items)
                }
                consume(TokenType.BRACE_CLOSE)
                consumeLineBreak()
                obj
            }
            else -> throw UnexpectedTokenException(token)
        }
    }

    private fun parseExprList(scope: Scope): ListNode {
        val params = mutableListOf<ExprNode>()
        val delimiters = listOf(TokenType.PAREN_CLOSE, TokenType.BRACKET_CLOSE, TokenType.ARROW)
        if (peek().type !in delimiters) {
            params.add(parseExpr(scope))
            while (peek().type !in delimiters) {
                consume(TokenType.COMMA)
                params.add(parseExpr(scope))
            }
        }
        return ListNode(scope, params)
    }

    private fun parseTermTail(scope: Scope, termHead: ExprNode): ExprNode {
        val token = peek()
        return when (token.type) {
            TokenType.DOT -> { // attribute access
                consume(TokenType.DOT)
                val attribute = IdentifierNode(scope, consume(TokenType.IDENTIFIER))
                AttributeAccessNode(scope, termHead, attribute.name)
            }
            TokenType.BRACKET_OPEN -> { // subscript access
                consume(TokenType.BRACKET_OPEN)
                val begin = if (peek().type == TokenType.COLON) {
                    IntegerNode(scope, 0)
                } else parseExpr(scope)
                val subscript = if (peek().type == TokenType.COLON) {
                    consume(TokenType.COLON)
                    if (peek().type == TokenType.BRACKET_CLOSE) {
                        SubscriptNode(scope, begin, true, null)
                    } else {
                        SubscriptNode(scope, begin, true, parseExpr(scope))
                    }
                } else {
                    SubscriptNode(scope, begin, false)
                }
                consume(TokenType.BRACKET_CLOSE)
                SubscriptAccessNode(scope, termHead, subscript)
            }
            TokenType.PAREN_OPEN -> { // function call
                consume(TokenType.PAREN_OPEN)
                val params = parseExprList(scope)
                consume(TokenType.PAREN_CLOSE)
                FunctionCallNodeFactory(scope, termHead, params).build()
            }
            else -> termHead
        }
    }

    private fun parseTerm(scope: Scope): ExprNode {
        var term = parseTermHead(scope)
        while (peek().type == TokenType.DOT || peek().type == TokenType.BRACKET_OPEN || peek().type == TokenType.PAREN_OPEN) {
            term = parseTermTail(scope, term)
        }
        return term
    }

    fun parse(tokens: List<Token>): Node {
        this.tokens = tokens
        this.current = 0
        // in case exception is thrown
//        while (text.size > lastStatics) text.removeLast()
//        val newStatics = text.subList(lastStatics, text.size)
//        lastStatics = text.size
        val scope = Scope(null, Frame(null))
        val ast = parseStmtList(scope, false)
        return ast
    }
}