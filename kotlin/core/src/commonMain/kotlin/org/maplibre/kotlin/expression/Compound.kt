package org.maplibre.kotlin.expression

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Compound expressions: `["operator", arg1, arg2, ...]` implemented as plain
 * functions. Mirrors the CompoundExpression mechanism in mbgl.
 */
class CompoundExpression(
    val op: String,
    val args: List<Expression>,
    val fn: (List<Value>, EvaluationContext) -> EvaluationResult,
    type: Type,
) : Expression(Kind.CompoundExpression, type) {

    override fun evaluate(ctx: EvaluationContext): EvaluationResult {
        val values = ArrayList<Value>(args.size)
        for (arg in args) {
            val r = arg.evaluate(ctx)
            if (r.isError) return r
            values.add(r.resultValue ?: Value.Null)
        }
        return fn(values, ctx)
    }

    override fun eachChild(visit: (Expression) -> Unit) = args.forEach(visit)
    override fun getOperator(): String = op
    override fun possibleOutputs(): List<Value?> = args.flatMap { it.possibleOutputs() }

    override fun equals(other: Any?): Boolean =
        other is CompoundExpression && other.op == op && other.args == args

    override fun hashCode(): Int = op.hashCode() * 31 + args.hashCode()
}

/** Builds the registry of compound expressions (operators and functions). */
object CompoundRegistry {

    fun build(
        op: String,
        args: List<Expression>,
        type: Type,
    ): Expression? {
        // Each handler takes already-evaluated argument values and returns a Value.
        // Number/boolean/string extraction helpers throw EvaluationException which
        // the wrapper converts into an EvaluationResult.Error.
        val handler: (List<Value>, EvaluationContext) -> Value = when (op) {
            // --- arithmetic ---
            "+" -> { v, _ -> Value.Number(asNumber(v[0]) + asNumber(v[1])) }
            "-" -> { v, _ -> Value.Number(asNumber(v[0]) - asNumber(v[1])) }
            "*" -> { v, _ -> Value.Number(asNumber(v[0]) * asNumber(v[1])) }
            "/" -> { v, _ ->
                val d = asNumber(v[1])
                if (d == 0.0) throw EvalException("Cannot divide by zero")
                Value.Number(asNumber(v[0]) / d)
            }
            "%" -> { v, _ -> Value.Number(asNumber(v[0]) % asNumber(v[1])) }
            "^" -> { v, _ -> Value.Number(asNumber(v[0]).pow(asNumber(v[1]))) }

            // --- comparison ---
            "==" -> { v, _ -> Value.Boolean(v[0] == v[1]) }
            "!=" -> { v, _ -> Value.Boolean(v[0] != v[1]) }
            "<" -> { v, _ -> Value.Boolean(compareValues(v[0], v[1]) < 0) }
            ">" -> { v, _ -> Value.Boolean(compareValues(v[0], v[1]) > 0) }
            "<=" -> { v, _ -> Value.Boolean(compareValues(v[0], v[1]) <= 0) }
            ">=" -> { v, _ -> Value.Boolean(compareValues(v[0], v[1]) >= 0) }

            // --- boolean ---
            "!" -> { v, _ -> Value.Boolean(!asBoolean(v[0])) }
            "all" -> { v, _ -> Value.Boolean(v.all { asBoolean(it) }) }
            "any" -> { v, _ -> Value.Boolean(v.any { asBoolean(it) }) }

            // --- feature access ---
            "zoom" -> { _, ctx ->
                val z = ctx.zoom ?: throw EvalException("No zoom provided")
                Value.Number(z.toDouble())
            }
            "get" -> { v, ctx ->
                val key = asString(v[0])
                val props = if (v.size > 1) {
                    (v[1] as? Value.Object)?.properties ?: emptyMap()
                } else {
                    ctx.feature?.properties ?: emptyMap()
                }
                props[key] ?: Value.Null
            }
            "has" -> { v, ctx ->
                val key = asString(v[0])
                val props = if (v.size > 1) {
                    (v[1] as? Value.Object)?.properties ?: emptyMap()
                } else {
                    ctx.feature?.properties ?: emptyMap()
                }
                Value.Boolean(props.containsKey(key))
            }
            "id" -> { v, ctx ->
                ctx.feature?.id ?: Value.Null
            }
            "length" -> { v, _ ->
                when (val x = v[0]) {
                    is Value.String -> Value.Number(x.value.length.toDouble())
                    is Value.Array -> Value.Number(x.values.size.toDouble())
                    else -> throw EvalException("Expected value to be of type string or array")
                }
            }

            // --- math functions ---
            "abs" -> { v, _ -> Value.Number(abs(asNumber(v[0]))) }
            "floor" -> { v, _ -> Value.Number(floor(asNumber(v[0]))) }
            "ceil" -> { v, _ -> Value.Number(ceil(asNumber(v[0]))) }
            "round" -> { v, _ -> Value.Number(round(asNumber(v[0]))) }
            "ln" -> { v, _ -> Value.Number(ln(asNumber(v[0]))) }
            "log2" -> { v, _ -> Value.Number(log2(asNumber(v[0]))) }
            "log10" -> { v, _ -> Value.Number(ln(asNumber(v[0])) / ln(10.0)) }
            "sqrt" -> { v, _ -> Value.Number(sqrt(asNumber(v[0]))) }
            "cos" -> { v, _ -> Value.Number(cos(asNumber(v[0]))) }
            "sin" -> { v, _ -> Value.Number(sin(asNumber(v[0]))) }
            "tan" -> { v, _ -> Value.Number(tan(asNumber(v[0]))) }
            "e" -> { _, _ -> Value.Number(kotlin.math.E) }
            "pi" -> { _, _ -> Value.Number(kotlin.math.PI) }

            // --- type coercion ---
            "to-number" -> { v, _ ->
                when (val x = v[0]) {
                    is Value.Number -> x
                    is Value.String -> x.value.toDoubleOrNull()
                        ?.let { Value.Number(it) }
                        ?: throw EvalException("Could not convert string to number")
                    is Value.Boolean -> Value.Number(if (x.value) 1.0 else 0.0)
                    else -> throw EvalException("Could not convert to number")
                }
            }
            "to-string" -> { v, _ -> Value.String(toString(v[0])) }
            "to-boolean" -> { v, _ ->
                Value.Boolean(
                    when (val x = v[0]) {
                        is Value.Boolean -> x.value
                        is Value.Number -> x.value != 0.0
                        is Value.String -> x.value.isNotEmpty()
                        is Value.Null -> false
                        else -> true
                    },
                )
            }
            "to-color" -> { v, _ ->
                when (val x = v[0]) {
                    is Value.Color -> x
                    is Value.String -> parseColor(x.value)
                        ?: throw EvalException("Could not convert string to color")
                    is Value.Array -> parseColorArray(x.values)
                        ?: throw EvalException("Could not convert array to color")
                    else -> throw EvalException("Could not convert to color")
                }
            }
            "array" -> { v, _ -> Value.Array(v.toList()) }
            "object" -> { v, _ ->
                if (v.size == 1 && v[0] is Value.Object) v[0]
                else throw EvalException("object expression requires a single object argument")
            }

            // --- string ---
            "downcase" -> { v, _ -> Value.String(asString(v[0]).lowercase()) }
            "upcase" -> { v, _ -> Value.String(asString(v[0]).uppercase()) }
            "concat" -> { v, _ ->
                Value.String(v.joinToString("") { asString(it) })
            }

            else -> return null
        }
        return CompoundExpression(op, args, { values, ctx ->
            try {
                EvaluationResult.Ok(handler(values, ctx))
            } catch (e: EvalException) {
                EvaluationResult.Error(e.message ?: "Evaluation error")
            }
        }, type)
    }

