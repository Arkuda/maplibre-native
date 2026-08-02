package org.maplibre.kotlin.expression

/**
 * Expression type system. Mirrors mbgl::style::expression::type::Type.
 */
sealed class Type {
    object NullType : Type() {
        override fun toString(): kotlin.String = "null"
    }

    object NumberType : Type() {
        override fun toString(): kotlin.String = "number"
    }

    object BooleanType : Type() {
        override fun toString(): kotlin.String = "boolean"
    }

    object StringType : Type() {
        override fun toString(): kotlin.String = "string"
    }

    object ColorType : Type() {
        override fun toString(): kotlin.String = "color"
    }

    object PaddingType : Type() {
        override fun toString(): kotlin.String = "padding"
    }

    object VariableAnchorOffsetCollectionType : Type() {
        override fun toString(): kotlin.String = "variableAnchorOffsetCollection"
    }

    object ObjectType : Type() {
        override fun toString(): kotlin.String = "object"
    }

    object ValueType : Type() {
        override fun toString(): kotlin.String = "value"
    }

    object CollatorType : Type() {
        override fun toString(): kotlin.String = "collator"
    }

    object FormattedType : Type() {
        override fun toString(): kotlin.String = "formatted"
    }

    object ImageType : Type() {
        override fun toString(): kotlin.String = "resolvedImage"
    }

    object ErrorType : Type() {
        override fun toString(): kotlin.String = "error"
    }

    data class ArrayType(val itemType: Type, val n: Int? = null) : Type() {
        override fun toString(): kotlin.String = when {
            n != null -> "array<$itemType, $n>"
            itemType == ValueType -> "array"
            else -> "array<$itemType>"
        }
    }

    companion object {
        val Null = NullType
        val Number = NumberType
        val Boolean = BooleanType
        val String = StringType
        val Color = ColorType
        val Padding = PaddingType
        val VariableAnchorOffsetCollection = VariableAnchorOffsetCollectionType
        val Value = ValueType
        val Object = ObjectType
        val Collator = CollatorType
        val Formatted = FormattedType
        val Error = ErrorType
        val Image = ImageType
    }
}

/** Type of the runtime [Value]. */
fun typeOf(value: Value): Type = when (value) {
    is Value.Null -> Type.Null
    is Value.Boolean -> Type.Boolean
    is Value.Number -> Type.Number
    is Value.String -> Type.String
    is Value.Color -> Type.Color
    is Value.Array -> Type.ArrayType(Type.Value)
    is Value.Object -> Type.Object
}

/** Converts a runtime [Value] to a human-readable string (for error messages). */
fun toString(value: Value): kotlin.String = when (value) {
    is Value.Null -> "null"
    is Value.Boolean -> value.value.toString()
    is Value.Number -> {
        if (value.value == value.value.toLong().toDouble()) {
            value.value.toLong().toString()
        } else {
            value.value.toString()
        }
    }
    is Value.String -> "\"${value.value}\""
    is Value.Color -> value.toString()
    is Value.Array -> value.values.joinToString(prefix = "[", postfix = "]") { toString(it) }
    is Value.Object ->
        value.properties.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k: ${toString(v)}" }
}

/**
 * Type of the expression that corresponds to a Kotlin value class.
 */
inline fun <reified T : Any> valueTypeToExpressionType(): Type = when (T::class) {
    Double::class, Float::class, Int::class, Long::class -> Type.Number
    String::class -> Type.String
    Boolean::class -> Type.Boolean
    else -> Type.Value
}
