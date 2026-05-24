package io.github.tanstacktable.core

/*
 * Per-column faceted min/max-values builder. Scans the column's faceted
 * row model and returns the smallest/largest numeric value seen.
 */

/**
 * Builds the per-column faceted-min/max-values factory. The returned
 * function is invoked once per `(table, columnId)` pair and produces a
 * memoised getter for `(min, max)` as a [Pair] of [Double]s, or `null`
 * when no numeric values are present.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> getFacetedMinMaxValues(): (table: Table<TData>, columnId: String) -> () -> Pair<Double, Double>? {
    return factory@{ table, columnId ->
        val memoized: () -> Pair<Double, Double>? = memo<Pair<Double, Double>?>(
            getDeps = { listOf(table.getColumn(columnId)?.getFacetedRowModel()) },
            fn = fn@{ deps ->
                @Suppress("UNCHECKED_CAST")
                val facetedRowModel = deps[0] as RowModel<TData>?

                if (facetedRowModel == null) return@fn null

                // Collect every unique value across the faceted rows, coerce to
                // `Double` and drop NaNs.
                val uniqueValues: List<Double> = facetedRowModel.flatRows
                    .flatMap { flatRow -> flatRow.getUniqueValues(columnId) }
                    .map { jsNumber(it) }
                    .filter { value -> !value.isNaN() }

                if (uniqueValues.isEmpty()) return@fn null

                var facetedMinValue = uniqueValues[0]
                var facetedMaxValue = uniqueValues[uniqueValues.size - 1]

                for (value in uniqueValues) {
                    if (value < facetedMinValue) {
                        facetedMinValue = value
                    } else if (value > facetedMaxValue) {
                        facetedMaxValue = value
                    }
                }

                Pair(facetedMinValue, facetedMaxValue)
            },
            opts = getMemoOptions(table.options, "debugTable", "getFacetedMinMaxValues"),
        )
                return@factory { memoized() }
    }
}

/**
 * Coerces an arbitrary value to [Double] using JavaScript `Number(...)`
 * semantics:
 *  - a [Number] passes through unchanged,
 *  - a [Boolean] becomes `1.0` / `0.0`,
 *  - a [String] becomes its parsed numeric value, `0.0` when blank, or
 *    `NaN` when not numeric,
 *  - anything else (including `null`) becomes `NaN`.
 *
 * Note: JavaScript distinguishes `Number(undefined) === NaN` from
 * `Number(null) === 0`. Kotlin has only `null`, so we treat it as
 * `undefined`. The downstream filter discards NaNs anyway.
 */
private fun jsNumber(value: Any?): Double = when (value) {
    null -> Double.NaN
    is Number -> value.toDouble()
    is Boolean -> if (value) 1.0 else 0.0
    is String -> if (value.isBlank()) 0.0 else (value.trim().toDoubleOrNull() ?: Double.NaN)
    else -> Double.NaN
}
