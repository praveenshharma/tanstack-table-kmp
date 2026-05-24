package io.github.tanstacktable.core

/*
 * Built-in sort functions used by the row-sorting pipeline. Each value is
 * a `SortingFn<Any?>` and is referenced by its key in [sortingFns]. A
 * comparator returns `-1` / `0` / `1`.
 */

/**
 * Splits on digit runs, capturing the runs so they survive the split — used
 * by the alphanumeric comparator below. The capturing group is significant:
 * `splitKeepingDelimiters` relies on the same alternating-segment shape.
 */
val reSplitAlphaNumeric: Regex = Regex("([0-9]+)")

/**
 * Case-insensitive alphanumeric comparator. Splits each value on digit
 * runs, then compares pairwise: digit runs numerically, text runs
 * lexicographically.
 */
val alphanumeric: SortingFn<Any?> = { rowA, rowB, columnId ->
    compareAlphanumeric(
        toString(rowA.getValue(columnId)).lowercase(),
        toString(rowB.getValue(columnId)).lowercase(),
    )
}

/**
 * Case-sensitive variant of [alphanumeric].
 */
val alphanumericCaseSensitive: SortingFn<Any?> = { rowA, rowB, columnId ->
    compareAlphanumeric(
        toString(rowA.getValue(columnId)),
        toString(rowB.getValue(columnId)),
    )
}

/**
 * Case-insensitive text comparator. More basic than [alphanumeric] (less
 * numeric support) but much faster.
 */
val text: SortingFn<Any?> = { rowA, rowB, columnId ->
    compareBasic(
        toString(rowA.getValue(columnId)).lowercase(),
        toString(rowB.getValue(columnId)).lowercase(),
    )
}

/**
 * Case-sensitive variant of [text].
 */
val textCaseSensitive: SortingFn<Any?> = { rowA, rowB, columnId ->
    compareBasic(
        toString(rowA.getValue(columnId)),
        toString(rowB.getValue(columnId)),
    )
}

/**
 * Date/time comparator. Tolerates nullish values and supports any operand
 * type that surfaces as a numeric epoch value or implements [Comparable]
 * (an ISO date string, a platform date type that implements `Comparable`,
 * ...). Opaque non-`Comparable` date objects cannot be ordered by this
 * comparator — configure a custom `sortingFn` in that case.
 */
val datetime: SortingFn<Any?> = { rowA, rowB, columnId ->
    val a = rowA.getValue(columnId)
    val b = rowB.getValue(columnId)

    // Can handle nullish values. Uses numeric/Comparable ordering rather
    // than equality because object identity is not meaningful here.
    compareDatetimeValues(a, b)
}

/**
 * Basic comparator: equal -> `0`, greater-than -> `1`, otherwise `-1`. No
 * stringification — the raw values go straight in.
 */
val basic: SortingFn<Any?> = { rowA, rowB, columnId ->
    compareBasic(rowA.getValue(columnId), rowB.getValue(columnId))
}

// Utils

/**
 * Equality first, then a greater-than check; returns `-1` when neither
 * holds (i.e. `a < b`).
 */
internal fun compareBasic(a: Any?, b: Any?): Int =
    if (a == b) 0 else if (genericGreaterThan(a, b)) 1 else -1

/**
 * Numeric-aware string conversion. Numbers stringify without a Kotlin-style
 * `.0` trailing decimal when integral (so `5`, not `5.0`); NaN and infinity
 * become the empty string. Strings pass through. Anything else becomes `""`.
 */
internal fun toString(a: Any?): String {
    if (a is Number) {
        val d = a.toDouble()
        if (d.isNaN() || d == Double.POSITIVE_INFINITY || d == Double.NEGATIVE_INFINITY) {
            return ""
        }
        return numberToJsString(a)
    }
    if (a is String) {
        return a
    }
    return ""
}

