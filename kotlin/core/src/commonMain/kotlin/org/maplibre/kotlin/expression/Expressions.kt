package org.maplibre.kotlin.expression

/**
 * Literal expression: `["literal", value]` or a bare JSON value.
 */
class LiteralExpression(value: Value) : Expression(Kind.Literal, typeOf(value)) {
    val value: Value = value

    override fun evaluate(ctx: EvaluationContext): EvaluationResult = EvaluationResult.Ok(value)
    override fun eachChild(visit: (Expression) -> Unit) {}
    override fun getOperator(): String = "literal"
    override fun serialize(): List<Any?> = listOf("literal", value)
    override fun possibleOutputs(): List<Value?> = listOf(value)

    override fun equals(other: Any?): Boolean = other is LiteralExpression && other.value == value
    override fun hashCode(): Int = value.hashCode()
}

/**
 * Step expression: `["step", input, baseOutput, stop1, out1, ...]`.
 * Ported from mbgl::style::expression::Step.
 */
class StepExpression(
    type: Type,
    val input: Expression,
    val stops: Map<Double, Expression>,
) : Expression(Kind.Step, type) {
    init {
        require(input.type == Type.Number) { "step input must be a number" }
    }

    override fun evaluate(ctx: EvaluationContext): EvaluationResult {
        val evaluatedInput = input.evaluate(ctx)
        if (evaluatedInput.isError) return evaluatedInput
        val v = evaluatedInput.resultValue
        val x = (v as? Value.Number)?.value ?: return EvaluationResult.Error("Input is not a number.")
        if (x.isNaN()) return EvaluationResult.Error("Input is not a number.")
        if (stops.isEmpty()) return EvaluationResult.Error("No stops in step curve.")

        // find the last stop with key <= x
        var chosen: Expression? = null
        for ((label, output) in stops) {
            if (label <= x) chosen = output else break
        }
        chosen ?: return EvaluationResult.Error("No stops in step curve.")
        return chosen.evaluate(ctx)
    }

    override fun eachChild(visit: (Expression) -> Unit) {
        visit(input)
        stops.values.forEach(visit)
    }

    override fun getOperator(): String = "step"

    override fun possibleOutputs(): List<Value?> =
        stops.values.flatMap { it.possibleOutputs() }

    override fun serialize(): List<Any?> = buildList {
        add("step")
        add(input.serialize())
        for ((label, output) in stops) {
            if (label > Double.NEGATIVE_INFINITY) add(label)
            add(output.serialize())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is StepExpression) return false
        if (input != other.input || stops.size != other.stops.size) return false
        return stops.all { (k, v) -> other.stops[k] == v }
    }

    override fun hashCode(): Int = input.hashCode() * 31 + stops.size
}

/**
 * Interpolate expression: `["interpolate", interpolation, input, stop1, out1, ...]`.
 * Ported from mbgl::style::expression::InterpolateImpl.
 */
