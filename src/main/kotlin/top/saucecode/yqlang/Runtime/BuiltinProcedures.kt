package top.saucecode.yqlang.Runtime

import top.saucecode.yqlang.BuiltinException
import top.saucecode.yqlang.Constants
import top.saucecode.yqlang.NoSuchMethodException
import top.saucecode.yqlang.Node.TypeMismatchRuntimeException
import top.saucecode.yqlang.NodeValue.*
import kotlin.math.pow

object BuiltinProcedures {
    private val dictionary = mapOf<String, (VirtualMachine) -> NodeValue>(
        // functions
        "time" to VirtualMachine::time,
        "range" to VirtualMachine::range,
        "rangeInclusive" to VirtualMachine::rangeInclusive,
        "number" to VirtualMachine::number,
        "num" to VirtualMachine::number,
        "integer" to VirtualMachine::integer,
        "float" to VirtualMachine::float,
        "string" to VirtualMachine::string,
        "str" to VirtualMachine::string,
        "object" to VirtualMachine::obj,
        "abs" to VirtualMachine::abs,
        "ord" to VirtualMachine::ord,
        "chr" to VirtualMachine::chr,
        "char" to VirtualMachine::chr,
        "pow" to VirtualMachine::pow,
        "sqrt" to VirtualMachine::sqrt,
        "boolean" to VirtualMachine::boolean,
        "bool" to VirtualMachine::boolean,
        "getNickname" to VirtualMachine::getNickname,
        "re" to VirtualMachine::re,
        "sleep" to VirtualMachine::sleep,
        // methods
        "split" to VirtualMachine::split,
        "join" to VirtualMachine::join,
        "find" to VirtualMachine::find,
        "findAll" to VirtualMachine::findAll,
        "contains" to VirtualMachine::contains,
        "length" to VirtualMachine::length,
        "random" to VirtualMachine::random,
        "enumerated" to VirtualMachine::enumerated,
        "sum" to VirtualMachine::sum,
        "filter" to VirtualMachine::filter,
        "reduce" to VirtualMachine::reduce,
        "map" to VirtualMachine::map,
        "max" to VirtualMachine::max,
        "min" to VirtualMachine::min,
        "reversed" to VirtualMachine::reversed,
        "sorted" to VirtualMachine::sorted,
        "match" to VirtualMachine::match,
        "matchAll" to VirtualMachine::matchAll,
        "matchEntire" to VirtualMachine::matchEntire,
        "replace" to VirtualMachine::replace,
    )
    val names = dictionary.toList().map { it.first }
    val values = dictionary.toList().map { it.second }
    fun id(func: String): Int = names.indexOf(func)
}

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
    } else if (expr.isListReference())
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
        } else if (arg is RegExValue) {
            return arg.findAll(str).toListValueReference(memory)
        }
    } else if (expr.isListReference()) {
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
    } else if (expr.isListReference()) return expr.asList()!!.contains(arg).toBooleanValue()
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
            collection.isStringReference() -> collection.asString()!!.value.random().toString()
                .toStringValueReference(memory)
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
    return when {
        begin is IntegerValue -> {
            if (end == null) IntegerRangeValue(0, begin.value, false) else IntegerRangeValue(
                begin.value, end.asInteger()!!, false
            )
        }
        begin.isStringReference() -> {
            CharRangeValue(begin.asString()!!.value[0], end!!.asString()!!.value[0], false, memory)
        }
        else -> throw TypeMismatchRuntimeException(listOf(IntegerValue::class, StringValue::class), begin)
    }
}

fun VirtualMachine.rangeInclusive(): NodeValue {
    val begin = arg(0)
    val end = argOrNull(1)
    return when {
        begin is IntegerValue -> {
            if (end == null) IntegerRangeValue(0, begin.value, true) else IntegerRangeValue(
                begin.value, end.asInteger()!!, true
            )
        }
        begin.isStringReference() -> {
            CharRangeValue(begin.asString()!!.value[0], end!!.asString()!!.value[0], true, memory)
        }
        else -> throw TypeMismatchRuntimeException(listOf(IntegerValue::class, StringValue::class), begin)
    }
}

