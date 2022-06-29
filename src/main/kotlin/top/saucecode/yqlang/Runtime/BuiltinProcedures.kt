package top.saucecode.yqlang.Runtime

import top.saucecode.yqlang.BuiltinException
import top.saucecode.yqlang.Constants
import top.saucecode.yqlang.NoSuchMethodException
import top.saucecode.yqlang.Node.TypeMismatchRuntimeException
import top.saucecode.yqlang.NodeValue.*

fun VirtualMachine.split(): NodeValue {
    val str = caller.asString()?.value ?: throw BuiltinException()
    val arg = argOrNull(0)
    if (arg == null) {
        return Constants.whiteSpace.split(str).filter { it.isNotEmpty() }.toStringListReference(memory)
    } else {
        if (arg.isStringReference())
            return str.split(arg.asString()!!.value).toStringListReference(memory)
        else if (arg is RegExValue)
            return arg.split(str).toStringListReference(memory)
    }
    throw NoSuchMethodException(caller, "split")
}
fun VirtualMachine.join(): NodeValue {
    val list = caller
    return if (list is Iterable<*>) {
        if (args.size == 0) {
            list.joinToString(" ").toStringValueReference(memory)
        } else {
            val sep = arg(0).asString() ?: throw BuiltinException()
            list.joinToString(sep.value).toStringValueReference(memory)
        }
    } else throw NoSuchMethodException(caller, "join")
}
fun VirtualMachine.find(): NodeValue {
    val expr = caller
    val arg = arg(0)
    if (expr.isStringReference()) {
        if (arg.isStringReference())
            return expr.asString()!!.value.indexOf(arg.asString()!!.value).toIntegerValue()
        else if (arg is RegExValue)
            return arg.find(expr.asString()!!.value)
    }
    else if (expr.isListReference())
        return expr.asList()!!.value.map { it.load(memory) }.indexOf(arg).toIntegerValue()
    throw NoSuchMethodException(caller, "find")
}
fun VirtualMachine.findAll(): NodeValue {
    val expr = caller
    val arg = arg(0)
    if (expr.isStringReference()) {
        val str = expr.asString()!!.value
        if (arg.isStringReference()) {
            val sub = arg.asString()!!.value
            return str.indices.filter { str.substring(it).startsWith(sub) }.toIntegerListReference(memory)
        }
        else if (arg is RegExValue) {
            return arg.findAll(str).toListValueReference(memory)
        }
    }
    else if (expr.isListReference()) {
        val list = expr.asList()!!.value
        return list.indices.filter { list[it].load(memory) == arg }.toIntegerListReference(memory)
    }
    throw NoSuchMethodException(caller, "findAll")
}
fun VirtualMachine.contains(): NodeValue {
    val expr = caller
    val arg = arg(0)
    if (expr.isStringReference()) {
        val str = expr.asString()!!.value
        if (arg.isStringReference()) return str.contains(arg.asString()!!.value).toBooleanValue()
        else if (arg is RegExValue) return arg.contains(str)
    }
    else if (expr.isListReference()) return expr.asList()!!.contains(arg).toBooleanValue()
    else if (expr is RangeValue<*>) return expr.contains(arg).toBooleanValue()
    throw NoSuchMethodException(caller, "contains")
}
fun VirtualMachine.length(): NodeValue {
    val expr = caller
    return when {
        expr.isStringReference() -> expr.asString()!!.value.length.toIntegerValue()
        expr.isListReference() -> expr.asList()!!.value.size.toIntegerValue()
        expr is RangeValue<*> -> expr.size.toIntegerValue()
        else -> throw NoSuchMethodException(caller, "length")
    }
}
fun VirtualMachine.time(): NodeValue {
    return System.currentTimeMillis().toIntegerValue()
}
fun VirtualMachine.random(): NodeValue {
    val collection = caller
    return if (collection.toBoolean()) {
        when {
            collection.isStringReference() -> collection.asString()!!.value.random().toString().toStringValueReference(memory)
            collection.isListReference() -> collection.asList()!!.value.random().load(memory)
            collection is RangeValue<*> -> collection.random()
            else -> throw NoSuchMethodException(caller, "random")
        }
    } else {
        val first = arg(0).asInteger()!!
        val second = arg(1).asInteger()!!
        (first until second).random().toIntegerValue()
    }
}
fun VirtualMachine.range(): NodeValue {
    val begin = arg(0)
    val end = argOrNull(1)
    return when (begin) {
        is IntegerValue -> {
            if (end == null) IntegerRangeValue(IntegerValue(0), begin, false) else IntegerRangeValue(
                begin, end as IntegerValue, false
            )
        }
//                is StringValue -> {
//                    CharRangeValue(begin, end!! as StringValue, false)
//                }
        else -> throw TypeMismatchRuntimeException(listOf(IntegerValue::class, StringValue::class), begin)
    }
}
fun VirtualMachine.rangeInclusive(): NodeValue {
    val begin = arg(0)
    val end = argOrNull(1)
    return when (begin) {
        is IntegerValue -> {
            if (end == null) IntegerRangeValue(IntegerValue(0), begin, false) else IntegerRangeValue(
                begin, end as IntegerValue, true
            )
        }
//                is StringValue -> {
//                    CharRangeValue(begin, end!! as StringValue, true)
//                }
        else -> throw TypeMismatchRuntimeException(listOf(IntegerValue::class, StringValue::class), begin)
    }
}
//        val Integer = BuiltinProcedureValue("integer", ListNode("what"), { context ->
//            val what = context.referenceEnvironment["what"] ?: return@BuiltinProcedureValue NullValue
//            when (what) {
//                is ArithmeticValue -> what.coercedTo(IntegerValue::class)
//                is StringValue -> IntegerValue(what.value.toLong())
//                else -> NullValue
//            }
//        }, null)
//        val Float = BuiltinProcedureValue("float", ListNode("what"), { context ->
//            val what = context.referenceEnvironment["what"] ?: return@BuiltinProcedureValue NullValue
//            when (what) {
//                is ArithmeticValue -> what.coercedTo(FloatValue::class)
//                is StringValue -> FloatValue(what.value.toDouble())
//                else -> NullValue
//            }
//        }, null)
//        val Number = BuiltinProcedureValue("number", ListNode("what"), { context ->
//            val what = context.referenceEnvironment["what"] ?: return@BuiltinProcedureValue NullValue
//            when (what) {
//                is BooleanValue -> what.coercedTo(IntegerValue::class)
//                is ArithmeticValue -> what
//                is StringValue -> {
//                    if (what.value.contains('.')) {
//                        FloatValue(what.value.toDouble())
//                    } else {
//                        IntegerValue(what.value.toLong())
//                    }
//                }
//                else -> NullValue
//            }
//        }, null)
//        val String = BuiltinProcedureValue("string", ListNode("what"), { context ->
//            context.referenceEnvironment["what"]!!.printStr.toNodeValue()
//        }, null)
//        val Object = BuiltinProcedureValue("object", ListNode("fields"), { context ->
//            val fields = context.referenceEnvironment["fields"]?.asList() ?: return@BuiltinProcedureValue ObjectValue()
//            val result = mutableMapOf<String, NodeValue>()
//            for (field in fields) {
//                val key = field.asList()!![0].asString()!!
//                val value = field.asList()!![1]
//                result[key] = value
//            }
//            return@BuiltinProcedureValue ObjectValue(result)
//        }, null)
//        val Abs = BuiltinProcedureValue("abs", ListNode("num"), { context ->
//            val it = context.referenceEnvironment["num"]!!.asArithmetic()!!
//            val minusIt = it.unaryMinus()
//            return@BuiltinProcedureValue if (it > minusIt) it else minusIt
//        }, null)
//        val Enumerated = BuiltinProcedureValue("enumerated", ListNode(), { context ->
//            val list = context.referenceEnvironment["this"]!!
//            return@BuiltinProcedureValue if (list is Iterable<*>) {
//                list.mapIndexed { index, value ->
//                    ListValue(
//                        mutableListOf(
//                            index.toNodeValue(), value as NodeValue
//                        )
//                    )
//                }.toNodeValue()
//            } else {
//                throw InterpretationRuntimeException("$list has no such method as \"enumerated\"")
//            }
//        }, null)
//        val Ord = BuiltinProcedureValue("ord", ListNode("str"), { context ->
//            context.referenceEnvironment["str"]!!.asString()!!.first().code.toLong().toNodeValue()
//        }, null)
//        val Chr = BuiltinProcedureValue("chr", ListNode("num"), { context ->
//            context.referenceEnvironment["num"]!!.asInteger()!!.toInt().toChar().toString().toNodeValue()
//        }, null)
//        val Pow = BuiltinProcedureValue("pow", ListNode("num", "exp"), { context ->
//            val num = context.referenceEnvironment["num"]!!.asArithmetic()!!
//            val exp = context.referenceEnvironment["exp"]!!.asArithmetic()!!
//            num.coercedTo(FloatValue::class).value.pow(exp.coercedTo(FloatValue::class).value).toNodeValue()
//        }, null)
//        val Sum = BuiltinProcedureValue("sum", ListNode(), { context ->
//            val list = context.referenceEnvironment["this"]!!
//            return@BuiltinProcedureValue if (list is Iterable<*>) {
//                var s: NodeValue? = null
//                for (item in list) {
//                    if (s == null) s = (item as NodeValue)
//                    else s = s.plus(item as NodeValue)
//                }
//                s ?: IntegerValue(0)
//            } else {
//                throw InterpretationRuntimeException("$list has no such method as \"sum\"")
//            }
//        }, null)
//        val Boolean = BuiltinProcedureValue("boolean", ListNode("value"), { context ->
//            context.referenceEnvironment["value"]!!.toBoolean().toNodeValue()
//        }, null)
//        fun VirtualMachine.filter(): NodeValue {
//            val list = caller.asList() ?: throw BuiltinException()
//            val predicate = arg(0).asClosure() ?: throw BuiltinException()
//            // TODO: support range, string, object
////            if (list !is Iterable<*>) throw InterpretationRuntimeException("$list has no such method as \"filter\"")
//            fun predicateCall(it: NodeValue) = predicate.call(context, 0, listOf(it))
//            val copies = list.map { predicateCall(it as NodeValue) to context.memory.createReference(it as NodeValue) }
//            return@BuiltinProcedureValue (ListValue(copies
//                .filter { it.first.toBoolean() }.mapTo(mutableListOf()) { it.second }, context.memory))
//        }

