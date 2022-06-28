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

    // operand: a pointer.
    // allocate on heap a copy of the pointee and push its address to stack.
    COPY_PUSH(1),

    // operand: offset from bp.
    // pop and save the top of stack to local variable.
    POP_SAVE_LOCAL(2),

    // operand: a pointer.
    // pop and save the top of stack to the pointee.
    POP_SAVE(3),

    // operand: a pointer.
    // pop the top of stack and assert its value equals the pointee.
    POP_ASSERT_EQ(4),

    // operand: length of list.
    // form a list of certain length push its address to stack.
    CONS_PUSH(5),

    // operand: expected length of list.
    // pop this list on the top of stack and extract and push its elements in reverse order.
    // excessive elements are discarded, missing elements are filled with NullValue.
    EXTRACT_LIST(6),

    // operand: subscript type.
    // see SubscriptNode for more detail.
    SUBSCRIPT_PUSH(7),

    // operand: number of key-value pairs in the object.
    // form an object and push its address to stack.
    CONS_OBJ_PUSH(8),

    // operand: a constant code.
    // 0 => NullValue, 1 => BooleanValue(false), 2 => BooleanValue(true)
    PUSH_IMM(9),

    // operand: a constant code.
    // same as above.
    POP_ASSERT_EQ_IMM(10),

    // operand: action code.
    // see StmtActionNode for more detail.
    ACTION(11),

    // operand: a label.
    // jump unconditionally to the label.
    JUMP(12),

    // operand: a label, which is the entry of the closure.
    // creates a closure by popping captures off the top of stack and pushing a closure to stack.
    CREATE_CLOSURE(13),

    // operand: number of local variables, excluding captures.
    // prepare a new frame for the function, by extracting captures and reserving local variables.
    PREPARE_FRAME(14),

    // operand: index of argument.
    // get the nth argument and copy it to stack.
    GET_NTH_ARG(15),

    // operand: none. the top of stack is viewed as return value.
    // pop current frame and jump to the return address.
    POP_RETURN(16),

    // operand: return address label.
    // calls the closure, assuming currently on stack: caller, closure, args.
    CALL(17),

    // operand: none.
    // no op.
    NOP(18),

    // operand: a label.
    // jump to the label if the top of stack is zero.
    JUMP_ZERO(19),

    // operand: a label.
    // jump to the label if the program is not first run.
    JUMP_NOT_FIRST_RUN(20),

    // operand: none.
    // pop.
    POP(21),

    // operand: none.
    // return null.
    RETURN(22),

    // operand: none.
    // pop the stack and save to register.
    POP_SAVE_TO_REG(23),

    // operand: none.
    // clear the register.
    CLEAR_REG(24),

    // operand: none.
    // get the iterator of the top of stack.
    PUSH_ITERATOR(25),

    // operator: a label.
    // jump if current iterator is exhausted.
    JUMP_IF_ITER_DONE(26),

    // operand: none.
    // pop current iterator.
    POP_ITERATOR(27),

    // operand: none.
    // get the next element of the current iterator.
    ITER_NEXT_PUSH(28),

    // operand: none.
    // create a new AccessView, assuming on stack: a collection.
    PUSH_ACCESS_VIEW(29),

    // operand: none.
    // extends the AccessView, assuming on stack: a subscript, and the AccessView on its proprietary stack.
    EXTEND_ACCESS_VIEW(30),

    // operand: none.
    // get the value of the AccessView, assuming the AccessView on its proprietary stack.
    ACCESS_GET(31),

    // operand: none.
    // set the value of the AccessView, assuming on stack: the rvalue and the AccessView on its proprietary stack.
    ACCESS_SET(32),

    // operand: binary operator code.
    // perform a binary operation on the first two on the top of stack.
    BINARY_OP(33),

    // operand: none.
    // converts the value on top of stack to boolean.
    TO_BOOL(34),

    // operand: a label.
    // jump to the label if the top of stack is not zero.
    JUMP_NOT_ZERO(35),

    // operand: unary operator code.
    // perform a unary operation on the top of stack.
    UNARY_OP(36),

    // operand: none
    // exit the program.
    EXIT(127);
    companion object {
        fun fromCode(code: Int): Op {
            return values().firstOrNull { it.code == code } ?: throw YqlangRuntimeException("Unknown op code: $code")
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
        return "${Op.fromCode(op)} $operand"
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
    inline fun performOp(op: NodeValue.(NodeValue) -> NodeValue) {
        memory.push(memory.allocate(memory[memory.pop()].op(memory[memory.pop()])))
    }
    fun execute() {
        while (true) {
            val byteCode = text[pc]
            val opCode = Op.fromCode(byteCode.op)
            when (opCode) {
                LOAD_LOCAL_PUSH -> memory.push(memory.getLocal(byteCode.operand))
                COPY_PUSH -> memory.push(memory.copy(byteCode.operand))
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
                        ?: throw TypeMismatchRuntimeException(listOf(ListValue::class.java), uncheckedList)
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
                    } ?: throw TypeMismatchRuntimeException(listOf(IntegerValue::class.java, StringValue::class.java), begin)
                    memory.push(memory.allocate(subscript))
                }
                CONS_OBJ_PUSH -> {
                    val list = mutableListOf<Pair<String, Pointer>>()
                    for (i in 0 until byteCode.operand) {
                        val value = memory.pop()
                        val uncheckedKey = memory[memory.pop()]
                        val key = uncheckedKey.asString()?.value 
                            ?: throw TypeMismatchRuntimeException(listOf(StringValue::class.java), uncheckedKey)
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
                            ?: throw TypeMismatchRuntimeException(listOf(IntegerValue::class.java), target)
                        ActionCode.PICSAVE -> target.asString()?.value?.let { executionContext.picSave(it) } 
                            ?: throw TypeMismatchRuntimeException(listOf(StringValue::class.java), target)
                        ActionCode.PICSEND -> target.asString()?.value?.let { executionContext.picSend(it) } 
                            ?: throw TypeMismatchRuntimeException(listOf(StringValue::class.java), target)
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
                    memory[memory.args].asList()?.value?.getOrNull(byteCode.operand)?.let { memory.copyTo(it, nth) } 
                        ?: throw YqlangRuntimeException("Failed to get argument.")
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
                        ?: throw TypeMismatchRuntimeException(listOf(ClosureValue::class.java), uncheckedClosure)
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
                        ?: throw TypeMismatchRuntimeException(listOf(ReferenceValue::class.java), uncheckedValue)
                    val accessView = AccessView.create(value, null, memory)
                    accessStack.push(accessView)
                }
                EXTEND_ACCESS_VIEW -> accessStack.push(accessStack.pop().subscript(memory[memory.pop()] as? SubscriptValue
                    ?: throw YqlangRuntimeException("Failed to subscript access view. This should not happen.")))
                ACCESS_GET -> memory.push(accessStack.pop().exec())
                ACCESS_SET -> accessStack.pop().assign(memory.pop())
                BINARY_OP -> when (BinaryOperatorCode.fromValue(byteCode.operand)) {
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
                TO_BOOL -> memory.push(memory.allocate(BooleanValue(memory[memory.pop()].toBoolean())))
                JUMP_NOT_ZERO -> if (memory[memory.pop()].toBoolean()) jump(byteCode.operand)
                UNARY_OP -> when (UnaryOperatorCode.fromValue(byteCode.operand)) {
                    UnaryOperatorCode.MINUS -> memory.push(memory.allocate(-memory[memory.pop()]))
                    UnaryOperatorCode.NOT -> memory.push(memory.allocate(!memory[memory.pop()]))
                }
                EXIT -> break
            }
            pc++
        }
    }
}