fun VirtualMachine.integer(): NodeValue {
    val what = arg(0)
    return if (what is ArithmeticValue) {
        IntegerValue(what)
    } else if (what.isStringReference()) {
        what.asString()!!.value.toLong().toIntegerValue()
    } else {
        throw TypeMismatchRuntimeException(listOf(ArithmeticValue::class, StringValue::class), what)
    }
}

fun VirtualMachine.float(): NodeValue {
    val what = arg(0)
    return if (what is ArithmeticValue) {
        FloatValue(what)
    } else if (what.isStringReference()) {
        what.asString()!!.value.toDouble().toFloatValue()
    } else {
        throw TypeMismatchRuntimeException(listOf(ArithmeticValue::class, StringValue::class), what)
    }
}

fun VirtualMachine.number(): NodeValue {
    val what = arg(0)
    return if (what is BooleanValue) {
        IntegerValue(what)
    } else if (what is ArithmeticValue) {
        what
    } else if (what.isStringReference()) {
        val str = what.asString()!!.value
        if (str.contains('.')) str.toDouble().toFloatValue()
        else str.toLong().toIntegerValue()
    } else {
        throw TypeMismatchRuntimeException(listOf(BooleanValue::class, StringValue::class), what)
    }
}

fun VirtualMachine.string(): NodeValue {
    return arg(0).printStr(0).toStringValueReference(memory)
}

fun VirtualMachine.obj(): NodeValue {
    val fields = arg(0).asList() ?: throw BuiltinException()
    val result = mutableMapOf<String, Pointer>()
    for (field in fields.value) {
        val kv = field.load(memory).asList()!!.value
        val name = kv[0].load(memory).asString()!!.value
        val value = kv[1]
        result[name] = value
    }
    return ObjectValue(result, memory).reference
}

fun VirtualMachine.abs(): NodeValue {
    val what = arg(0)
    if (what is ArithmeticValue) {
        return what.abs()
    } else {
        throw TypeMismatchRuntimeException(listOf(ArithmeticValue::class), what)
    }
}

fun VirtualMachine.enumerated(): NodeValue {
    @Suppress("UNCHECKED_CAST")
    val list = caller as? Iterable<NodeValue> ?: throw NoSuchMethodException(caller, "enumerated")
    return ListValue(list.mapIndexedTo(mutableListOf()) { index, ptr ->
        memory.allocate(
            ListValue(
                mutableListOf(memory.allocate(index.toIntegerValue()), memory.allocate(ptr)),
                memory
            ).reference
        )
    }, memory).reference
}

fun VirtualMachine.ord(): NodeValue {
    return arg(0).asString()!!.value.first().code.toIntegerValue()
}

fun VirtualMachine.chr(): NodeValue {
    return arg(0).asInteger()!!.toInt().toChar().toString().toStringValueReference(memory)
}

fun VirtualMachine.pow(): NodeValue {
    val num = arg(0).asArithmetic()!!
    val exp = arg(1).asArithmetic()!!
    return FloatValue(num).value.pow(FloatValue(exp).value).toFloatValue()
}

fun VirtualMachine.sqrt(): NodeValue {
    val num = arg(0).asArithmetic()!!
    return kotlin.math.sqrt(FloatValue(num).value).toFloatValue()
}

fun VirtualMachine.sum(): NodeValue {
    @Suppress("UNCHECKED_CAST")
    val list = caller as? Iterable<NodeValue> ?: throw NoSuchMethodException(caller, "filter")
    var acc: NodeValue? = null
    list.forEach {
        acc = if (acc == null) {
            it
        } else {
            acc!!.addAssign(it)
        }
    }
    return acc!!
}

fun VirtualMachine.boolean(): NodeValue {
    val what = arg(0)
    return if (what is ArithmeticValue) {
        BooleanValue(what)
    } else if (what.isStringReference()) {
        what.asString()!!.value.toBooleanStrict().toBooleanValue()
    } else {
        throw TypeMismatchRuntimeException(listOf(ArithmeticValue::class, StringValue::class), what)
    }
}

