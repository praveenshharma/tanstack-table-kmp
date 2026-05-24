package io.github.tanstacktable.core

/*
 * Per-column faceted unique-values builder. Counts how often each distinct
 * cell value appears across the column's faceted row model.
 */

/**
 * Builds the per-column faceted-unique-values factory. The returned
 * function is invoked once per `(table, columnId)` pair and produces a
 * memoised getter for a `value -> count` map. Iteration order matches the
 * order each value was first seen.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> getFacetedUniqueValues(): (table: Table<TData>, columnId: String) -> () -> Map<Any?, Int> {
    return factory@{ table, columnId ->
        val memoized: () -> Map<Any?, Int> = memo<Map<Any?, Int>>(
            getDeps = { listOf(table.getColumn(columnId)?.getFacetedRowModel()) },
            fn = fn@{ deps ->
                @Suppress("UNCHECKED_CAST")
                val facetedRowModel = deps[0] as RowModel<TData>?

                if (facetedRowModel == null) return@fn LinkedHashMap<Any?, Int>()

                val facetedUniqueValues = LinkedHashMap<Any?, Int>()

                for (i in facetedRowModel.flatRows.indices) {
                    val values = facetedRowModel.flatRows[i].getUniqueValues(columnId)

                    for (j in values.indices) {
                        val value = values[j]

                        if (facetedUniqueValues.containsKey(value)) {
                            facetedUniqueValues[value] = (facetedUniqueValues[value] ?: 0) + 1
                        } else {
                            facetedUniqueValues[value] = 1
                        }
                    }
                }

                facetedUniqueValues
            },
            opts = getMemoOptions(
                table.options,
                "debugTable",
                "getFacetedUniqueValues_$columnId",
            ),
        )
                return@factory { memoized() }
    }
}
