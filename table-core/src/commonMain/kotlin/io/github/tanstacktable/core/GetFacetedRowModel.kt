package io.github.tanstacktable.core

/*
 * Per-column faceted row-model builder. Returns the rows that would be
 * visible if every column filter except the column's own were applied,
 * plus the global filter when active.
 */

/**
 * Builds the per-column faceted-row-model factory. The returned function
 * is invoked once per `(table, columnId)` pair and produces a memoised
 * getter for that column's [RowModel].
 *
 * When the pre-filtered model is empty and no filters are active, the
 * pre-filtered model is returned as-is.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> getFacetedRowModel(): (table: Table<TData>, columnId: String) -> () -> RowModel<TData> {
    return factory@{ table, columnId ->
        val memoized: () -> RowModel<TData> = memo<RowModel<TData>>(
            getDeps = {
                listOf(
                    table.getPreFilteredRowModel(),
                    table.getState().columnFilters,
                    table.getState().globalFilter,
                    // Listed only so changes to the filtered row-model retrigger this memo.
                    table.getFilteredRowModel(),
                )
            },
            fn = { deps ->
                @Suppress("UNCHECKED_CAST")
                val preRowModel = deps[0] as RowModel<TData>

                @Suppress("UNCHECKED_CAST")
                val columnFilters = deps[1] as ColumnFiltersState?

                val globalFilter = deps[2]

                if (
                    preRowModel.rows.isEmpty() ||
                    ((columnFilters == null || columnFilters.isEmpty()) && !isTruthy(globalFilter))
                ) {
                    return@memo preRowModel
                }

                // All column-filter ids except this column's, plus the global
                // filter id when active. The early-return above guarantees
                // `columnFilters` is non-empty here.
                val filterableIds: List<String> = buildList {
                    addAll(columnFilters!!.map { d -> d.id }.filter { d -> d != columnId })
                    add(if (isTruthy(globalFilter)) "__global__" else null)
                }.filter { isTruthy(it) }.filterNotNull()

                val filterRowsImpl = filterRowsImpl@{ row: Row<TData> ->
                    // Horizontally filter rows through each column.
                    for (i in filterableIds.indices) {
                        if (row.columnFilters[filterableIds[i]] == false) {
                            return@filterRowsImpl false
                        }
                    }
                    true
                }

                filterRows(preRowModel.rows, filterRowsImpl, table)
            },
            opts = getMemoOptions(table.options, "debugTable", "getFacetedRowModel"),
        )
                return@factory { memoized() }
    }
}