    // --- helpers ---

    private class EvalException(message: String) : Exception(message)

    private fun asNumber(v: Value): Double =
        (v as? Value.Number)?.value ?: throw EvalException("Expected a number, but found ${toString(v)}")

    private fun asBoolean(v: Value): Boolean =
        (v as? Value.Boolean)?.value ?: throw EvalException("Expected a boolean, but found ${toString(v)}")

    private fun asString(v: Value): String =
        (v as? Value.String)?.value ?: throw EvalException("Expected a string, but found ${toString(v)}")

    private fun compareValues(a: Value, b: Value): Int {
        val an = (a as? Value.Number)?.value
        val bn = (b as? Value.Number)?.value
        if (an != null && bn != null) return an.compareTo(bn)
        // fall back to string comparison
        val as_ = (a as? Value.String)?.value ?: toString(a)
        val bs = (b as? Value.String)?.value ?: toString(b)
        return as_.compareTo(bs)
    }

    private fun parseColor(s: String): Value.Color? {
        val hex = s.removePrefix("#")
        if (hex.length == 6 || hex.length == 8) {
            val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
            val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
            val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
            val a = if (hex.length == 8) (hex.substring(6, 8).toIntOrNull(16) ?: 255) else 255
            return Value.Color(r / 255.0, g / 255.0, b / 255.0, a / 255.0)
        }
        if (hex.length == 3) {
            val r = hex[0].digitToIntOrNull(16) ?: return null
            val g = hex[1].digitToIntOrNull(16) ?: return null
            val b = hex[2].digitToIntOrNull(16) ?: return null
            return Value.Color(r / 15.0, g / 15.0, b / 15.0, 1.0)
        }
        // named colors (common subset)
        return when (s.lowercase()) {
            "red" -> Value.Color(1.0, 0.0, 0.0, 1.0)
            "green" -> Value.Color(0.0, 0.5, 0.0, 1.0)
            "blue" -> Value.Color(0.0, 0.0, 1.0, 1.0)
            "black" -> Value.Color(0.0, 0.0, 0.0, 1.0)
            "white" -> Value.Color(1.0, 1.0, 1.0, 1.0)
            "gray" -> Value.Color(0.5, 0.5, 0.5, 1.0)
            "transparent" -> Value.Color(0.0, 0.0, 0.0, 0.0)
            else -> null
        }
    }

    private fun parseColorArray(values: List<Value>): Value.Color? {
        if (values.size < 3) return null
        val nums = values.mapNotNull { (it as? Value.Number)?.value }
        if (nums.size < 3) return null
        val a = if (nums.size >= 4) nums[3] else 1.0
        return Value.Color(nums[0] / 255.0, nums[1] / 255.0, nums[2] / 255.0, a)
    }
}
