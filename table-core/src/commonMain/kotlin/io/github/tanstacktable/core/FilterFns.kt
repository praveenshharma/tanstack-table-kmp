package io.github.tanstacktable.core

/*
 * Built-in filter functions used by the column-filtering and global-filtering
 * pipelines. Each entry is a callable [FilterFn] that may also carry
 * `autoRemove` and `resolveFilterValue` members.
 *
 * Function values in Kotlin cannot carry properties, so `FilterFn` is an
 * interface with an `operator fun invoke(...)` plus the two optional members.
 * Each entry below is an anonymous object that fills in only the members the
 * built-in needs.
 */

/**
 * Case-insensitive substring filter. Stringifies both the cell value and the
 * filter value before comparing. A nullish cell value short-circuits to
 * `false`.
 */
val includesString: FilterFn<Any?> = object : FilterFn<Any?> {
    override fun invoke(
        row: Row<Any?>,
        columnId: String,
        filterValue: Any?,
        addMeta: (FilterMeta) -> Unit,
    ): Boolean {
        val search = filterValue?.toString()?.lowercase()
        // When `search` is null (filterValue was nullish), the original
        // JS substring search would look for the literal `"undefined"`
        // because `String.includes(undefined)` coerces to that.
        return (row.getValue(columnId)?.toString()?.lowercase()
            ?.contains(search ?: "undefined")) == true
    }

    override val autoRemove: ColumnFilterAutoRemoveTestFn<Any?> =
        { value, _column -> testFalsey(value) }
}

/**
 * Case-sensitive variant of [includesString].
 */
val includesStringSensitive: FilterFn<Any?> = object : FilterFn<Any?> {
    override fun invoke(
        row: Row<Any?>,
        columnId: String,
        filterValue: Any?,
        addMeta: (FilterMeta) -> Unit,
    ): Boolean {
        return (row.getValue(columnId)?.toString()
            ?.contains(filterValue.toString())) == true
    }

    override val autoRemove: ColumnFilterAutoRemoveTestFn<Any?> =
        { value, _column -> testFalsey(value) }
}

/**
 * Case-insensitive whole-string equality.
 */
val equalsString: FilterFn<Any?> = object : FilterFn<Any?> {
    override fun invoke(
        row: Row<Any?>,
        columnId: String,
        filterValue: Any?,
        addMeta: (FilterMeta) -> Unit,
    ): Boolean {
        return row.getValue(columnId)?.toString()?.lowercase() ==
            filterValue?.toString()?.lowercase()
    }

    override val autoRemove: ColumnFilterAutoRemoveTestFn<Any?> =
        { value, _column -> testFalsey(value) }
}

/**
 * Tests whether the cell value (a list) contains [filterValue].
 */
val arrIncludes: FilterFn<Any?> = object : FilterFn<Any?> {
    override fun invoke(
        row: Row<Any?>,
        columnId: String,
        filterValue: Any?,
        addMeta: (FilterMeta) -> Unit,
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        return ((row.getValue(columnId) as List<Any?>?)?.contains(filterValue)) == true
    }

    override val autoRemove: ColumnFilterAutoRemoveTestFn<Any?> =
        { value, _column -> testFalsey(value) }
}

/**
 * Tests whether the cell value (a list) contains every element of
 * [filterValue].
 */
val arrIncludesAll: FilterFn<Any?> = object : FilterFn<Any?> {
    override fun invoke(
        row: Row<Any?>,
        columnId: String,
        filterValue: Any?,
        addMeta: (FilterMeta) -> Unit,
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        return !((filterValue as List<Any?>).any { value ->
            !(((row.getValue(columnId) as List<Any?>?)?.contains(value)) == true)
        })
    }

    override val autoRemove: ColumnFilterAutoRemoveTestFn<Any?> =
        { value, _column ->
            testFalsey(value) || !isTruthy(lengthOf(value))
        }
}

/**
 * Tests whether the cell value (a list) contains any element of [filterValue].
 */
val arrIncludesSome: FilterFn<Any?> = object : FilterFn<Any?> {
    override fun invoke(
        row: Row<Any?>,
        columnId: String,
        filterValue: Any?,
        addMeta: (FilterMeta) -> Unit,
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        return (filterValue as List<Any?>).any { value ->
            ((row.getValue(columnId) as List<Any?>?)?.contains(value)) == true
        }
    }

    override val autoRemove: ColumnFilterAutoRemoveTestFn<Any?> =
        { value, _column ->
            testFalsey(value) || !isTruthy(lengthOf(value))
        }
}

/**
 * Strict equality between the cell value and [filterValue] using JavaScript
 * `===` semantics (value-equal for primitives, reference-equal otherwise).
 */