/**
 * Alphanumeric comparator on two strings. Mixed sorting is slow but very
 * inclusive of many edge cases — it handles numbers, mixed alphanumeric
 * combinations, and even null, undefined and infinity values.
 *
 * Splits both inputs into alternating digit / non-digit runs, then walks
 * the runs pairwise: when both runs parse as numbers, compares numerically;
 * when one does, the numeric run sorts first; otherwise compares
 * lexicographically. Length is the tie-break.
 */
internal fun compareAlphanumeric(aStr: String, bStr: String): Int {
    // Split on number groups, but keep the delimiter. Then remove empty
    // segments (the JS equivalent is `filter(Boolean)`).
    val a: MutableList<String> = splitKeepingDelimiters(aStr).filter { isTruthy(it) }.toMutableList()
    val b: MutableList<String> = splitKeepingDelimiters(bStr).filter { isTruthy(it) }.toMutableList()

    while (a.isNotEmpty() && b.isNotEmpty()) {
        val aa = a.removeAt(0)
        val bb = b.removeAt(0)

        val an = jsParseInt(aa)
        val bn = jsParseInt(bb)

        // Mirrors the JS pattern `[an, bn].sort()` — a default sort by
        // string conversion — so the subsequent NaN classification matches.
        val combo = jsDefaultSortPair(an, bn)

        // Both are strings.
        if (combo[0].isNaN()) {
            if (aa > bb) {
                return 1
            }
            if (bb > aa) {
                return -1
            }
            continue
        }

        // One is a string, one is a number.
        if (combo[1].isNaN()) {
            return if (an.isNaN()) -1 else 1
        }

        // Both are numbers.
        if (an > bn) {
            return 1
        }
        if (bn > an) {
            return -1
        }
    }

    return a.size - b.size
}

// Exports

/**
 * The set of built-in sort functions, keyed by name. Iteration order
 * matches insertion order.
 */
val sortingFns: Map<String, SortingFn<Any?>> = mapOf(
    "alphanumeric" to alphanumeric,
    "alphanumericCaseSensitive" to alphanumericCaseSensitive,
    "text" to text,
    "textCaseSensitive" to textCaseSensitive,
    "datetime" to datetime,
    "basic" to basic,
)

/**
 * Name of a built-in sort function. Modelled as [String] because Kotlin
 * cannot express a closed union of string literals — the names accepted at
 * runtime are the keys of [sortingFns].
 */
typealias BuiltInSortingFn = String

/*
 * ----------------------------------------------------------------------------
 * The helpers below reproduce JavaScript primitive operations that have no
 * direct commonMain equivalent. They exist so the comparators above can
 * stay faithful to the JS semantics they rely on.
 * ----------------------------------------------------------------------------
 */

/**
 * Stringifies a finite [Number] without a trailing `.0` for integral
 * values. Kotlin's `Double.toString()` always emits `.0`; this matches the
 * JS `String(number)` shape so caller logic that compares against
 * stringified numbers does not see cosmetic differences.
 */
private fun numberToJsString(n: Number): String {
    val d = n.toDouble()
    return if (d == d.toLong().toDouble()) {
        d.toLong().toString()
    } else {
        d.toString()
    }
}

/**
 * Leading-integer parse mimicking `parseInt(str, 10)`: skip leading
 * whitespace, take an optional sign and the longest run of ASCII base-10
 * digits, return `NaN` when no digit prefix exists. Returns [Double] so the
 * NaN sentinel survives.
 *
 * Restricts digits to the ASCII range `'0'..'9'` — `Char.isDigit()` accepts
 * non-ASCII Unicode digits too, which would be a behavioural drift.
 */
private fun jsParseInt(str: String): Double {
    val s = str.trimStart()
    var i = 0
    if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
    val digitsStart = i
    while (i < s.length && s[i] in '0'..'9') i++
    if (i == digitsStart) return Double.NaN
    val sign = if (s.isNotEmpty() && s[0] == '-') -1.0 else 1.0
    return sign * (s.substring(digitsStart, i).toDoubleOrNull() ?: return Double.NaN)
}

