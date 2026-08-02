package org.maplibre.kotlin.expression

import org.maplibre.kotlin.style.StyleJsonParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses style-spec expression JSON into an [Expression] tree.
 * Mirrors mbgl's ParsingContext + per-expression parse() methods.
 */
class ExpressionParser {

    fun parse(element: JsonElement): Expression? {
        return when (element) {
            is JsonPrimitive -> parsePrimitive(element)
            is JsonArray -> parseArray(element)
            is JsonObject -> parseObject(element)
        }
    }

    private fun parsePrimitive(el: JsonPrimitive): Expression {
        return LiteralExpression(primitiveToValue(el))
    }

    private fun primitiveToValue(el: JsonPrimitive): Value {
        el.booleanOrNull?.let { return Value.Boolean(it) }
        el.doubleOrNull?.let { return Value.Number(it) }
        // int vs double: keep as number
        val content = el.content
        content.toDoubleOrNull()?.let { return Value.Number(it) }
        return Value.String(content)
    }

    private fun parseObject(el: JsonObject): Expression {
        // bare object => literal object value (for "get" on object literals, etc.)
        val map: Map<kotlin.String, Value> = el.mapValues { (_, v) -> valueFromElement(v) }
        return LiteralExpression(Value.Object(map))
    }

    private fun valueFromElement(el: JsonElement): Value = when (el) {
        is JsonPrimitive -> primitiveToValue(el)
        is JsonArray -> Value.Array(el.map { valueFromElement(it) })
        is JsonObject -> Value.Object(el.mapValues { (_, v) -> valueFromElement(v) })
    }
    private fun parseArray(el: JsonArray): Expression? {
        if (el.isEmpty()) return LiteralExpression(Value.Array(emptyList()))

        val head = el[0]
        // bare arrays (not expressions) — literal
        if (head !is JsonPrimitive) {
            return LiteralExpression(Value.Array(el.map { valueFromElement(it) }))
        }
        val op = head.content

        // number/string/boolean literal wrapped in array? treat first element as op name;
        // if it's not a known expression op, fall back to literal array
        if (op.toDoubleOrNull() != null || op == "true" || op == "false") {
            return LiteralExpression(Value.Array(el.map { valueFromElement(it) }))
        }

        return parseExpressionOp(op, el)
    }

    private fun parseExpressionOp(op: String, el: JsonArray): Expression? {
        val args = el.drop(1)

        when (op) {
            "literal" -> {
                val v = args.firstOrNull()?.let { valueFromElement(it) } ?: Value.Null
                return LiteralExpression(v)
            }
            "step" -> return parseStep(el)
            "interpolate" -> return parseInterpolate(el)
            "match" -> return parseMatch(el)
            "coalesce" -> {
                val parsed = args.mapNotNull { parse(it) }
                if (parsed.size != args.size) return null
                return CoalesceExpression(parsed)
            }
            "case" -> return parseCase(el)
            "let" -> return parseLet(el)
            "var" -> {
                val name = (args[0] as? JsonPrimitive)?.content ?: return null
                return VarExpression(name)
            }
            "get", "has", "id", "length", "zoom", "abs", "floor", "ceil", "round", "ln",
            "log2", "log10", "sqrt", "cos", "sin", "tan", "e", "pi",
            "+", "-", "*", "/", "%", "^",
            "==", "!=", "<", ">", "<=", ">=",
            "!", "all", "any",
            "to-number", "to-string", "to-boolean", "to-color",
            "array", "object", "downcase", "upcase", "concat",
            -> {
                val parsed = args.mapNotNull { parse(it) }
                if (parsed.size != args.size) return null
                val compoundType = if (op == "zoom") Type.Number else Type.Value
                return CompoundRegistry.build(op, parsed, compoundType)
                    ?: LiteralExpression(Value.Null)
            }
            else -> return null // unknown expression
        }
    }

    private fun parseStep(el: JsonArray): StepExpression? {
        // ["step", input, base, stop1, out1, ...]
        val input = parse(el[1]) ?: return null
        val stops = LinkedHashMap<Double, Expression>()
        var i = 2
        if (i >= el.size) return null
        // first output has implicit -infinity label
        val baseOutput = parse(el[i]) ?: return null
        stops[Double.NEGATIVE_INFINITY] = baseOutput
        i++
        while (i + 1 < el.size) {
            val label = (el[i] as? JsonPrimitive)?.doubleOrNull ?: return null
            val output = parse(el[i + 1]) ?: return null
            stops[label] = output
            i += 2
        }
        return StepExpression(Type.Value, input, stops)
    }