class InterpolateExpression(
    type: Type,
    val interpolator: Interpolator,
    val input: Expression,
    val stops: Map<Double, Expression>,
) : Expression(Kind.Interpolate, type) {
    init {
        require(input.type == Type.Number) { "interpolate input must be a number" }
    }

    override fun evaluate(ctx: EvaluationContext): EvaluationResult {
        val evaluatedInput = input.evaluate(ctx)
        if (evaluatedInput.isError) return evaluatedInput
        val x = (evaluatedInput.resultValue as? Value.Number)?.value
            ?: return EvaluationResult.Error("Input is not a number.")
        if (x.isNaN()) return EvaluationResult.Error("Input is not a number.")
        if (stops.isEmpty()) return EvaluationResult.Error("No stops in exponential curve.")

        val sortedKeys = stops.keys.sorted()
        val it = sortedKeys.firstOrNull { it > x }
        if (it == null) {
            // x beyond last stop
            return stops[sortedKeys.last()]!!.evaluate(ctx)
        }
        val idx = sortedKeys.indexOf(it)
        if (idx == 0) {
            return stops[it]!!.evaluate(ctx)
        }
        val lowerKey = sortedKeys[idx - 1]
        val t = interpolator.interpolationFactor(FloatRange(lowerKey.toFloat(), it.toFloat()), x.toFloat())

        if (t == 0.0) return stops[lowerKey]!!.evaluate(ctx)
        if (t == 1.0) return stops[it]!!.evaluate(ctx)

        val lower = stops[lowerKey]!!.evaluate(ctx)
        if (lower.isError) return lower
        val upper = stops[it]!!.evaluate(ctx)
        if (upper.isError) return upper

        return interpolateValues(type, lower.resultValue ?: Value.Null, upper.resultValue ?: Value.Null, t)
    }

    private fun interpolateValues(type: Type, lower: Value, upper: Value, t: Double): EvaluationResult {
        return when (type) {
            Type.NumberType -> {
                val l = (lower as? Value.Number)?.value
                    ?: return EvaluationResult.Error("Expected value to be of type number")
                val u = (upper as? Value.Number)?.value
                    ?: return EvaluationResult.Error("Expected value to be of type number")
                EvaluationResult.Ok(Value.Number(interpolate(l, u, t)))
            }
            Type.ColorType -> {
                val l = lower.toColor()
                    ?: return EvaluationResult.Error("Expected value to be of type color")
                val u = upper.toColor()
                    ?: return EvaluationResult.Error("Expected value to be of type color")
                EvaluationResult.Ok(interpolate(l, u, t))
            }
            is Type.ArrayType -> {
                if (type.itemType != Type.Number || type.n == null) {
                    return EvaluationResult.Error("Type $type is not interpolatable.")
                }
                val l = lower as? Value.Array
                    ?: return EvaluationResult.Error("Expected value to be of type array")
                val u = upper as? Value.Array
                    ?: return EvaluationResult.Error("Expected value to be of type array")
                val ld = l.values.map { (it as? Value.Number)?.value ?: 0.0 }
                val ud = u.values.map { (it as? Value.Number)?.value ?: 0.0 }
                EvaluationResult.Ok(Value.Array(interpolate(ld, ud, t).map { Value.Number(it) }))
            }
            else -> EvaluationResult.Error("Type $type is not interpolatable.")
        }
    }

    override fun eachChild(visit: (Expression) -> Unit) {
        visit(input)
        stops.values.forEach(visit)
    }

    override fun getOperator(): String = "interpolate"

    override fun possibleOutputs(): List<Value?> =
        stops.values.flatMap { it.possibleOutputs() }

    override fun serialize(): List<Any?> = buildList {
        add("interpolate")
        add(
            when (interpolator) {
                is Interpolator.Exponential ->
                    if (interpolator.base == 1.0) listOf("linear") else listOf("exponential", interpolator.base)
                is Interpolator.CubicBezier ->
                    listOf("cubic-bezier", interpolator.ub.p1.first, interpolator.ub.p1.second, interpolator.ub.p2.first, interpolator.ub.p2.second)
            },
        )
        add(input.serialize())
        for ((label, output) in stops) {
            add(label)
            add(output.serialize())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is InterpolateExpression) return false
        if (interpolator != other.interpolator || input != other.input || stops.size != other.stops.size) return false
        return stops.all { (k, v) -> other.stops[k] == v }
    }

    override fun hashCode(): Int = interpolator.hashCode() * 31 + input.hashCode()
}

/**
 * Match expression: `["match", input, label1, out1, ..., otherwise]`.
 * Ported from mbgl::style::expression::Match (string/number labels).
 */
class MatchExpression(
    type: Type,
    val input: Expression,
    val branches: Map<Value, Expression>,
    val otherwise: Expression,
) : Expression(Kind.Match, type) {

    override fun evaluate(ctx: EvaluationContext): EvaluationResult {
        val inputValue = input.evaluate(ctx)
        if (inputValue.isError) return inputValue
        val v = inputValue.resultValue ?: return otherwise.evaluate(ctx)
        branches[v]?.let { return it.evaluate(ctx) }
        return otherwise.evaluate(ctx)
    }

    override fun eachChild(visit: (Expression) -> Unit) {
        visit(input)
        branches.values.forEach(visit)
        visit(otherwise)
    }

    override fun getOperator(): String = "match"

    override fun possibleOutputs(): List<Value?> =
        branches.values.flatMap { it.possibleOutputs() } + otherwise.possibleOutputs()

    override fun serialize(): List<Any?> = buildList {
        add("match")
        add(input.serialize())
        // group labels by output
        val grouped = LinkedHashMap<Expression, MutableList<Value>>()
        for ((label, output) in branches) {
            grouped.getOrPut(output) { mutableListOf() }.add(label)
        }
        for ((output, labels) in grouped) {
            if (labels.size == 1) add(labels[0]) else add(labels)
            add(output.serialize())
        }
        add(otherwise.serialize())
    }

    override fun equals(other: Any?): Boolean {
        if (other !is MatchExpression) return false
        if (input != other.input || otherwise != other.otherwise || branches.size != other.branches.size) return false
        return branches.all { (k, v) -> other.branches[k] == v }
    }

    override fun hashCode(): Int = input.hashCode() * 31 + otherwise.hashCode()
}