/**
 * Tokenises [s] into alternating digit / non-digit runs, preserving order
 * — equivalent to `String.split(/([0-9]+)/)` after empties are dropped.
 *
 * Kotlin's `Regex.split` does not retain captured groups, which would
 * discard the numeric runs that [compareAlphanumeric] depends on.
 */
private fun splitKeepingDelimiters(s: String): List<String> {
    if (s.isEmpty()) return listOf("")
    val result = mutableListOf<String>()
    var start = 0
    var currentIsDigit = s[0] in '0'..'9'
    for (i in 1 until s.length) {
        val isDigit = s[i] in '0'..'9'
        if (isDigit != currentIsDigit) {
            result.add(s.substring(start, i))
            start = i
            currentIsDigit = isDigit
        }
    }
    result.add(s.substring(start))
    return result
}

/**
 * Returns `[x, y]` sorted by their JS-string forms (`numberToJsString` for
 * finite numbers, `"NaN"` for NaN). Mirrors `[x, y].sort()` so the
 * subsequent NaN classification in [compareAlphanumeric] is consistent.
 */
private fun jsDefaultSortPair(x: Double, y: Double): List<Double> {
    val sx = if (x.isNaN()) "NaN" else numberToJsString(x)
    val sy = if (y.isNaN()) "NaN" else numberToJsString(y)
    return if (sx <= sy) listOf(x, y) else listOf(y, x)
}

/**
 * Generic `>` for the operand kinds that [compareBasic] sees from the
 * `basic` / `text` sort fns. Numeric or string operands compare directly;
 * mixed types attempt a numeric coercion first and otherwise fall back to a
 * string comparison.
 */
private fun genericGreaterThan(a: Any?, b: Any?): Boolean {
    if (a is Number && b is Number) {
        val da = a.toDouble()
        val db = b.toDouble()
        return da > db
    }
    if (a is String && b is String) {
        return a > b
    }
    val na = jsToNumber(a)
    val nb = jsToNumber(b)
    if (!na.isNaN() && !nb.isNaN()) {
        return na > nb
    }
    return jsStringOf(a) > jsStringOf(b)
}

/**
 * Numeric coercion (`ToNumber`-style): numbers pass through, booleans
 * become `1.0`/`0.0`, numeric-looking strings parse, everything else
 * becomes `NaN`.
 */
private fun jsToNumber(v: Any?): Double = when (v) {
    null -> Double.NaN
    is Number -> v.toDouble()
    is Boolean -> if (v) 1.0 else 0.0
    is String -> if (v.isBlank()) 0.0 else (v.trim().toDoubleOrNull() ?: Double.NaN)
    else -> Double.NaN
}

/**
 * String coercion used as a last-resort fallback in [genericGreaterThan].
 */
private fun jsStringOf(v: Any?): String = when (v) {
    null -> "null"
    is Number -> if (v.toDouble().isNaN()) "NaN" else numberToJsString(v)
    else -> v.toString()
}

/**
 * Compares two date-like values using `>` / `<` semantics with tolerance
 * for nullish or non-comparable operands (returns `0`).
 *  - Two numbers compare as epoch values numerically.
 *  - Two values of the same `Comparable` runtime type use `compareTo`.
 *  - Anything else returns `0`.
 */
@Suppress("UNCHECKED_CAST")
private fun compareDatetimeValues(a: Any?, b: Any?): Int {
    if (a is Number && b is Number) {
        val da = a.toDouble()
        val db = b.toDouble()
        return if (da > db) 1 else if (da < db) -1 else 0
    }
    if (a is Comparable<*> && b is Comparable<*> && a::class == b::class) {
        val cmp = (a as Comparable<Any>).compareTo(b)
        return if (cmp > 0) 1 else if (cmp < 0) -1 else 0
    }
    return 0
}
