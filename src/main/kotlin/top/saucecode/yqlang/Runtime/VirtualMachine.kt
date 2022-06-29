package top.saucecode.yqlang.Runtime

import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.Node.*
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.Runtime.Op.*
import top.saucecode.yqlang.YqlangException
import java.util.*
import kotlinx.serialization.Serializable

enum class Op(val code: Int) {
    // operand: offset from bp.
    // load and push local variable to stack.
    LOAD_LOCAL_PUSH(0),
    LOAD_LOCAL_PUSH_REF(1),

    // operand: a pointer.
    // allocate on heap a copy of the pointee and push its address to stack.
    LOAD_PUSH(2),
    LOAD_PUSH_REF(3),

    // operand: offset from bp.
    // pop and save the top of stack to local variable.
    POP_SAVE_LOCAL(4),

    // operand: a pointer.
    // pop and save the top of stack to the pointee.
    POP_SAVE(5),

    // operand: a pointer.
    // pop the top of stack and assert its value equals the pointee.
    POP_ASSERT_EQ(6),

    // operand: length of list.
    // form a list of certain length push its address to stack.
    CONS_PUSH(7),

    // operand: expected length of list.
    // pop this list on the top of stack and extract and push its elements in reverse order.
    // excessive elements are discarded, missing elements are filled with NullValue.
    EXTRACT_LIST(8),

    // operand: subscript type.
    // see SubscriptNode for more detail.
    SUBSCRIPT_PUSH(9),

    // operand: number of key-value pairs in the object.
    // form an object and push its address to stack.
    CONS_OBJ_PUSH(10),

    // operand: a constant code.
    // 0 => NullValue, 1 => BooleanValue(false), 2 => BooleanValue(true)
    PUSH_IMM(11),

    // operand: a constant code.
    // same as above.
    POP_ASSERT_EQ_IMM(12),

    // operand: action code.
    // see StmtActionNode for more detail.
    ACTION(13),

    // operand: a label.
    // jump unconditionally to the label.
    JUMP(14),

    // operand: a label, which is the entry of the closure.
    // creates a closure by popping captures off the top of stack and pushing a closure to stack.
    CREATE_CLOSURE(15),

    // operand: number of local variables, excluding captures.
    // prepare a new frame for the function, by extracting captures and reserving local variables.
    PREPARE_FRAME(16),

    // operand: index of argument.
    // get the nth argument and copy it to stack.
    GET_NTH_ARG(17),
    GET_NTH_ARG_REF(18),

    // operand: none. the top of stack is viewed as return value.
    // pop current frame and jump to the return address.
    POP_RETURN(19),

    // operand: return address label.
    // calls the closure, assuming currently on stack: caller, closure, args.
    CALL(20),

    // operand: none.
    // no op.
    NOP(21),

    // operand: a label.
    // jump to the label if the top of stack is zero.
    JUMP_ZERO(22),

    // operand: a label.
    // jump to the label if the program is not first run.
    JUMP_NOT_FIRST_RUN(23),

    // operand: none.
    // pop.
    POP(24),

    // operand: none.
    // return null.
    RETURN(25),

    // operand: none.
    // pop the stack and save to register.
    POP_SAVE_TO_REG(26),

    // operand: none.
    // clear the register.
    CLEAR_REG(27),

    // operand: none.
    // get the iterator of the top of stack.
    PUSH_ITERATOR(28),

    // operator: a label.
    // jump if current iterator is exhausted.
    JUMP_IF_ITER_DONE(29),

    // operand: none.
    // pop current iterator.
    POP_ITERATOR(30),

    // operand: none.
    // get the next element of the current iterator.
    ITER_NEXT_PUSH(31),

    // operand: none.
    // create a new AccessView, assuming on stack: a collection.
    PUSH_ACCESS_VIEW(32),

    // operand: none.
    // extends the AccessView, assuming on stack: a subscript, and the AccessView on its proprietary stack.
    EXTEND_ACCESS_VIEW(33),

    // operand: none.
    // get the value of the AccessView, assuming the AccessView on its proprietary stack.
    ACCESS_GET(34),
    ACCESS_GET_REF(35),

    // operand: none.
    // set the value of the AccessView, assuming on stack: the rvalue and the AccessView on its proprietary stack.
    ACCESS_SET(36),

    // operand: binary operator code.
    // perform a binary operation on the first two on the top of stack.
    BINARY_OP(37),

    // operand: none.
    // converts the value on top of stack to boolean.
    TO_BOOL(38),

    // operand: a label.
    // jump to the label if the top of stack is not zero.
    JUMP_NOT_ZERO(39),

