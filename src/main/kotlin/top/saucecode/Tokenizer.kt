package top.saucecode

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
                currentChar == '\n' -> pushAndAdvance(Token(TokenType.NEWLINE, "\n"))
                currentChar == ':' -> pushAndAdvance(Token(TokenType.COLON, ":"))
                currentChar == '.' -> pushAndAdvance(Token(TokenType.DOT, "."))
                currentChar == ',' -> pushAndAdvance(Token(TokenType.COMMA, ","))
                currentChar == '*' -> handleTwoCharOp(TokenType.ASSIGN, "*=", TokenType.MULT)
                currentChar == '%' -> handleTwoCharOp(TokenType.ASSIGN, "%=", TokenType.MOD)
                currentChar == '+' -> handleTwoCharOp(TokenType.ASSIGN, "+=", TokenType.PLUS)
                currentChar == '-' -> {
                    if (index < input.length - 1 && input[index + 1] == '>') {
                        tokens.add(Token(TokenType.ARROW, "->"))
                        advance()
                        advance()
                    } else {
                        handleTwoCharOp(TokenType.ASSIGN, "-=", TokenType.MINUS)
                    }
                }
                currentChar == '>' -> handleTwoCharOp(TokenType.GREATER_EQ, ">=", TokenType.GREATER)
                currentChar == '<' -> handleTwoCharOp(TokenType.LESS_EQ, "<=", TokenType.LESS)
                currentChar == '=' -> handleTwoCharOp(TokenType.EQUAL, "==", TokenType.ASSIGN)
                currentChar == '!' -> handleTwoCharOp(TokenType.NOT_EQUAL, "!=", TokenType.NOT)
                currentChar == '&' -> handleTwoCharOp(TokenType.LOGIC_AND, "&&")
                currentChar == '|' -> handleTwoCharOp(TokenType.LOGIC_OR, "||")
                currentChar == '/' -> {
                    if (index == input.length - 1) {
                        pushAndAdvance(Token(TokenType.DIV, "/"))
                    } else {
                        if (input[index + 1] == '/') {
                            while (index < input.length && currentChar != '\n') {
                                advance()
                            }
                        } else {
                            handleTwoCharOp(TokenType.ASSIGN, "/=", TokenType.DIV)
                        }
                    }
                }
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
                currentChar == '\'' -> {
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
                                '\'' -> str += '\''
                                else -> str += "\\$currentChar"
                            }
                        } else if (currentChar == '\\') {
                            escape = true
                        } else if (currentChar == '\'') {
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
                currentChar.isLetter() || currentChar == '_' || currentChar == '$' -> {
                    val start = index
                    do {
                        advance()
                    } while ((currentChar.isLetterOrDigit() || currentChar == '_') && index < input.length)
                    when (val value = input.substring(start, index)) {
                        "if" -> tokens.add(Token(TokenType.IF, "if"))
                        "else" -> tokens.add(Token(TokenType.ELSE, "else"))
                        "func" -> tokens.add(Token(TokenType.FUNC, "func"))
                        "return" -> tokens.add(Token(TokenType.RETURN, "return"))
                        "while" -> tokens.add(Token(TokenType.WHILE, "while"))
                        "continue" -> tokens.add(Token(TokenType.CONTINUE, "continue"))
                        "break" -> tokens.add(Token(TokenType.BREAK, "break"))
                        "for" -> tokens.add(Token(TokenType.FOR, "for"))
                        "in" -> tokens.add(Token(TokenType.IN, "in"))
                        "init" -> tokens.add(Token(TokenType.INIT, "init"))
                        "say" -> tokens.add(Token(TokenType.ACTION, "say"))
                        "nudge" -> tokens.add(Token(TokenType.ACTION, "nudge"))
                        // "text" -> tokens.add(Token(TokenType.IDENTIFIER, "text")) // events are special identifiers
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