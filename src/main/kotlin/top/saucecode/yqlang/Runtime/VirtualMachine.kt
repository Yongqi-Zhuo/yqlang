package top.saucecode.yqlang.Runtime

import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.Node.ActionCode
import top.saucecode.yqlang.Node.TypeMismatchRuntimeException
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.YqlangException
import java.util.*

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

}

enum class ImmediateCode(val code: Int) {
    NULL(0), FALSE(1), TRUE(2);
    companion object {
        fun fromCode(code: Int): ImmediateCode {
            return ImmediateCode.values().first { it.code == code }
        }
    }
}
data class ByteCode(val op: Int, val operand: Int = 0)

open class YqlangRuntimeException(message: String) : YqlangException(message)
class PatternMatchingConstantUnmatchedException : YqlangRuntimeException("Pattern matching failed: constant unmatched")
class InterruptedException : YqlangRuntimeException("Interrupted")

// TODO: implement +=, -=,...
class VirtualMachine(val executionContext: ExecutionContext) {
    val memory: Memory = executionContext.memory // separate memory from ExecContext. memory includes text.
    val text: List<ByteCode> = listOf()
    val labels = mutableListOf<Int>()
    var pc = 0
    var register: Pointer? = null
    val iteratorStack: Stack<Iterator<Pointer>> = Stack()
    fun jump(label: Int) {
        // to avoid recursion, check thread status
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        pc = labels[label] - 1
    }
    fun execute() {
        while (true) {
            val byteCode = text[pc]
            when (byteCode.op) {
                Op.LOAD_LOCAL_PUSH.code -> memory.push(memory.getLocal(byteCode.operand))
                Op.COPY_PUSH.code -> memory.push(memory.copy(byteCode.operand))
                Op.POP_SAVE_LOCAL.code -> memory.copyTo(memory.pop(), memory.getLocal(byteCode.operand))
                Op.POP_ASSERT_EQ.code -> if (memory[memory.pop()] != memory[byteCode.operand]) throw PatternMatchingConstantUnmatchedException()
                Op.CONS_PUSH.code -> {
                    val list = mutableListOf<Pointer>()
                    for (i in 0 until byteCode.operand) {
                        list.add(memory.pop())
                    }
                    list.reverse()
                    memory.push(memory.allocate(ListValue(list, memory).reference))
                }
                Op.EXTRACT_LIST.code -> {
                    val uncheckedList = memory[memory.pop()]
                    val list = uncheckedList.asList()?.value ?: throw TypeMismatchRuntimeException(listOf(ListValue::class.java), uncheckedList)
                    val expectedLength = byteCode.operand
                    for (i in list.size until expectedLength) {
                        memory.push(memory.allocate(NullValue))
                    }
                    list.take(expectedLength).reversed().forEach { memory.push(it) }
                }
                Op.SUBSCRIPT_PUSH.code -> {
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
                Op.CONS_OBJ_PUSH.code -> {
                    val list = mutableListOf<Pair<String, Pointer>>()
                    for (i in 0 until byteCode.operand) {
                        val value = memory.pop()
                        val uncheckedKey = memory[memory.pop()]
                        val key = uncheckedKey.asString()?.value ?: throw TypeMismatchRuntimeException(listOf(StringValue::class.java), uncheckedKey)
                        list.add(key to value)
                    }
                    list.reverse()
                    val obj = ObjectValue(list.toMap(mutableMapOf()), memory)
                    memory.push(memory.allocate(obj.reference))
                }
                Op.PUSH_IMM.code -> {
                    when (ImmediateCode.fromCode(byteCode.operand)) {
                        ImmediateCode.NULL -> memory.push(memory.allocate(NullValue))
                        ImmediateCode.FALSE -> memory.push(memory.allocate(BooleanValue(false)))
                        ImmediateCode.TRUE -> memory.push(memory.allocate(BooleanValue(true)))
                    }
                }
                Op.POP_ASSERT_EQ_IMM.code -> {
                    val constCode = ImmediateCode.fromCode(byteCode.operand)
                    val value = memory[memory.pop()]
                    when (constCode) {
                        ImmediateCode.NULL -> if (value != NullValue) throw PatternMatchingConstantUnmatchedException()
                        ImmediateCode.FALSE -> if (value != BooleanValue(false)) throw PatternMatchingConstantUnmatchedException()
                        ImmediateCode.TRUE -> if (value != BooleanValue(true)) throw PatternMatchingConstantUnmatchedException()
                    }
                }
                Op.ACTION.code -> {
                    val actionCode = byteCode.operand
                    val target = memory[memory.pop()]
                    when (ActionCode.fromCode(actionCode)) {
                        ActionCode.SAY -> executionContext.say(target.printStr)
                        ActionCode.NUDGE -> target.asInteger()?.let { executionContext.nudge(it) } ?: throw TypeMismatchRuntimeException(listOf(IntegerValue::class.java), target)
                        ActionCode.PICSAVE -> target.asString()?.value?.let { executionContext.picSave(it) } ?: throw TypeMismatchRuntimeException(listOf(StringValue::class.java), target)
                        ActionCode.PICSEND -> target.asString()?.value?.let { executionContext.picSend(it) } ?: throw TypeMismatchRuntimeException(listOf(StringValue::class.java), target)
                    }
                }
                Op.JUMP.code -> jump(byteCode.operand)
                Op.CREATE_CLOSURE.code -> {
                    val closure = ClosureValue(memory.pop(), byteCode.operand)
                    memory.push(memory.allocate(closure))
                }
                Op.PREPARE_FRAME.code -> {
                    // now on stack: lastBp, retAddr, caller, args, captures. Now expand captures
                    val captures = memory[memory.pop()].asList()?.value ?: throw YqlangRuntimeException("Failed to pass captures. This should not happen.")
                    captures.forEach { memory.push(it) } // pass by reference!
                    repeat(byteCode.operand) { memory.push(memory.allocate(NullValue)) }
                    // any need to push something, in order that the return value won't be $?
                }
                Op.GET_NTH_ARG.code -> {
                    val nth = memory.allocate(NullValue)
                    memory[memory.args].asList()?.value?.getOrNull(byteCode.operand)?.let { memory.copyTo(it, nth) } ?: throw YqlangRuntimeException("Failed to get argument.")
                    memory.push(nth)
                }
                Op.POP_RETURN.code -> {
                    val retVal = memory.pop() // just pass the last result
                    val label = memory.popFrame()
                    memory.push(retVal)
                    jump(label)
                }
                Op.CALL.code -> {
                    val args = memory.pop()
                    val uncheckedClosure = memory[memory.pop()]
                    val closure = uncheckedClosure as? ClosureValue ?: throw TypeMismatchRuntimeException(listOf(ClosureValue::class.java), uncheckedClosure)
                    val caller = memory.pop()
                    val retAddr = byteCode.operand
                    memory.pushFrame(retAddr, caller, args, closure.captureList)
                    jump(closure.entry)
                }
                Op.NOP.code -> { } // do nothing
                Op.JUMP_ZERO.code -> if (!memory[memory.pop()].toBoolean()) jump(byteCode.operand)
                Op.JUMP_NOT_FIRST_RUN.code -> if (!executionContext.firstRun) jump(byteCode.operand)
                Op.POP.code -> memory.pop()
                Op.RETURN.code -> {
                    val label = memory.popFrame()
                    (register ?: memory.allocate(NullValue)).let { memory.push(it) }
                    jump(label)
                }
                Op.POP_SAVE_TO_REG.code -> register = memory.pop()
                Op.CLEAR_REG.code -> register = null
                Op.PUSH_ITERATOR.code -> iteratorStack.push((memory[memory.pop()] as Iterable<Pointer>).iterator())
                Op.JUMP_IF_ITER_DONE.code -> if (!iteratorStack.peek().hasNext()) jump(byteCode.operand)
                Op.POP_ITERATOR.code -> iteratorStack.pop()
                Op.ITER_NEXT_PUSH.code -> memory.push(iteratorStack.peek().next())
                else -> throw YqlangRuntimeException("Invalid byte code $byteCode")
            }
            pc++
        }
    }
}