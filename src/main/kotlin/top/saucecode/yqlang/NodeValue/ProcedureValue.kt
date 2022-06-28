package top.saucecode.yqlang.NodeValue

import kotlinx.serialization.Serializable
import top.saucecode.yqlang.Runtime.Pointer

@Serializable
data class ClosureValue(val captureList: Pointer, val entry: Int) : NodeValue() {
    override fun toBoolean(): Boolean = true
    override val debugStr: String
        get() = "closure($captureList, $entry)"
    override val printStr: String
        get() = debugStr
}
