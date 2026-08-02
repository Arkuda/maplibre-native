package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.maplibre.kotlin.expression.CompoundExpression
import org.maplibre.kotlin.expression.EvaluationContext
import org.maplibre.kotlin.expression.EvaluationResult
import org.maplibre.kotlin.expression.Expression
import org.maplibre.kotlin.expression.ExpressionParser
import org.maplibre.kotlin.expression.InterpolateExpression
import org.maplibre.kotlin.expression.Interpolator
import org.maplibre.kotlin.expression.LiteralExpression
import org.maplibre.kotlin.expression.MatchExpression
import org.maplibre.kotlin.expression.StepExpression
import org.maplibre.kotlin.expression.UnitBezier
import org.maplibre.kotlin.expression.Value
import org.maplibre.kotlin.expression.interpolationFactor
import kotlinx.serialization.json.Json
import kotlin.math.abs

private val json = Json { ignoreUnknownKeys = true }

private fun eval(exprJson: String, zoom: Float? = null): EvaluationResult {
    val parser = ExpressionParser()
    val expr = parser.parse(json.parseToJsonElement(exprJson))
    requireNotNull(expr) { "failed to parse: $exprJson" }
    return expr.evaluate(EvaluationContext(zoom = zoom))
}

private fun evalNum(exprJson: String, zoom: Float? = null): Double {
    val r = eval(exprJson, zoom)
    assertIs<EvaluationResult.Ok>(r)
    assertIs<Value.Number>(r.value)
    return r.value.value
}

private fun evalStr(exprJson: String): String {
    val r = eval(exprJson)
    assertIs<EvaluationResult.Ok>(r)
    assertIs<Value.String>(r.value)
    return r.value.value
}

private fun evalBool(exprJson: String): Boolean {
    val r = eval(exprJson)
    assertIs<EvaluationResult.Ok>(r)
    assertIs<Value.Boolean>(r.value)
    return r.value.value
}

class UnitBezierTest {
    @Test
    fun solvesKnownBezier() {
        // linear bezier (0,0)-(1,1): solve(0.5) == 0.5
        val linear = UnitBezier(0.0, 0.0, 1.0, 1.0)
        assertEquals(0.5, linear.solve(0.5, 1e-6), 1e-4)
        assertEquals(0.0, linear.solve(0.0, 1e-6), 1e-9)
        assertEquals(1.0, linear.solve(1.0, 1e-6), 1e-9)
    }

    @Test
    fun easeInOutBezier() {
        // ease-in-out: (0.42,0)-(0.58,1). At x=0.5, y ~ 0.5
        val bezier = UnitBezier(0.42, 0.0, 0.58, 1.0)
        val y = bezier.solve(0.5, 1e-6)
        assertEquals(0.5, y, 1e-2)
    }
}

class InterpolationFactorTest {
    @Test
    fun linearFactor() {
        assertEquals(0.5f, interpolationFactor(1.0f, org.maplibre.kotlin.expression.FloatRange(0f, 10f), 5f))
        assertEquals(0f, interpolationFactor(1.0f, org.maplibre.kotlin.expression.FloatRange(0f, 10f), 0f))
        assertEquals(1f, interpolationFactor(1.0f, org.maplibre.kotlin.expression.FloatRange(0f, 10f), 10f))
    }

    @Test
    fun exponentialFactor() {
        // base 2: (2^5 - 1)/(2^10 - 1)
        val f = interpolationFactor(2.0f, org.maplibre.kotlin.expression.FloatRange(0f, 10f), 5f)
        val expected = (32.0 - 1) / (1024.0 - 1)
        assertEquals(expected.toFloat(), f, 1e-6f)
    }

    @Test
    fun zeroDiffReturnsZero() {
        assertEquals(0f, interpolationFactor(1.0f, org.maplibre.kotlin.expression.FloatRange(5f, 5f), 5f))
    }
}

class StepExpressionTest {
    @Test
    fun stepChoosesLastStopBelowInput() {
        // ["step", ["zoom"], 1, 10, 2, 20, 3]: base=1, at z>=10 -> 2, at z>=20 -> 3
        val r = eval("""["step", ["zoom"], 1, 10, 2, 20, 3]""", zoom = 12f)
        assertIs<EvaluationResult.Ok>(r)
        assertIs<Value.Number>(r.value)
        assertEquals(2.0, r.value.value)
    }

