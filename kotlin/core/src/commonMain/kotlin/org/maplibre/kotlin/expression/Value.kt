package org.maplibre.kotlin.expression

import org.maplibre.kotlin.tile.CanonicalTileID

/**
 * Expression value — the runtime value type used by the expression engine.
 * Mirrors mbgl::style::expression::Value.
 */
sealed class Value {
    object Null : Value()
    data class Boolean(val value: kotlin.Boolean) : Value()
    data class Number(val value: Double) : Value()
    data class String(val value: kotlin.String) : Value()
    data class Color(val r: Double, val g: Double, val b: Double, val a: Double) : Value() {
        override fun toString(): kotlin.String {
            val hex = { v: Double ->
                ((v * 255.0).toInt().coerceIn(0, 255)).toString(16).padStart(2, '0')
            }
            return "#${hex(r)}${hex(g)}${hex(b)}"
        }
    }
    data class Array(val values: List<Value>) : Value()
    data class Object(val properties: Map<kotlin.String, Value>) : Value()

    /** Converts this value to a Color when it is a color or a color string. */
    fun toColor(): Value.Color? = when (this) {
        is Value.Color -> this
        is Value.String -> parseColorString(value)
        else -> null
    }

    companion object {
        val NullValue = Null

        /** Javascript's Number.MAX_SAFE_INTEGER */
        const val MAX_SAFE_INTEGER: Double = 9007199254740991.0

        fun isSafeInteger(x: Long): kotlin.Boolean =
            kotlin.math.abs(x.toDouble()) <= MAX_SAFE_INTEGER

        private fun parseColorString(s: kotlin.String): Value.Color? {
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
    }
}

/** Result of expression evaluation: either an error message or a value. */
sealed class EvaluationResult {
    data class Error(val message: kotlin.String) : EvaluationResult()
    data class Ok(val value: Value) : EvaluationResult()

    val isError: kotlin.Boolean get() = this is Error
    val error: Error? get() = this as? Error
    val resultValue: Value? get() = (this as? Ok)?.value
}

/**
 * Context passed to expression evaluation. Mirrors
 * mbgl::style::expression::EvaluationContext (feature subset).
 */
class EvaluationContext(
    val zoom: Float? = null,
    val feature: Feature? = null,
    val colorRampParameter: Double? = null,
    val canonical: CanonicalTileID? = null,
) {
    /** Variables bound by enclosing ["let", ...] expressions. */
    internal var vars: Map<kotlin.String, Value> = emptyMap()

    fun withVars(env: Map<kotlin.String, Value>): EvaluationContext {
        val merged = vars + env
        return EvaluationContext(zoom, feature, colorRampParameter, canonical).also { it.vars = merged }
    }

    fun lookupVar(name: kotlin.String): Value? = vars[name]

    /** Immutable feature interface consumed by expressions (get/has etc.). */
    interface Feature {
        val id: Value?
        val properties: Map<kotlin.String, Value>
        fun geometry(): List<List<Pair<Double, Double>>> = emptyList()
    }
}

/** Expression dependency flags (bitmask). */
object Dependency {
    const val NONE = 0
    const val FEATURE = 1 shl 0
    const val IMAGE = 1 shl 1
    const val ZOOM = 1 shl 2
    const val LOCATION = 1 shl 3
    const val BIND = 1 shl 4
    const val VAR = 1 shl 5
    const val OVERRIDE = 1 shl 6
    const val ELEVATION = 1 shl 7
    const val ALL = (1 shl 8) - 1
}

/**
 * Base class of all expressions. Mirrors mbgl::style::expression::Expression.
 */
abstract class Expression(
    val kind: Kind,
    val type: Type,
    val dependencies: Int = Dependency.NONE,
) {
    abstract fun evaluate(ctx: EvaluationContext): EvaluationResult
    abstract fun eachChild(visit: (Expression) -> Unit)
    abstract fun getOperator(): kotlin.String
    open fun serialize(): List<Any?> = buildList {
        add(getOperator())
        eachChild { add(it.serialize()) }
    }
    open fun possibleOutputs(): List<Value?> = emptyList()

    fun has(dep: Int): kotlin.Boolean = (dependencies and dep) != 0

    override fun equals(other: Any?): kotlin.Boolean {
        if (this === other) return true
        if (other !is Expression) return false
        if (kind != other.kind) return false
        return this === other // subclasses override equals
    }

    override fun hashCode(): Int = kind.hashCode()
}

enum class Kind {
    Coalesce, CompoundExpression, Literal, At, Interpolate, Assertion, Length, Step,
    Let, Var, CollatorExpression, Coercion, Match, Error, Case, Any, All, Comparison,
    FormatExpression, FormatSectionOverride, NumberFormat, ImageExpression, In, Within,
    Distance, IndexOf, Slice,
}