//                val Reduce = BuiltinProcedureValue("reduce", ListNode("initial", "reducer"), { context ->
//            val reducer = context.referenceEnvironment["reducer"]!!.asProcedure()!!
//            fun reducerCall(acc: NodeValue, it: NodeValue) = reducer.call(context, listOf(acc, it).toNodeValue())
//            val list = context.referenceEnvironment["this"]!!
//            return@BuiltinProcedureValue if (list is Iterable<*>) {
//                var res = context.referenceEnvironment["initial"]!!
//                for (i in list) {
//                    res = reducerCall(res, i as NodeValue)
//                }
//                res
//            } else {
//                throw InterpretationRuntimeException("$list has no such method as \"reduce\"")
//            }
//        }, null)
//        val Map = BuiltinProcedureValue("map", ListNode("mapper"), { context ->
//            val mapper = context.referenceEnvironment["mapper"]!!.asProcedure()!!
//            fun mapperCall(it: NodeValue) = mapper.call(context, listOf(it).toNodeValue())
//            val collection = context.referenceEnvironment["this"]!!
//            return@BuiltinProcedureValue if (collection is Iterable<*>) {
//                (collection.map { mapperCall(it as NodeValue) }).toNodeValue()
//            } else {
//                throw InterpretationRuntimeException("$collection has no such method as \"map\"")
//            }
//        }, null)
//        val Max = BuiltinProcedureValue("max", ListNode("list"), { context ->
//            val list = (context.referenceEnvironment["this"] as? Iterable<*>) ?: (context.referenceEnvironment["list"]!! as Iterable<*>)
//            return@BuiltinProcedureValue list.maxByOrNull { it as NodeValue }!! as NodeValue
//        }, null)
//        val Min = BuiltinProcedureValue("max", ListNode("list"), { context ->
//            val list = (context.referenceEnvironment["this"] as? Iterable<*>) ?: (context.referenceEnvironment["list"]!! as Iterable<*>)
//            return@BuiltinProcedureValue list.minByOrNull { it as NodeValue }!! as NodeValue
//        }, null)
//        val Reversed = BuiltinProcedureValue("reversed", ListNode(), { context ->
//            return@BuiltinProcedureValue when (val list = context.referenceEnvironment["this"]!!) {
//                is ListValue -> list.value.reversed().toNodeValue()
//                is StringValue -> list.value.reversed().toNodeValue()
//                is Iterable<*> -> {
//                    @Suppress("UNCHECKED_CAST") (list.reversed() as List<NodeValue>).toNodeValue()
//                }
//                else -> throw InterpretationRuntimeException("$list has no such method as \"reversed\"")
//            }
//        }, null)
//        val Sorted = BuiltinProcedureValue("sorted", ListNode("cmp"), { context ->
//            @Suppress("UNCHECKED_CAST") val list = context.referenceEnvironment["this"]!! as Iterable<NodeValue>
//            val cmp = context.referenceEnvironment["cmp"]?.asProcedure()
//            return@BuiltinProcedureValue if (cmp == null) {
//                list.sorted().toNodeValue()
//            } else {
//                list.sortedWith { a, b ->
//                    val res = cmp.call(context, ListValue(mutableListOf(a, b)))
//                    if (res.toBoolean()) {
//                        1
//                    } else {
//                        -1
//                    }
//                }.toNodeValue()
//            }
//        }, null)
//        val GetNickname = BuiltinProcedureValue("getNickname", ListNode("id"), { context ->
//            val user = context.referenceEnvironment["id"]!!.asInteger()!!
//            return@BuiltinProcedureValue context.nickname(user).toNodeValue()
//        }, null)
//        val Re = BuiltinProcedureValue("re", ListNode("pattern", "flags"), { context ->
//            val pattern = context.referenceEnvironment["pattern"]!!.asString()!!
//            val flags = context.referenceEnvironment["flags"]?.asString() ?: ""
//            return@BuiltinProcedureValue RegExValue(pattern, flags)
//        }, null)
//        val Match = BuiltinProcedureValue("match", ListNode("re"), { context ->
//            val str = context.referenceEnvironment["this"]!! as StringValue
//            val re = context.referenceEnvironment["re"]!!.asRegEx()!!
//            return@BuiltinProcedureValue re.match(str)
//        }, null)
//        val MatchAll = BuiltinProcedureValue("matchAll", ListNode("re"), { context ->
//            val str = context.referenceEnvironment["this"]!! as StringValue
//            val re = context.referenceEnvironment["re"]!!.asRegEx()!!
//            return@BuiltinProcedureValue re.matchAll(str)
//        }, null)
//        val MatchEntire = BuiltinProcedureValue("matchEntire", ListNode("re"), { context ->
//            val str = context.referenceEnvironment["this"]!! as StringValue
//            val re = context.referenceEnvironment["re"]!!.asRegEx()!!
//            return@BuiltinProcedureValue re.matchEntire(str)
//        }, null)
//        val Replace = BuiltinProcedureValue("replace", ListNode("re", "replacement"), { context ->
//            val str = context.referenceEnvironment["this"]!! as StringValue
//            val replacement = context.referenceEnvironment["replacement"]!! as StringValue
//            return@BuiltinProcedureValue when (val re = context.referenceEnvironment["re"]!!) {
//                is RegExValue -> re.replace(str, replacement)
//                is StringValue -> str.value.replace(re.value, replacement.value).toNodeValue()
//                else -> throw InterpretationRuntimeException("$re has no such method as \"replace\"")
//            }
//        }, null)
//        val Sleep = BuiltinProcedureValue("sleep", ListNode("ms"), { context ->
//            val ms = context.referenceEnvironment["ms"]!!.asInteger()!!
//            context.sleep(ms)
//            return@BuiltinProcedureValue NullValue
//        }, null)