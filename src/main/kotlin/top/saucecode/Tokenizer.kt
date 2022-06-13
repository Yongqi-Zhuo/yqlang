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

        fun scanString(delimiter: Char = '"', escape: Boolean = true): String {
            var str = ""
            var toEscape = false
            advance()
            while (index < input.length) {
                if (escape && toEscape) {
                    toEscape = false
                    when (currentChar) {
                        'n' -> str += '\n'
                        'r' -> str += '\r'
                        't' -> str += '\t'
                        '\\' -> str += '\\'
                        delimiter -> str += delimiter
                        else -> str += "\\$currentChar"
                    }
                } else if (escape && currentChar == '\\') {
                    toEscape = true
                } else if (currentChar == delimiter) {
                    break
                } else {
                    str += currentChar
                }
                advance()
            }
            advance()
            return str
        }

        var rawStringFlag = false

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
                    if (rawStringFlag) {
                        tokens.add(Token(TokenType.STRING, scanString('"', false)))
                        rawStringFlag = false
                    } else {
                        tokens.add(Token(TokenType.STRING, scanString('"', true)))
                    }
                }
                currentChar == '\'' -> {
                    if (rawStringFlag) {
                        tokens.add(Token(TokenType.STRING, scanString('\'', false)))
                        rawStringFlag = false
                    } else {
                        tokens.add(Token(TokenType.STRING, scanString('\'', true)))
                    }
                }
                currentChar.isDigit() -> {
                    val start = index
                    do {
                        advance()
                    } while (currentChar.isDigit() && index < input.length)
                    val value = input.substring(start, index)
                    tokens.add(Token(TokenType.NUMBER, value))
                }
                currentChar.isLetterOrDigit() || currentChar == '_' || currentChar == '$' -> {
                    if (currentChar == 'r' && index < input.length - 1 && (input[index + 1] in listOf('"', '\''))) {
                        rawStringFlag = true
                        advance()
                        continue
                    }
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
                        "picsave" -> tokens.add(Token(TokenType.ACTION, "picsave"))
                        "picsend" -> tokens.add(Token(TokenType.ACTION, "picsend"))
                        // "text" -> tokens.add(Token(TokenType.IDENTIFIER, "text")) // events are special identifiers
                        else -> tokens.add(Token(TokenType.IDENTIFIER, value))
                    }
                }
                currentChar == '#' -> break
                else -> {
                    advance()
                }
            }
        }
        tokens.add(Token(TokenType.EOF, "EOF"))
        return tokens
    }
}