val equals: FilterFn<Any?> = object : FilterFn<Any?> {
    override fun invoke(
        row: Row<Any?>,
        columnId: String,
        filterValue: Any?,
        addMeta: (FilterMeta) -> Unit,
    ): Boolean {
        return jsStrictEquals(row.getValue(columnId), filterValue)
    }

    override val autoRemove: ColumnFilterAutoRemoveTestFn<Any?> =
        { value, _column -> testFalsey(value) }
}

/**
 * Loose equality between the cell value and [filterValue] using JavaScript
 * `==` semantics (type coercion for cross-type primitive comparisons).
 * `ToPrimitive` coercion of arbitrary objects is not reproduced — object
 * operands fall back to reference equality.
 */
val weakEquals: FilterFn<Any?> = object : FilterFn<Any?> {
    override fun invoke(
        row: Row<Any?>,
        columnId: String,
        filterValue: Any?,
        addMeta: (FilterMeta) -> Unit,
    ): Boolean {
        return jsLooseEquals(row.getValue(columnId), filterValue)
    }

    override val autoRemove: ColumnFilterAutoRemoveTestFn<Any?> =
        { value, _column -> testFalsey(value) }
}

/**
 * Range filter for numeric cell values. [filterValue] is a `[min, max]`
 * pair; non-numeric / null bounds become `-Infinity` / `+Infinity`, and an
 * inverted pair is swapped.
 */
val inNumberRange: FilterFn<Any?> = object : FilterFn<Any?> {
    override fun invoke(
        row: Row<Any?>,
        columnId: String,
        filterValue: Any?,
        addMeta: (FilterMeta) -> Unit,
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val fv = filterValue as List<Number>
        val min = fv[0]
        val max = fv[1]

        val rowValue = (row.getValue(columnId) as Number).toDouble()
        return rowValue >= min.toDouble() && rowValue <= max.toDouble()
    }

    override val autoRemove: ColumnFilterAutoRemoveTestFn<Any?> =
        { value, _column ->
            @Suppress("UNCHECKED_CAST")
            testFalsey(value) ||
                (testFalsey((value as List<Any?>)[0]) && testFalsey(value[1]))
        }

    override val resolveFilterValue: TransformFilterValueFn<Any?> =
        { value, _column ->
            @Suppress("UNCHECKED_CAST")
            val list = value as List<Any?>
            val unsafeMin = list[0]
            val unsafeMax = list[1]

            val parsedMin: Double =
                if (unsafeMin !is Number) jsParseFloat(unsafeMin) else unsafeMin.toDouble()
            val parsedMax: Double =
                if (unsafeMax !is Number) jsParseFloat(unsafeMax) else unsafeMax.toDouble()

            // Null bounds collapse to ±Infinity; non-numeric values that
            // failed parsing also become unbounded.
            var min: Double =
                if (unsafeMin == null || parsedMin.isNaN()) Double.NEGATIVE_INFINITY else parsedMin
            var max: Double =
                if (unsafeMax == null || parsedMax.isNaN()) Double.POSITIVE_INFINITY else parsedMax

            if (min > max) {
                val temp = min
                min = max
                max = temp
            }

            listOf(min, max)
        }
}

// Export

/**
 * The set of built-in filter functions. Supports both `.includesString` /
 * `.equals` member access and `filterFns[key]` lookup. Returns `null` for
 * an unknown key.
 */
object filterFns {
    val includesString: FilterFn<Any?> = io.github.tanstacktable.core.includesString
    val includesStringSensitive: FilterFn<Any?> =
        io.github.tanstacktable.core.includesStringSensitive
    val equalsString: FilterFn<Any?> = io.github.tanstacktable.core.equalsString
    val arrIncludes: FilterFn<Any?> = io.github.tanstacktable.core.arrIncludes
    val arrIncludesAll: FilterFn<Any?> = io.github.tanstacktable.core.arrIncludesAll
    val arrIncludesSome: FilterFn<Any?> = io.github.tanstacktable.core.arrIncludesSome
    val equals: FilterFn<Any?> = io.github.tanstacktable.core.equals
    val weakEquals: FilterFn<Any?> = io.github.tanstacktable.core.weakEquals
    val inNumberRange: FilterFn<Any?> = io.github.tanstacktable.core.inNumberRange

    /**
     * Looks up a built-in filter fn by name. Returns `null` when the name is
     * unknown.
     */
    operator fun get(key: String): FilterFn<Any?>? = when (key) {
        "includesString" -> includesString
        "includesStringSensitive" -> includesStringSensitive
        "equalsString" -> equalsString
        "arrIncludes" -> arrIncludes
        "arrIncludesAll" -> arrIncludesAll
        "arrIncludesSome" -> arrIncludesSome
        "equals" -> equals
        "weakEquals" -> weakEquals
        "inNumberRange" -> inNumberRange
        else -> null
    }
}