    // operand: unary operator code.
    // perform a unary operation on the top of stack.
    UNARY_OP(40),

    // operand: op assign code.
    // perform an op-assign operation.
    OP_ASSIGN(41),

    // operand: none
    // exit the program.
    EXIT(42);
    companion object {
        val printLength: Int = values().map { it.toString().length }.maxOf { it } + 1
        fun fromCode(code: Int): Op {
            return values()[code]
//            return values().firstOrNull { it.code == code } ?: throw YqlangRuntimeException("Unknown op code: $code")
        }
    }
}

enum class ImmediateCode(val code: Int) {
    NULL(0), FALSE(1), TRUE(2);
    companion object {
        fun fromCode(code: Int): ImmediateCode {
            return ImmediateCode.values().first { it.code == code }
        }
    }
}
@Serializable
data class ByteCode(val op: Int, val operand: Int = 0) {
    override fun toString(): String {
        return "${Op.fromCode(op).toString().padEnd(Op.printLength)}${operand.toString(16).padStart(8)}"
    }
}

open class YqlangRuntimeException(message: String) : YqlangException(message)
class PatternMatchingConstantUnmatchedException : YqlangRuntimeException("Pattern matching failed: constant unmatched")
class InterruptedException : YqlangRuntimeException("Interrupted")

