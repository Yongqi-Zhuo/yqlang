package top.saucecode.yqlang.Runtime

import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.Node.ActionCode
import top.saucecode.yqlang.Node.TypeMismatchRuntimeException
import top.saucecode.yqlang.NodeValue.*
import top.saucecode.yqlang.YqlangException

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

    // operand: return value. -1 if none.
    // pop current frame and jump to the return address.
    RETURN(16),

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
    val memory: Memory = executionContext.memory
    val text: List<ByteCode> = listOf()
    val labels = mutableListOf<Int>()
    var pc = 0
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
                Op.JUMP.code -> {
                    pc = labels[byteCode.operand]
                    continue
                }
                Op.CREATE_CLOSURE.code -> {
                    val closure = ClosureValue(memory.pop(), byteCode.operand)
                    memory.push(memory.allocate(closure))
                }
                Op.PREPARE_FRAME.code -> {
                    // to avoid recursion, check thread status
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
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
                Op.RETURN.code -> {
                    var retVal = byteCode.operand // pass by reference?
                    if (retVal == -1) {
                        retVal = memory.pop() // just pass the last result
                    }
                    val label = memory.popFrame()
                    memory.push(retVal)
                    pc = labels[label]
                    continue
                }
                else -> throw YqlangRuntimeException("Invalid byte code $byteCode")
            }
            pc++
        }
    }
}