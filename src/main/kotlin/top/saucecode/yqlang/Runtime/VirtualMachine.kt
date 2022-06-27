package top.saucecode.yqlang.Runtime

import top.saucecode.yqlang.CompileException
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
    // pop the top of stack and assert its value equals the pointee.
    POP_ASSERT_EQ(3),

    // operand: length of list.
    // form a list of certain length push its address to stack.
    CONS_PUSH(4),

    // operand: expected length of list.
    // pop this list on the top of stack and extract and push its elements in reverse order.
    // excessive elements are discarded, missing elements are filled with NullValue.
    EXTRACT_LIST(5),

    // operand: subscript type.
    // see SubscriptNode for more detail.
    SUBSCRIPT_PUSH(6),

    // operand: number of key-value pairs in the object.
    // form an object and push its address to stack.
    CONS_OBJ_PUSH(7),

    // operand: a constant code.
    // 0 => NullValue, 1 => BooleanValue(false), 2 => BooleanValue(true)
    PUSH_IMM(8),

    // operand: a constant code.
    // same as above.
    POP_ASSERT_EQ_IMM(9),

    // operand: action code.
    // see StmtActionNode for more detail.
    ACTION(10),

}

enum class ImmediateCode(val code: Int) {
    NULL(0), FALSE(1), TRUE(2);
    companion object {
        fun fromCode(code: Int): ImmediateCode {
            return ImmediateCode.values().first { it.code == code }
        }
    }
}
data class ByteCode(val op: Int, val operand1: Int = 0, val operand2: Int = 0)

open class YqlangRuntimeException(message: String) : YqlangException(message)
class PatternMatchingConstantUnmatchedException : YqlangRuntimeException("Pattern matching failed: constant unmatched")

// TODO: implement +=, -=,...
class VirtualMachine(val executionContext: ExecutionContext) {
    val memory: Memory = executionContext.memory
    val text: List<ByteCode> = listOf()
    var pc = 0
    fun execute() {
        while (true) {
            val byteCode = text[pc]
            when (byteCode.op) {
                Op.LOAD_LOCAL_PUSH.code -> memory.push(memory.getLocal(byteCode.operand1))
                Op.COPY_PUSH.code -> memory.push(memory.copy(byteCode.operand1))
                Op.POP_SAVE_LOCAL.code -> memory.copyTo(memory.pop(), memory.getLocal(byteCode.operand1))
                Op.POP_ASSERT_EQ.code -> if (memory[memory.pop()] != memory[byteCode.operand1]) throw PatternMatchingConstantUnmatchedException()
                Op.CONS_PUSH.code -> {
                    val list = mutableListOf<Pointer>()
                    for (i in 0 until byteCode.operand1) {
                        list.add(memory.pop())
                    }
                    list.reverse()
                    memory.push(memory.allocate(ListValue(list, memory).reference))
                }
                Op.EXTRACT_LIST.code -> {
                    val uncheckedList = memory[memory.pop()]
                    val list = uncheckedList.asList()?.value ?: throw TypeMismatchRuntimeException(listOf(ListValue::class.java), uncheckedList)
                    val expectedLength = byteCode.operand1
                    for (i in list.size until expectedLength) {
                        memory.push(memory.allocate(NullValue))
                    }
                    list.take(expectedLength).reversed().forEach { memory.push(it) }
                }
                Op.SUBSCRIPT_PUSH.code -> {
                    val subscriptType = byteCode.operand1
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
                    for (i in 0 until byteCode.operand1) {
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
                    when (ImmediateCode.fromCode(byteCode.operand1)) {
                        ImmediateCode.NULL -> memory.push(memory.allocate(NullValue))
                        ImmediateCode.FALSE -> memory.push(memory.allocate(BooleanValue(false)))
                        ImmediateCode.TRUE -> memory.push(memory.allocate(BooleanValue(true)))
                    }
                }
                Op.POP_ASSERT_EQ_IMM.code -> {
                    val constCode = ImmediateCode.fromCode(byteCode.operand1)
                    val value = memory[memory.pop()]
                    when (constCode) {
                        ImmediateCode.NULL -> if (value != NullValue) throw PatternMatchingConstantUnmatchedException()
                        ImmediateCode.FALSE -> if (value != BooleanValue(false)) throw PatternMatchingConstantUnmatchedException()
                        ImmediateCode.TRUE -> if (value != BooleanValue(true)) throw PatternMatchingConstantUnmatchedException()
                    }
                }
                Op.ACTION.code -> {
                    val actionCode = byteCode.operand1
                    val target = memory[memory.pop()]
                    when (ActionCode.fromCode(actionCode)) {
                        ActionCode.SAY -> executionContext.say(target.printStr)
                        ActionCode.NUDGE -> target.asInteger()?.let { executionContext.nudge(it) } ?: throw TypeMismatchRuntimeException(listOf(IntegerValue::class.java), target)
                        ActionCode.PICSAVE -> target.asString()?.value?.let { executionContext.picSave(it) } ?: throw TypeMismatchRuntimeException(listOf(StringValue::class.java), target)
                        ActionCode.PICSEND -> target.asString()?.value?.let { executionContext.picSend(it) } ?: throw TypeMismatchRuntimeException(listOf(StringValue::class.java), target)
                    }
                }
                else -> throw YqlangRuntimeException("Invalid byte code $byteCode")
            }
            pc++
        }
    }
}