/**
 * Name of a built-in filter function. Modelled as [String] because Kotlin
 * cannot express a closed union of string literals.
 */
typealias BuiltInFilterFn = String

// Utils

/**
 * Returns `true` for `null` or the empty string. Note: this is not general
 * truthiness — `testFalsey(0)` is `false`.
 */
internal fun testFalsey(value: Any?): Boolean =
    value == null || value == ""

/**
 * Returns the length of [value] when it is a list, string or array; `null`
 * otherwise. Used by `autoRemove` to detect empty filter values.
 */
private fun lengthOf(value: Any?): Number? = when (value) {
    is List<*> -> value.size
    is String -> value.length
    is Array<*> -> value.size
    else -> null
}

/**
 * Mimics `parseFloat`: coerces [value] to a string, skips leading
 * whitespace, then parses the longest leading run that forms a valid float
 * (sign, digits, decimal point, exponent, or the literal `Infinity`).
 * Returns [Double.NaN] when no such prefix exists.
 *
 * Only ASCII digits are accepted, matching JS — Kotlin's `Char.isDigit()`
 * accepts more.
 */
private fun jsParseFloat(value: Any?): Double {
    val s = value.toString().trimStart()
    if (s.isEmpty()) return Double.NaN

    var i = 0
    if (i < s.length && (s[i] == '+' || s[i] == '-')) i++

    // Accept the literal `Infinity` (with optional sign).
    if (s.regionMatches(i, "Infinity", 0, "Infinity".length)) {
        return if (s.isNotEmpty() && s[0] == '-') {
            Double.NEGATIVE_INFINITY
        } else {
            Double.POSITIVE_INFINITY
        }
    }

    var sawDigit = false
    while (i < s.length && s[i] in '0'..'9') {
        i++; sawDigit = true
    }
    if (i < s.length && s[i] == '.') {
        i++
        while (i < s.length && s[i] in '0'..'9') {
            i++; sawDigit = true
        }
    }
    if (sawDigit && i < s.length && (s[i] == 'e' || s[i] == 'E')) {
        val expStart = i
        i++
        if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
        var expDigit = false
        while (i < s.length && s[i] in '0'..'9') {
            i++; expDigit = true
        }
        // No exponent digits — drop the bare `e` from the parsed prefix.
        if (!expDigit) i = expStart
    }

    if (!sawDigit) return Double.NaN
    return s.substring(0, i).toDoubleOrNull() ?: Double.NaN
}

/**
 * Strict-equality implementation matching JavaScript `===`: different types
 * are unequal; numbers compare by value (NaN never equal); strings and
 * booleans compare by value; everything else compares by reference.
 */
private fun jsStrictEquals(a: Any?, b: Any?): Boolean = when {
    a == null || b == null -> a == null && b == null
    a is Number && b is Number -> {
        val x = a.toDouble()
        val y = b.toDouble()
        !x.isNaN() && !y.isNaN() && x == y
    }
    a is String && b is String -> a == b
    a is Boolean && b is Boolean -> a == b
    else -> a === b
}

/**
 * Loose-equality implementation matching JavaScript `==` for the primitive
 * operand types (boolean coerces to number; number/string compare as
 * numbers; same-type primitives compare by value). Arbitrary object operands
 * fall back to reference equality because `ToPrimitive` coercion has no
 * portable commonMain form.
 */
private fun jsLooseEquals(a: Any?, b: Any?): Boolean = when {
    a == null || b == null -> a == null && b == null
    a is Boolean -> jsLooseEquals(if (a) 1.0 else 0.0, b)
    b is Boolean -> jsLooseEquals(a, if (b) 1.0 else 0.0)
    a is Number && b is Number -> {
        val x = a.toDouble()
        val y = b.toDouble()
        !x.isNaN() && !y.isNaN() && x == y
    }
    a is String && b is String -> a == b
    a is Number && b is String -> {
        val x = a.toDouble()
        val y = jsToNumber(b)
        !x.isNaN() && !y.isNaN() && x == y
    }
    a is String && b is Number -> jsLooseEquals(b, a)
    else -> a === b
}

/**
 * `Number(string)` coercion used by [jsLooseEquals]: whitespace is trimmed,
 * an empty/whitespace string becomes `0.0`, an otherwise fully-numeric
 * string becomes its value, anything else becomes `NaN`. Non-decimal numeric
 * literals (hex, binary, octal) are not supported.
 */
private fun jsToNumber(s: String): Double {
    val t = s.trim()
    if (t.isEmpty()) return 0.0
    return t.toDoubleOrNull() ?: Double.NaN
}
