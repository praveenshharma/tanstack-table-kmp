package io.github.tanstacktable.core

/*
 * Expanded row-model builder. Flattens the row tree so expanded sub-rows
 * appear inline with their parents, when row pagination is enabled and
 * `paginateExpandedRows` is true.
 */

/**
 * Builds the expanded row-model factory. The returned function is invoked
 * once per [Table] and produces a memoised getter for the expanded
 * [RowModel]. When no rows are expanded, or `paginateExpandedRows` is
 * disabled, the pre-expanded model is returned unchanged.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> getExpandedRowModel(): (table: Table<TData>) -> () -> RowModel<TData> {
    return factory@{ table ->
        val memoized: () -> RowModel<TData> = memo<RowModel<TData>>(
            getDeps = {
                listOf(
                    table.getState().expanded,
                    table.getPreExpandedRowModel(),
                    table.options.paginateExpandedRows,
                )
            },
            fn = { deps ->
                val expanded = deps[0]

                @Suppress("UNCHECKED_CAST")
                val rowModel = deps[1] as RowModel<TData>

                val paginateExpandedRows = deps[2]

                // `expanded` is either the literal `true` or a `Map<String, Boolean>`.
                // When it is a map, count the entries; otherwise treat the count as 0.
                val expandedKeyCount = (expanded as? Map<*, *>)?.size ?: 0
                if (
                    rowModel.rows.isEmpty() ||
                    (expanded != true && expandedKeyCount == 0)
                ) {
                    return@memo rowModel
                }

                if (paginateExpandedRows != true) {
                    // Only expand rows at this point if they are being paginated.
                    return@memo rowModel
                }

                expandRows(rowModel)
            },
            opts = getMemoOptions(table.options, "debugTable", "getExpandedRowModel"),
        )
                return@factory { memoized() }
    }
}

/**
 * Returns a new [RowModel] whose `rows` walk every expanded sub-row inline
 * with its parent, in source order.
 */
fun <TData> expandRows(rowModel: RowModel<TData>): RowModel<TData> {
    val expandedRows = mutableListOf<Row<TData>>()

    fun handleRow(row: Row<TData>) {
        expandedRows.add(row)

        if (row.subRows.isNotEmpty() && row.getIsExpanded()) {
            row.subRows.forEach { handleRow(it) }
        }
    }

    rowModel.rows.forEach { handleRow(it) }

    return RowModel(
        rows = expandedRows,
        flatRows = rowModel.flatRows,
        rowsById = rowModel.rowsById,
    )
}