/**
 * Coalesce expression: `["coalesce", expr1, expr2, ...]` — first non-null result.
 */
class CoalesceExpression(val args: List<Expression>) : Expression(Kind.Coalesce, Type.Value) {
    override fun evaluate(ctx: EvaluationContext): EvaluationResult {
        for (arg in args) {
            val result = arg.evaluate(ctx)
            if (result.isError) return result
            val v = result.resultValue
            if (v != null && v != Value.Null) return result
        }
        return EvaluationResult.Error("No matching values.")
    }

    override fun eachChild(visit: (Expression) -> Unit) = args.forEach(visit)
    override fun getOperator(): String = "coalesce"
    override fun possibleOutputs(): List<Value?> = args.flatMap { it.possibleOutputs() }

    override fun equals(other: Any?): Boolean = other is CoalesceExpression && other.args == args
    override fun hashCode(): Int = args.hashCode()
}

/**
 * Case expression: `["case", cond1, out1, cond2, out2, ..., fallback]`.
 */
class CaseExpression(
    val branches: List<Pair<Expression, Expression>>,
    val fallback: Expression,
) : Expression(Kind.Case, Type.Value) {
    override fun evaluate(ctx: EvaluationContext): EvaluationResult {
        for ((cond, output) in branches) {
            val condResult = cond.evaluate(ctx)
            if (condResult.isError) return condResult
            if (condResult.resultValue == Value.Boolean(true)) {
                return output.evaluate(ctx)
            }
        }
        return fallback.evaluate(ctx)
    }

    override fun eachChild(visit: (Expression) -> Unit) {
        branches.forEach { (c, o) -> visit(c); visit(o) }
        visit(fallback)
    }

    override fun getOperator(): String = "case"
    override fun possibleOutputs(): List<Value?> =
        branches.flatMap { it.second.possibleOutputs() } + fallback.possibleOutputs()

    override fun equals(other: Any?): Boolean =
        other is CaseExpression && other.branches == branches && other.fallback == fallback

    override fun hashCode(): Int = branches.hashCode() * 31 + fallback.hashCode()
}

/** Let expression: `["let", name1, expr1, ..., body]`. */
class LetExpression(
    val bindings: Map<String, Expression>,
    val body: Expression,
) : Expression(Kind.Let, body.type) {
    override fun evaluate(ctx: EvaluationContext): EvaluationResult {
        val env = HashMap<String, Value>()
        for ((name, expr) in bindings) {
            val result = expr.evaluate(ctx)
            if (result.isError) return result
            env[name] = result.resultValue ?: Value.Null
        }
        // evaluate body with variables bound by this let
        return body.evaluate(ctx.withVars(env))
    }

    override fun eachChild(visit: (Expression) -> Unit) {
        bindings.values.forEach(visit)
        visit(body)
    }

    override fun getOperator(): String = "let"

    override fun equals(other: Any?): Boolean =
        other is LetExpression && other.bindings == bindings && other.body == body

    override fun hashCode(): Int = bindings.hashCode() * 31 + body.hashCode()
}

/** Var expression: `["var", name]` — reads a variable bound by Let. */
class VarExpression(val name: String) : Expression(Kind.Var, Type.Value) {
    override fun evaluate(ctx: EvaluationContext): EvaluationResult {
        val v = ctx.lookupVar(name)
            ?: return EvaluationResult.Error("Unknown variable \"$name\".")
        return EvaluationResult.Ok(v)
    }

    override fun eachChild(visit: (Expression) -> Unit) {}
    override fun getOperator(): String = "var"
    override fun equals(other: Any?): Boolean = other is VarExpression && other.name == name
    override fun hashCode(): Int = name.hashCode()
}