    @Test
    fun stepBelowFirstStop() {
        // zoom below first stop -> base output
        val r = eval("""["step", ["zoom"], 1, 10, 2, 20, 3]""", zoom = 5f)
        assertIs<EvaluationResult.Ok>(r)
        assertEquals(1.0, (r.value as Value.Number).value)
    }

    @Test
    fun stepAboveLastStop() {
        val r = eval("""["step", ["zoom"], 1, 10, 2, 20, 3]""", zoom = 25f)
        assertIs<EvaluationResult.Ok>(r)
        assertEquals(3.0, (r.value as Value.Number).value)
    }
}

class InterpolateExpressionTest {
    @Test
    fun linearInterpolation() {
        // ["interpolate", ["linear"], ["zoom"], 0, 100, 10, 200]
        val r = eval("""["interpolate", ["linear"], ["zoom"], 0, 100, 10, 200]""", zoom = 5f)
        assertIs<EvaluationResult.Ok>(r)
        assertIs<Value.Number>(r.value)
        assertEquals(150.0, r.value.value, 1e-6)
    }

    @Test
    fun interpolationClampsOutsideRange() {
        assertEquals(100.0, evalNum("""["interpolate", ["linear"], ["zoom"], 0, 100, 10, 200]""", zoom = -5f), 1e-6)
        assertEquals(200.0, evalNum("""["interpolate", ["linear"], ["zoom"], 0, 100, 10, 200]""", zoom = 15f), 1e-6)
    }

    @Test
    fun exponentialInterpolation() {
        // base 2, zoom 0..10, 100..200: at z=5 => 100 + (2^5-1)/(2^10-1)*100
        val expected = 100.0 + (32.0 - 1) / (1024.0 - 1) * 100.0
        assertEquals(expected, evalNum("""["interpolate", ["exponential", 2], ["zoom"], 0, 100, 10, 200]""", zoom = 5f), 1e-3)
    }

    @Test
    fun colorInterpolation() {
        // interpolate between black and white at 0.5 => gray 128
        val r = eval("""["interpolate", ["linear"], ["zoom"], 0, "#000000", 10, "#ffffff"]""", zoom = 5f)
        assertIs<EvaluationResult.Ok>(r)
        assertIs<Value.Color>(r.value)
        val c = r.value
        assertEquals(0.5, c.r, 1e-6)
        assertEquals(0.5, c.g, 1e-6)
        assertEquals(0.5, c.b, 1e-6)
        assertEquals(1.0, c.a, 1e-6)
    }
}

class MatchExpressionTest {
    @Test
    fun matchByString() {
        // ["match", ["get", "class"], "motorway", 10, "trunk", 5, 0]
        val exprJson = """["match", ["get", "class"], "motorway", 10, "trunk", 5, 0]"""
        val feature = object : EvaluationContext.Feature {
            override val id: Value? = null
            override val properties: Map<String, Value> = mapOf("class" to Value.String("trunk"))
        }
        val parser = ExpressionParser()
        val expr = parser.parse(json.parseToJsonElement(exprJson))!!
        val r = expr.evaluate(EvaluationContext(feature = feature))
        assertIs<EvaluationResult.Ok>(r)
        assertEquals(5.0, (r.value as Value.Number).value)
    }

    @Test
    fun matchFallback() {
        val exprJson = """["match", ["get", "class"], "motorway", 10, 0]"""
        val feature = object : EvaluationContext.Feature {
            override val id: Value? = null
            override val properties: Map<String, Value> = mapOf("class" to Value.String("residential"))
        }
        val parser = ExpressionParser()
        val expr = parser.parse(json.parseToJsonElement(exprJson))!!
        val r = expr.evaluate(EvaluationContext(feature = feature))
        assertIs<EvaluationResult.Ok>(r)
        assertEquals(0.0, (r.value as Value.Number).value)
    }
}