fun VirtualMachine.filter(): NodeValue {
    @Suppress("UNCHECKED_CAST")
    val list = caller as? Iterable<NodeValue> ?: throw NoSuchMethodException(caller, "filter")
    val predicate = argPointer(0)
    val nonExistingCaller = memory.allocate(NullValue())
    return ListValue(list.map { memory.allocate(it) }
        .filterTo(mutableListOf()) {
            executeClosure(
                predicate, nonExistingCaller, memory.allocate(ListValue(mutableListOf(it), memory).reference)
            ).load(memory).toBoolean()
        }, memory).reference
}

fun VirtualMachine.reduce(): NodeValue {
    @Suppress("UNCHECKED_CAST")
    val list = caller as? Iterable<NodeValue> ?: throw NoSuchMethodException(caller, "reduce")
    var initial = argPointer(0)
    val accumulator = argPointer(1)
    val nonExistingCaller = memory.allocate(NullValue())
    list.forEach {
        initial = executeClosure(
            accumulator,
            nonExistingCaller,
            memory.allocate(listOf(memory[initial], it).toListValueReference(memory))
        )
    }
    return memory[initial]
}

fun VirtualMachine.map(): NodeValue {
    @Suppress("UNCHECKED_CAST")
    val list = caller as? Iterable<NodeValue> ?: throw NoSuchMethodException(caller, "map")
    val transform = argPointer(0)
    val nonExistingCaller = memory.allocate(NullValue())
    return ListValue(list.mapTo(mutableListOf()) { ptr ->
        executeClosure(
            transform,
            nonExistingCaller,
            memory.allocate(ListValue(mutableListOf(memory.allocate(ptr)), memory).reference)
        )
    }, memory).reference
}

fun VirtualMachine.max(): NodeValue {
    @Suppress("UNCHECKED_CAST")
    val list = caller as? Iterable<NodeValue> ?: throw NoSuchMethodException(caller, "max")
    return list.maxOf { it }
}

fun VirtualMachine.min(): NodeValue {
    @Suppress("UNCHECKED_CAST")
    val list = caller as? Iterable<NodeValue> ?: throw NoSuchMethodException(caller, "min")
    return list.minOf { it }
}

fun VirtualMachine.reversed(): NodeValue {
    @Suppress("UNCHECKED_CAST")
    val list = caller as? Iterable<NodeValue> ?: throw NoSuchMethodException(caller, "reversed")
    return list.reversed().toListValueReference(memory)
}

fun VirtualMachine.sorted(): NodeValue {
    @Suppress("UNCHECKED_CAST")
    val list = caller as? Iterable<NodeValue> ?: throw NoSuchMethodException(caller, "sorted")
    return list.sorted().toListValueReference(memory)
}

fun VirtualMachine.getNickname(): NodeValue {
    return executionContext.nickname(arg(0).asInteger()!!).toStringValueReference(memory)
}

fun VirtualMachine.re(): NodeValue {
    return RegExValue(arg(0).asString()!!.value, argOrNull(1)?.asString()?.value ?: "")
}

fun VirtualMachine.match(): NodeValue {
    return arg(0).asRegEx()!!.match(caller.asString()!!.value).toStringListReference(memory)
}

fun VirtualMachine.matchAll(): NodeValue {
    return arg(0).asRegEx()!!.matchAll(caller.asString()!!.value)
        .mapTo(mutableListOf()) { it.toStringListReference(memory) }
        .toListValueReference(memory)
}

fun VirtualMachine.matchEntire(): NodeValue {
    return arg(0).asRegEx()!!.matchEntire(caller.asString()!!.value)
}

fun VirtualMachine.replace(): NodeValue {
    val str = caller.asString()!!.value
    val replacement = arg(1).asString()!!.value
    val pattern = arg(0)
    return when {
        pattern is RegExValue -> pattern.replace(str, replacement).toStringValueReference(memory)
        pattern.isStringReference() -> str.replace(pattern.asString()!!.value, replacement)
            .toStringValueReference(memory)
        else -> throw TypeMismatchRuntimeException(listOf(RegExValue::class, StringValue::class), pattern)
    }
}

fun VirtualMachine.sleep(): NodeValue {
    val ms = arg(0).asInteger()!!
    executionContext.sleep(ms)
    return NullValue()
}