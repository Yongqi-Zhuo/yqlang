package top.saucecode.NodeValue

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import top.saucecode.Constants
import top.saucecode.ExecutionContext
import top.saucecode.Node.ListNode
import top.saucecode.Node.Node

@Serializable
sealed class ProcedureValue(protected val params: ListNode, protected var self: NodeValue?) : NodeValue() {
    override fun toBoolean(): Boolean = true
    abstract fun execute(context: ExecutionContext): NodeValue
    fun call(context: ExecutionContext, args: ListValue): NodeValue {
        val res: NodeValue
        try {
            context.stack.push(args)
            res = execute(context)
        } finally {
            context.stack.pop()
        }
        return res
    }

    fun bind(self: NodeValue?): ProcedureValue {
        this.self = self
        return this
    }

    abstract fun copy(): ProcedureValue
}

@Serializable(with = BuiltinProcedureValue.Serializer::class)
class BuiltinProcedureValue(
    private val name: String,
    params: ListNode,
    private val func: (context: ExecutionContext) -> NodeValue,
    self: NodeValue?
) : ProcedureValue(params, self) {
    override fun toString(): String = "builtin($name)"
    override fun execute(context: ExecutionContext): NodeValue {
        context.stack.nameArgs(context, params, self)
        return func(context)
    }

    override fun copy(): ProcedureValue {
        return BuiltinProcedureValue(name, params, func, self)
    }

    class Serializer : KSerializer<BuiltinProcedureValue> {
        override val descriptor = buildClassSerialDescriptor("top.saucecode.NodeValue.BuiltinProcedureValue") {
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
class NodeProcedureValue(private val func: Node, params: ListNode, self: NodeValue?) :
    ProcedureValue(params, self) {
    override fun toString() = "procedure($func)"
    override fun execute(context: ExecutionContext): NodeValue {
        context.stack.nameArgs(context, params, self)
        return func.exec(context)
    }

    override fun copy(): ProcedureValue {
        return NodeProcedureValue(func, params, self)
    }

    class Serializer : KSerializer<NodeProcedureValue> {
        override val descriptor = buildClassSerialDescriptor("top.saucecode.NodeValue.NodeProcedureValue") {
            element<Node>("func")
            element<ListNode>("params")
        }

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
            NodeProcedureValue(
                decodeSerializableElement(descriptor, 0, Node.serializer()),
                decodeSerializableElement(descriptor, 1, ListNode.serializer()),
                null
            )
        }

        override fun serialize(encoder: Encoder, value: NodeProcedureValue) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, Node.serializer(), value.func)
            encodeSerializableElement(descriptor, 1, ListNode.serializer(), value.params)
        }
    }
}