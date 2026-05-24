package io.github.tanstacktable.core

/*
 * Core row-model builder. Walks the raw `data` array, materialising a [Row]
 * for every entry and recursing into sub-rows via `getSubRows`. The result
 * is the unfiltered, unsorted, ungrouped baseline that every other row
 * model derives from.
 */

/**
 * Builds the core row-model factory. The returned function is invoked once
 * per [Table] and produces a memoised getter for the core [RowModel].
 *
 * The memoised function is exposed as a zero-arg `() -> RowModel<TData>`
 * because the underlying [memo] always accepts a one-arg `(TArg) -> R`;
 * this wraps a `null` `depArgs` through to match the original `() => ...`
 * shape used everywhere row-model factories are read.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> getCoreRowModel(): (table: Table<TData>) -> () -> RowModel<TData> {
    return factory@{ table ->
        val memoized = memo<RowModel<TData>>(
            getDeps = { listOf(table.options.data) },
            fn = fn@{ deps ->
                @Suppress("UNCHECKED_CAST")
                val data = deps[0] as List<TData>

                // `RowModel` is immutable, so its three containers are built
                // up as locals and assembled at the end.
                val rowModelRows: MutableList<Row<TData>>
                val rowModelFlatRows = mutableListOf<Row<TData>>()
                val rowModelRowsById = mutableMapOf<String, Row<TData>>()

                fun accessRows(
                    originalRows: List<TData>,
                    depth: Int = 0,
                    parentRow: Row<TData>? = null,
                ): List<Row<TData>> {
                    val rows = mutableListOf<Row<TData>>()

                    for (i in originalRows.indices) {
                        // This could be an expensive check at scale, so we should move it somewhere else, but where?
                        // if (!id) {
                        //   if (process.env.NODE_ENV !== 'production') {
                        //     throw new Error(`getRowId expected an ID, but got ${id}`)
                        //   }
                        // }

                        // Make the row.
                        val row = createRow(
                            table,
                            table._getRowId(originalRows[i], i, parentRow),
                            originalRows[i],
                            i,
                            depth,
                            null,
                            parentRow?.id,
                        )

                        // Keep track of every row in a flat array.
                        rowModelFlatRows.add(row)
                        // Also keep track of every row by its ID.
                        rowModelRowsById[row.id] = row
                        // Push table row into parent.
                        rows.add(row)

                        // Get the original sub-rows.
                        val getSubRows = table.options.getSubRows
                        if (getSubRows != null) {
                            row.originalSubRows = getSubRows(originalRows[i], i)

                            // Then recursively access them.
                            val originalSubRows = row.originalSubRows
                            if (originalSubRows != null && originalSubRows.isNotEmpty()) {
                                row.subRows = accessRows(originalSubRows, depth + 1, row)
                            }
                        }
                    }

                    return rows
                }

                rowModelRows = accessRows(data).toMutableList()

                RowModel(
                    rows = rowModelRows,
                    flatRows = rowModelFlatRows,
                    rowsById = rowModelRowsById,
                )
            },
            opts = getMemoOptions(
                table.options,
                "debugTable",
                "getRowModel",
            ) { table._autoResetPageIndex() },
        )
        return@factory { memoized() }
    }
}