    private fun parseInterpolate(el: JsonArray): InterpolateExpression? {
        // ["interpolate", ["linear"|"exponential"|"cubic-bezier", ...], input, stop1, out1, ...]
        val interpEl = el[1] as? JsonArray ?: return null
        val interpName = (interpEl[0] as? JsonPrimitive)?.content ?: return null
        val interpolator: Interpolator = when (interpName) {
            "linear" -> Interpolator.Exponential(1.0)
            "exponential" -> {
                val base = (interpEl[1] as? JsonPrimitive)?.doubleOrNull ?: 1.0
                Interpolator.Exponential(base)
            }
            "cubic-bezier" -> {
                if (interpEl.size != 5) return null
                val x1 = (interpEl[1] as? JsonPrimitive)?.doubleOrNull ?: return null
                val y1 = (interpEl[2] as? JsonPrimitive)?.doubleOrNull ?: return null
                val x2 = (interpEl[3] as? JsonPrimitive)?.doubleOrNull ?: return null
                val y2 = (interpEl[4] as? JsonPrimitive)?.doubleOrNull ?: return null
                Interpolator.CubicBezier(UnitBezier(x1, y1, x2, y2))
            }
            else -> return null
        }

        val input = parse(el[2]) ?: return null
        val stops = LinkedHashMap<Double, Expression>()
        var i = 3
        while (i + 1 < el.size) {
            val label = (el[i] as? JsonPrimitive)?.doubleOrNull ?: return null
            val output = parse(el[i + 1]) ?: return null
            stops[label] = output
            i += 2
        }
        if (stops.isEmpty()) return null
        // infer the output type from the first stop's expression
        val outputType = inferOutputType(stops.values.first())
        return InterpolateExpression(outputType, interpolator, input, stops)
    }

    /** Determines the interpolate output type from a sample output expression. */
    private fun inferOutputType(sample: Expression): Type = when (sample) {
        is LiteralExpression -> when (val v = sample.value) {
            is Value.Number -> Type.Number
            is Value.Color -> Type.Color
            is Value.String -> if (v.toColor() != null) Type.Color else Type.Value
            is Value.Array -> Type.ArrayType(Type.Number, sample.value.values.size)
            else -> Type.Value
        }
        else -> Type.Value
    }

    private fun parseMatch(el: JsonArray): MatchExpression? {
        // ["match", input, label(s), output, ..., otherwise]
        val input = parse(el[1]) ?: return null
        val branches = LinkedHashMap<Value, Expression>()
        var i = 2
        while (i + 1 < el.size) {
            val labelEl = el[i]
            val output = parse(el[i + 1]) ?: return null
            if (labelEl is JsonArray) {
                for (l in labelEl) {
                    branches[valueFromElement(l)] = output
                }
            } else {
                branches[valueFromElement(labelEl)] = output
            }
            i += 2
        }
        val otherwise = parse(el.last()) ?: return null
        return MatchExpression(Type.Value, input, branches, otherwise)
    }

    private fun parseCase(el: JsonArray): CaseExpression? {
        // ["case", cond1, out1, cond2, out2, ..., fallback]
        val branches = mutableListOf<Pair<Expression, Expression>>()
        var i = 1
        while (i + 1 < el.size) {
            val cond = parse(el[i]) ?: return null
            val output = parse(el[i + 1]) ?: return null
            branches.add(cond to output)
            i += 2
        }
        val fallback = parse(el.last()) ?: return null
        return CaseExpression(branches, fallback)
    }

    private fun parseLet(el: JsonArray): LetExpression? {
        // ["let", name1, expr1, name2, expr2, ..., body]
        val bindings = LinkedHashMap<String, Expression>()
        var i = 1
        while (i + 1 < el.size) {
            val name = (el[i] as? JsonPrimitive)?.content ?: return null
            val expr = parse(el[i + 1]) ?: return null
            bindings[name] = expr
            i += 2
        }
        val body = parse(el.last()) ?: return null
        return LetExpression(bindings, body)
    }
}