// TODO: implement +=, -=,...
class VirtualMachine(val executionContext: ExecutionContext, val memory: Memory) {
    val text: List<ByteCode> = memory.text!!
    val labels = memory.labels!!
    var pc = 0
    var register: Pointer? = null
    val iteratorStack: Stack<Iterator<Pointer>> = Stack()
    val accessStack: Stack<AccessView> = Stack()
    fun jump(label: Int) {
        // to avoid recursion, check thread status
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        pc = labels[label] - 1
    }
    private inline fun performOp(op: NodeValue.(NodeValue) -> NodeValue) {
        val op2 = memory.pop()
        val op1 = memory.pop()
        memory.push(memory.allocate(memory[op1].op(memory[op2])))
    }
    private inline fun performOpAssign(op: NodeValue.(NodeValue) -> NodeValue) {
        // TODO: after getting rid of NodeValue, do not assign twice.
        val op2 = memory.pop()
        val op1 = memory.pop()
        memory[op1] = memory[op1].op(memory[op2])
    }
    fun execute() {
        while (pc < text.size) {
            val byteCode = text[pc]
//            println(byteCode)
            when (Op.fromCode(byteCode.op)) {
                LOAD_LOCAL_PUSH -> memory.push(memory.copy(memory.getLocal(byteCode.operand)))
                LOAD_LOCAL_PUSH_REF -> memory.push(memory.getLocal(byteCode.operand))
                LOAD_PUSH -> memory.push(memory.copy(byteCode.operand))
                LOAD_PUSH_REF -> memory.push(byteCode.operand)
                POP_SAVE_LOCAL -> memory.copyTo(memory.pop(), memory.getLocal(byteCode.operand))
                POP_SAVE -> memory.copyTo(memory.pop(), byteCode.operand)
                POP_ASSERT_EQ -> if (memory[memory.pop()] != memory[byteCode.operand]) throw PatternMatchingConstantUnmatchedException()
                CONS_PUSH -> {
                    val list = mutableListOf<Pointer>()
                    for (i in 0 until byteCode.operand) {
                        list.add(memory.pop())
                    }
                    list.reverse()
                    memory.push(memory.allocate(ListValue(list, memory).reference))
                }
                EXTRACT_LIST -> {
                    val uncheckedList = memory[memory.pop()]
                    val list = uncheckedList.asList()?.value 
                        ?: throw TypeMismatchRuntimeException(listOf(ListValue::class), uncheckedList)
                    val expectedLength = byteCode.operand
                    for (i in list.size until expectedLength) {
                        memory.push(memory.allocate(NullValue))
                    }
                    list.take(expectedLength).reversed().forEach { memory.push(it) }
                }
                SUBSCRIPT_PUSH -> {
                    val subscriptType = byteCode.operand
                    val end = if (subscriptType == 2) memory[memory.pop()] else null
                    val begin = memory[memory.pop()]
                    val extended = subscriptType > 0
                    val subscript = when (begin) {
                        is IntegerValue -> IntegerSubscriptValue(begin.value.toInt(), extended, end?.asInteger()?.toInt())
                        is ReferenceValue -> begin.asStringValue()?.value?.let { KeySubscriptValue(it) }
                        else -> null
                    } ?: throw TypeMismatchRuntimeException(listOf(IntegerValue::class, StringValue::class), begin)
                    memory.push(memory.allocate(subscript))
                }
                CONS_OBJ_PUSH -> {
                    val list = mutableListOf<Pair<String, Pointer>>()
                    repeat(byteCode.operand) {
                        val value = memory.pop()
                        val uncheckedKey = memory[memory.pop()]
                        val key = uncheckedKey.asString()?.value 
                            ?: throw TypeMismatchRuntimeException(listOf(StringValue::class), uncheckedKey)
                        list.add(key to value)
                    }
                    list.reverse()
                    val obj = ObjectValue(list.toMap(mutableMapOf()), memory)
                    memory.push(memory.allocate(obj.reference))
                }
                PUSH_IMM -> {
                    when (ImmediateCode.fromCode(byteCode.operand)) {
                        ImmediateCode.NULL -> memory.push(memory.allocate(NullValue))
                        ImmediateCode.FALSE -> memory.push(memory.allocate(BooleanValue(false)))
                        ImmediateCode.TRUE -> memory.push(memory.allocate(BooleanValue(true)))
                    }
                }
                POP_ASSERT_EQ_IMM -> {
                    val constCode = ImmediateCode.fromCode(byteCode.operand)
                    val value = memory[memory.pop()]
                    when (constCode) {
                        ImmediateCode.NULL -> if (value != NullValue) throw PatternMatchingConstantUnmatchedException()
                        ImmediateCode.FALSE -> if (value != BooleanValue(false)) throw PatternMatchingConstantUnmatchedException()
                        ImmediateCode.TRUE -> if (value != BooleanValue(true)) throw PatternMatchingConstantUnmatchedException()
                    }
                }
                ACTION -> {
                    val actionCode = byteCode.operand
                    val target = memory[memory.pop()]
                    when (ActionCode.fromCode(actionCode)) {
                        ActionCode.SAY -> executionContext.say(target.printStr)
                        ActionCode.NUDGE -> target.asInteger()?.let { executionContext.nudge(it) } 
                            ?: throw TypeMismatchRuntimeException(listOf(IntegerValue::class), target)
                        ActionCode.PICSAVE -> target.asString()?.value?.let { executionContext.picSave(it) } 
                            ?: throw TypeMismatchRuntimeException(listOf(StringValue::class), target)
                        ActionCode.PICSEND -> target.asString()?.value?.let { executionContext.picSend(it) } 
                            ?: throw TypeMismatchRuntimeException(listOf(StringValue::class), target)
                    }
                }
                JUMP -> jump(byteCode.operand)
                CREATE_CLOSURE -> {
                    val closure = ClosureValue(memory.pop(), byteCode.operand)
                    memory.push(memory.allocate(closure))
                }
                PREPARE_FRAME -> {
                    // now on stack: lastBp, retAddr, caller, args, captures. Now expand captures
                    val captures = memory[memory.pop()].asList()?.value 
                        ?: throw YqlangRuntimeException("Failed to pass captures. This should not happen.")
                    captures.forEach { memory.push(it) } // pass by reference!
                    repeat(byteCode.operand) { memory.push(memory.allocate(NullValue)) }
                }
                GET_NTH_ARG -> {
                    val nth = memory.allocate(NullValue)
                    val argId = byteCode.operand
                    memory[memory.args].asList()?.value?.getOrNull(argId)?.let { memory.copyTo(it, nth) }
                        ?: throw YqlangRuntimeException("Failed to get $argId: out of range.")
                    memory.push(nth)
                }
                GET_NTH_ARG_REF -> {
                    val argId = byteCode.operand
                    val nth = memory[memory.args].asList()?.value?.getOrNull(argId)
                        ?: throw YqlangRuntimeException("Failed to get $argId: out of range.")
                    memory.push(nth)
                }
                POP_RETURN -> {
                    val retVal = memory.pop() // just pass the last result
                    val label = memory.popFrame()
                    memory.push(retVal)
                    jump(label)
                }
                CALL -> {
                    // on stack: caller, closure, args.
                    val args = memory.pop()
                    val uncheckedClosure = memory[memory.pop()]
                    val closure = uncheckedClosure as? ClosureValue 
                        ?: throw TypeMismatchRuntimeException(listOf(ClosureValue::class), uncheckedClosure)
                    val caller = memory.pop()
                    val retAddr = byteCode.operand
                    memory.pushFrame(retAddr, caller, args, closure.captureList)
                    jump(closure.entry)
                }
                NOP -> { } // do nothing
                JUMP_ZERO -> if (!memory[memory.pop()].toBoolean()) jump(byteCode.operand)
                JUMP_NOT_FIRST_RUN -> if (!executionContext.firstRun) jump(byteCode.operand)
                POP -> memory.pop()
                RETURN -> {
                    val label = memory.popFrame()
                    (register ?: memory.allocate(NullValue)).let { memory.push(it) }
                    register = null
                    jump(label)
                }
                POP_SAVE_TO_REG -> register = memory.pop()
                CLEAR_REG -> register = null
                PUSH_ITERATOR ->
                    @Suppress("UNCHECKED_CAST") iteratorStack.push((memory[memory.pop()] as Iterable<Pointer>).iterator())
                JUMP_IF_ITER_DONE -> if (!iteratorStack.peek().hasNext()) jump(byteCode.operand)
                POP_ITERATOR -> iteratorStack.pop()
                ITER_NEXT_PUSH -> memory.push(iteratorStack.peek().next())
                PUSH_ACCESS_VIEW -> {
                    val uncheckedValue = memory[memory.pop()]
                    val value = (uncheckedValue as? ReferenceValue)?.value
                        ?: throw TypeMismatchRuntimeException(listOf(ReferenceValue::class), uncheckedValue)
                    val accessView = AccessView.create(value, null, memory)
                    accessStack.push(accessView)
                }
                EXTEND_ACCESS_VIEW -> accessStack.push(accessStack.pop().subscript(memory[memory.pop()] as? SubscriptValue
                    ?: throw YqlangRuntimeException("Failed to subscript access view. This should not happen.")))
                ACCESS_GET -> memory.push(accessStack.pop().exec(false))
                ACCESS_GET_REF -> memory.push(accessStack.pop().exec(true))
                ACCESS_SET -> accessStack.pop().assign(memory.pop())
                BINARY_OP -> {
                    when (BinaryOperatorCode.fromValue(byteCode.operand)) {
                        BinaryOperatorCode.ADD -> performOp(NodeValue::plus)
                        BinaryOperatorCode.SUB -> performOp(NodeValue::minus)
                        BinaryOperatorCode.MUL -> performOp(NodeValue::times)
                        BinaryOperatorCode.DIV -> performOp(NodeValue::div)
                        BinaryOperatorCode.MOD -> performOp(NodeValue::rem)
                        BinaryOperatorCode.EQUAL -> performOp { that -> BooleanValue(this == that) }
                        BinaryOperatorCode.NOT_EQUAL -> performOp { that -> BooleanValue(this != that) }
                        BinaryOperatorCode.GREATER -> performOp { that -> BooleanValue(this > that) }
                        BinaryOperatorCode.LESS -> performOp { that -> BooleanValue(this < that) }
                        BinaryOperatorCode.GREATER_EQ -> performOp { that -> BooleanValue(this >= that) }
                        BinaryOperatorCode.LESS_EQ -> performOp { that -> BooleanValue(this <= that) }
                        BinaryOperatorCode.LOGIC_AND -> performOp { that -> BooleanValue(this.toBoolean() && that.toBoolean()) }
                        BinaryOperatorCode.LOGIC_OR -> performOp { that -> BooleanValue(this.toBoolean() || that.toBoolean()) }
                        BinaryOperatorCode.IN -> performOp { that -> BooleanValue(this in that) }
                    }
                }
                TO_BOOL -> memory.push(memory.allocate(BooleanValue(memory[memory.pop()].toBoolean())))
                JUMP_NOT_ZERO -> if (memory[memory.pop()].toBoolean()) jump(byteCode.operand)
                UNARY_OP -> {
                    when (UnaryOperatorCode.fromValue(byteCode.operand)) {
                        UnaryOperatorCode.MINUS -> memory.push(memory.allocate(-memory[memory.pop()]))
                        UnaryOperatorCode.NOT -> memory.push(memory.allocate(!memory[memory.pop()]))
                    }
                }
                OP_ASSIGN -> {
                    when (OpAssignCode.from(byteCode.operand)) {
                        OpAssignCode.ADD_ASSIGN -> performOpAssign(NodeValue::addAssign)
                        OpAssignCode.SUB_ASSIGN -> performOpAssign(NodeValue::subAssign)
                        OpAssignCode.MUL_ASSIGN -> performOpAssign(NodeValue::mulAssign)
                        OpAssignCode.DIV_ASSIGN -> performOpAssign(NodeValue::divAssign)
                        OpAssignCode.MOD_ASSIGN -> performOpAssign(NodeValue::modAssign)
                    }
                }
                EXIT -> break
            }
            pc++
        }
    }
}