class CoalesceCaseLetTest {
    @Test
    fun coalescePicksFirstNonNull() {
        assertEquals(5.0, evalNum("""["coalesce", ["get", "missing"], 5]"""))
    }

    @Test
    fun casePicksFirstTrue() {
        val exprJson = """["case", ["==", 1, 2], "a", ["==", 2, 2], "b", "c"]"""
        assertEquals("b", evalStr(exprJson))
    }

    @Test
    fun letVar() {
        val exprJson = """["let", "x", 10, ["+", ["var", "x"], 5]]"""
        assertEquals(15.0, evalNum(exprJson))
    }

    @Test
    fun nestedLet() {
        val exprJson = """["let", "a", 1, ["let", "b", 2, ["+", ["var", "a"], ["var", "b"]]]]"""
        assertEquals(3.0, evalNum(exprJson))
    }
}

class CompoundExpressionTest {
    @Test
    fun arithmetic() {
        assertEquals(7.0, evalNum("""["+", 3, 4]"""))
        assertEquals(2.0, evalNum("""["-", 5, 3]"""))
        assertEquals(12.0, evalNum("""["*", 3, 4]"""))
        assertEquals(2.5, evalNum("""["/", 5, 2]"""))
        assertEquals(1.0, evalNum("""["%", 5, 2]"""))
        assertEquals(8.0, evalNum("""["^", 2, 3]"""))
    }

    @Test
    fun comparisons() {
        assertTrue(evalBool("""["<", 1, 2]"""))
        assertTrue(evalBool("""[">=", 2, 2]"""))
        assertTrue(evalBool("""["==", "a", "a"]"""))
        assertTrue(evalBool("""["!=", 1, 2]"""))
    }

    @Test
    fun booleanOps() {
        assertTrue(evalBool("""["all", true, ["!=", 1, 2]]"""))
        assertTrue(evalBool("""["any", false, true]"""))
        assertTrue(evalBool("""["!", false]"""))
    }

    @Test
    fun mathFunctions() {
        assertEquals(3.0, evalNum("""["abs", -3]"""))
        assertEquals(4.0, evalNum("""["ceil", 3.2]"""))
        assertEquals(3.0, evalNum("""["floor", 3.8]"""))
        assertEquals(3.0, evalNum("""["round", 3.2]"""))
        assertEquals(9.0, evalNum("""["sqrt", 81]"""))
        assertEquals(1.0, evalNum("""["ln", ["e"]]"""), 1e-9)
    }

    @Test
    fun coercion() {
        assertEquals(42.0, evalNum("""["to-number", "42"]"""))
        assertEquals("42", evalStr("""["to-string", 42]"""))
        assertTrue(evalBool("""["to-boolean", 1]"""))
    }

    @Test
    fun getFromFeature() {
        val exprJson = """["get", "name"]"""
        val feature = object : EvaluationContext.Feature {
            override val id: Value? = null
            override val properties: Map<String, Value> = mapOf("name" to Value.String("Berlin"))
        }
        val parser = ExpressionParser()
        val expr = parser.parse(json.parseToJsonElement(exprJson))!!
        val r = expr.evaluate(EvaluationContext(feature = feature))
        assertIs<EvaluationResult.Ok>(r)
        assertEquals("Berlin", (r.value as Value.String).value)
    }

    @Test
    fun stringFunctions() {
        assertEquals("hello world", evalStr("""["concat", "hello ", "world"]"""))
        assertEquals("abc", evalStr("""["downcase", "ABC"]"""))
        assertEquals("ABC", evalStr("""["upcase", "abc"]"""))
    }
}

class SerializationTest {
    @Test
    fun stepSerializes() {
        val expr = ExpressionParser().parse(json.parseToJsonElement("""["step", ["zoom"], 1, 10, 2]"""))!!
        val s = expr.serialize()
        assertEquals("step", s[0])
    }

    @Test
    fun interpolateSerializes() {
        val expr = ExpressionParser().parse(
            json.parseToJsonElement("""["interpolate", ["linear"], ["zoom"], 0, 1, 10, 2]"""),
        )!!
        val s = expr.serialize()
        assertEquals("interpolate", s[0])
        assertEquals(listOf("linear"), s[1])
    }
}
