package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import top.saucecode.yqlang.Constants
import top.saucecode.yqlang.ExecutionContext
import top.saucecode.yqlang.Node.ListNode
import top.saucecode.yqlang.Node.Node
import top.saucecode.yqlang.Node.ReturnException
import top.saucecode.yqlang.Runtime.Pointer

interface ConvertibleToCallableProcedure {
    fun call(context: ExecutionContext, pc: Int, args: Pointer): NodeValue
}

@Serializable
sealed class ProcedureValue(protected val params: ListNode) : PassByReferenceNodeValue(), ConvertibleToCallableProcedure {
    override fun toBoolean(): Boolean = true
    protected abstract fun execute(context: ExecutionContext): NodeValue
    fun call(context: ExecutionContext, pc: Int, caller: Pointer?, args: Pointer): NodeValue {
        val res: NodeValue
        try {
            context.referenceEnvironment.pushFrame()
            context.memory.pushFrame(0, caller, args)
            params.toPattern(context).assign(context, args)
            res = execute(context)
        } finally {
            context.referenceEnvironment.popScope()
            val savedPc = context.memory.popFrame()
        }
        return res
    }

    override fun call(context: ExecutionContext, pc: Int, args: Pointer): NodeValue {
        return call(context, pc, null, args)
    }
}

@Serializable
class BoundProcedureValue(private val procedure: Pointer, private val self: Pointer) : PassByValueNodeValue(), ConvertibleToCallableProcedure {
    override val debugStr: String
        get() = "BoundProcedure"
    override val printStr: String
        get() = debugStr
    override fun toBoolean(): Boolean = true
    override fun call(context: ExecutionContext, pc: Int, args: Pointer): NodeValue {
        val procedureValue = context.memory[procedure] as ProcedureValue
        return procedureValue.call(context, pc, self, args)
    }
}

@Serializable(with = BuiltinProcedureValue.Serializer::class)
class BuiltinProcedureValue(
    private val name: String,
    params: ListNode,
    private val func: (context: ExecutionContext) -> NodeValue
) : ProcedureValue(params) {
    override val debugStr: String
        get() = "builtin($name)"
    override val printStr: String
        get() = debugStr
    override fun execute(context: ExecutionContext): NodeValue {
        return try {
            func(context)
        } catch (e: ReturnException) {
            e.value
        }
    }

    class Serializer : KSerializer<BuiltinProcedureValue> {
        override val descriptor = buildClassSerialDescriptor("top.saucecode.yqlang.NodeValue.BuiltinProcedureValue") {
            element<String>("name")
        }

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
            val name = decodeStringElement(descriptor, 0)
            Constants.builtinProcedures[name]!!
        }

        override fun serialize(encoder: Encoder, value: BuiltinProcedureValue) = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.name)
        }
    }
}

@Serializable(with = NodeProcedureValue.Serializer::class)
class NodeProcedureValue(private val func: Node, params: ListNode) : ProcedureValue(params) {
    override val debugStr: String
        get() = "procedure($func($params))"
    override val printStr: String
        get() = debugStr
    override fun execute(context: ExecutionContext): NodeValue {
        return try {
            func.exec(context)
        } catch (e: ReturnException) {
            e.value
        }
    }

    class Serializer : KSerializer<NodeProcedureValue> {
        override val descriptor = buildClassSerialDescriptor("top.saucecode.yqlang.NodeValue.NodeProcedureValue") {
            element<Node>("func")
            element<ListNode>("params")
        }

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
            NodeProcedureValue(
                decodeSerializableElement(descriptor, 0, Node.serializer()),
                decodeSerializableElement(descriptor, 1, ListNode.serializer())
            )
        }

        override fun serialize(encoder: Encoder, value: NodeProcedureValue) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, Node.serializer(), value.func)
            encodeSerializableElement(descriptor, 1, ListNode.serializer(), value.params)
        }
    }
}