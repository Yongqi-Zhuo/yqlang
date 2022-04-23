package top.saucecode

import kotlinx.serialization.Serializable

@Serializable
enum class TokenType {
    BRACE_OPEN, BRACE_CLOSE, PAREN_OPEN, PAREN_CLOSE, BRACKET_OPEN, BRACKET_CLOSE, // Braces and parentheses
    NEWLINE, SEMICOLON, COLON, ASSIGN, DOT, COMMA, INIT, // Statements
    IF, ELSE, FUNC, RETURN, WHILE, CONTINUE, BREAK, FOR, IN, // Control flow
    ARROW, // Lambda arrow
    // Operators begin
    NOT, // Unary NOT, unary MINUS cannot be discriminated by tokenizer
    MULT, DIV, MOD, // MULT_OP
    PLUS, // ADD_OP
    MINUS, // ADD_OP, but can be unary op
    GREATER, LESS, GREATER_EQ, LESS_EQ, // COMP_OP
    EQUAL, NOT_EQUAL, // EQ_OP
    LOGIC_AND, // LOGIC_OP
    LOGIC_OR, // LOGIC_OP
    // Operators end
    ACTION, IDENTIFIER, NUMBER, STRING, EOF
}

fun IntRange.safeSubscript(index: Int): Int? {
    val size = last - first + 1
    val i = if (index < 0) size + index else index
    if (i < 0 || i >= size) return null
    return i + first
}

fun IntRange.safeSlice(begin: Int, end: Int?): IntRange? {
    val size = last - first + 1
    val b = if (begin < 0) size + begin else begin
    val e = if (end == null) size else if (end < 0) size + end else if (end > size) size else end
    if (b >= e || b < 0) return null
    return first + b until first + e
}

@Serializable
data class Token(val type: TokenType, val value: String) {
    override fun toString(): String {
        return if (type == TokenType.STRING) "$type: \"$value\"" else "$type: $value"
    }
}