package top.saucecode.yqlang.Node

import top.saucecode.yqlang.*

sealed class Node(val scope: Scope) {
    abstract fun generateCode(buffer: CodegenContext)
}

class ExpressionCannotBeAssignedException(expression: Node) : CompileException("Expression $expression cannot be assigned")

sealed class ExprNode(scope: Scope) : Node(scope) {
    enum class CodeGenExprType {
        // CONSUME must not rebind! all binds happen at compile time.
        PRODUCE_VALUE, CONSUME, PRODUCE_REFERENCE
    }
    protected lateinit var codeGenExprType: CodeGenExprType
    private var declared = false
    fun declareProduce(isReference: Boolean) {
        if (declared) return
        declared = true
        codeGenExprType = if (isReference) CodeGenExprType.PRODUCE_REFERENCE else CodeGenExprType.PRODUCE_VALUE
        prepareProduce(isReference)
    }
    fun declareConsume(allBinds: Boolean) {
        if (declared) return
        declared = true
        codeGenExprType = CodeGenExprType.CONSUME
        prepareConsume(allBinds)
    }
    protected abstract fun prepareProduce(isReference: Boolean)
    protected abstract fun prepareConsume(allBinds: Boolean)
}