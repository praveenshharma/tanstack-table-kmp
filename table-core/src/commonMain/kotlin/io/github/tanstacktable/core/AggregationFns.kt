package io.github.tanstacktable.core

import kotlin.math.floor

/*
 * Built-in aggregation functions used by the grouping/aggregation pipeline.
 * Each value is an `AggregationFn<Any?>` and is referenced by its key in
 * [aggregationFns].
 */

/**
 * Sums numeric child-row aggregations. Non-numeric values contribute zero.
 * Adds aggregations directly rather than walking leaf rows for speed.
 */
val sum: AggregationFn<Any?> = { columnId, _leafRows, childRows ->
    // It's faster to just add the aggregations together instead of
    // processing leaf nodes individually.
    childRows.fold(0.0) { sum, next ->
        val nextValue = next.getValue(columnId)
        sum + (if (nextValue is Number) nextValue.toDouble() else 0.0)
    }
}

/**
 * Returns the minimum numeric value across child rows, or `null` if no
 * numeric values are present. NaN values are skipped (matching the
 * `value >= value` self-comparison guard).
 */
val min: AggregationFn<Any?> = { columnId, _leafRows, childRows ->
    var min: Double? = null

    childRows.forEach { row ->
        val value = (row.getValue(columnId) as? Number)?.toDouble()

        // When `min` is still null, only adopt a non-NaN initial value.
        if (
            value != null &&
            ((min != null && min!! > value) || (min == null && value >= value))
        ) {
            min = value
        }
    }

    min
}

/**
 * Returns the maximum numeric value across child rows, or `null` if no
 * numeric values are present. NaN values are skipped.
 */
val max: AggregationFn<Any?> = { columnId, _leafRows, childRows ->
    var max: Double? = null

    childRows.forEach { row ->
        val value = (row.getValue(columnId) as? Number)?.toDouble()
        if (
            value != null &&
            ((max != null && max!! < value) || (max == null && value >= value))
        ) {
            max = value
        }
    }

    max
}

/**
 * Returns `listOf(min, max)` across child rows. Each entry is `Double?`,
 * `null` when no numeric values were seen. NaN values are skipped.
 */
val extent: AggregationFn<Any?> = { columnId, _leafRows, childRows ->
    var min: Double? = null
    var max: Double? = null

    childRows.forEach { row ->
        val value = (row.getValue(columnId) as? Number)?.toDouble()
        if (value != null) {
            if (min == null) {
                if (value >= value) {
                    min = value
                    max = value
                }
            } else {
                if (min!! > value) min = value
                if (max!! < value) max = value
            }
        }
    }

    listOf(min, max)
}

/**
 * Returns the arithmetic mean of numeric leaf-row values, or `null` if no
 * numeric values are present. Non-numeric values coerce to NaN and are
 * filtered out by the `value >= value` self-comparison.
 */
val mean: AggregationFn<Any?> = { columnId, leafRows, _childRows ->
    var count = 0
    var sum = 0.0

    leafRows.forEach { row ->
        val raw = row.getValue(columnId)
        // Mirror JavaScript's unary-plus coercion: numbers pass through, any
        // other value becomes NaN and is rejected by the self-comparison guard.
        val value = if (raw is Number) raw.toDouble() else Double.NaN
        if (raw != null && value >= value) {
            ++count
            sum += value
        }
    }

    if (count != 0) sum / count else null
}

/**
 * Returns the median of numeric leaf-row values. Returns `null` for an empty
 * input or when any value is non-numeric. For an even count, returns the
 * average of the two middle elements.
 */
val median: AggregationFn<Any?> = fn@{ columnId, leafRows, _childRows ->
    if (leafRows.isEmpty()) {
        return@fn null
    }

    val values: List<Any?> = leafRows.map { row -> row.getValue(columnId) }
    if (!isNumberArray(values)) {
        return@fn null
    }
    if (values.size == 1) {
        return@fn values[0]
    }

    val mid = floor(values.size / 2.0).toInt()
    val nums = values.map { (it as Number).toDouble() }.sortedBy { it }
    if (values.size % 2 != 0) nums[mid] else (nums[mid - 1] + nums[mid]) / 2
}

/**
 * Returns the distinct leaf-row values in first-insertion order.
 */
val unique: AggregationFn<Any?> = { columnId, leafRows, _childRows ->
    leafRows.map { d -> d.getValue(columnId) }.toSet().toList()
}

/**
 * Returns the count of distinct leaf-row values.
 */
val uniqueCount: AggregationFn<Any?> = { columnId, leafRows, _childRows ->
    leafRows.map { d -> d.getValue(columnId) }.toSet().size
}

/**
 * Returns the number of leaf rows.
 */
val count: AggregationFn<Any?> = { _columnId, leafRows, _childRows ->
    leafRows.size
}

/**
 * The set of built-in aggregation functions, keyed by name. Iteration order
 * is preserved.
 */
val aggregationFns: Map<String, AggregationFn<Any?>> = mapOf(
    "sum" to sum,
    "min" to min,
    "max" to max,
    "extent" to extent,
    "mean" to mean,
    "median" to median,
    "unique" to unique,
    "uniqueCount" to uniqueCount,
    "count" to count,
)

/**
 * Name of a built-in aggregation function. Modelled as [String] because Kotlin
 * cannot express a closed union of string literals — the names accepted at
 * runtime are the keys of [aggregationFns].
 */
typealias BuiltInAggregationFn = String
