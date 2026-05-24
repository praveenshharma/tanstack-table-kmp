package io.github.tanstacktable.core

/*
 * Filtered row-model builder. Runs each row through every active column
 * filter plus the global filter, tagging it with the per-filter results in
 * `columnFilters` / `columnFiltersMeta`, then filters the row tree.
 */

/**
 * Builds the table-level filtered row-model factory. The returned function
 * is invoked once per [Table] and produces a memoised getter for the
 * filtered [RowModel].
 *
 * When there are no rows, no column filters and no global filter, every
 * row's filter state is cleared and the pre-filtered model is returned as
 * is.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> getFilteredRowModel(): (table: Table<TData>) -> () -> RowModel<TData> {
    return factory@{ table ->
        val memoized: () -> RowModel<TData> = memo<RowModel<TData>>(
            getDeps = {
                listOf(
                    table.getPreFilteredRowModel(),
                    table.getState().columnFilters,
                    table.getState().globalFilter,
                )
            },
            fn = fn@{ deps ->
                @Suppress("UNCHECKED_CAST")
                val rowModel = deps[0] as RowModel<TData>

                @Suppress("UNCHECKED_CAST")
                val columnFilters = deps[1] as ColumnFiltersState?

                val globalFilter = deps[2]

                // Fast path: nothing to filter, so wipe per-row filter state
                // and return the pre-filtered model.
                if (
                    rowModel.rows.isEmpty() ||
                    ((columnFilters == null || columnFilters.isEmpty()) && !isTruthy(globalFilter))
                ) {
                    for (i in rowModel.flatRows.indices) {
                        rowModel.flatRows[i].columnFilters = mutableMapOf()
                        rowModel.flatRows[i].columnFiltersMeta = mutableMapOf()
                    }
                    return@fn rowModel
                }

                val resolvedColumnFilters = mutableListOf<ResolvedColumnFilter<TData>>()
                val resolvedGlobalFilters = mutableListOf<ResolvedColumnFilter<TData>>()

                (columnFilters ?: emptyList()).forEach { d ->
                    val column = table.getColumn(d.id)

                    if (column == null) {
                        return@forEach
                    }

                    val filterFn = column.getFilterFn()

                    if (filterFn == null) {
                        // The original gates this warning behind
                        // `process.env.NODE_ENV !== 'production'`; commonMain
                        // has no equivalent, so it is emitted unconditionally.
                        println(
                            "Could not find a valid 'column.filterFn' for column with the ID: ${column.id}.",
                        )
                        return@forEach
                    }

                    resolvedColumnFilters.add(
                        ResolvedColumnFilter(
                            id = d.id,
                            filterFn = filterFn,
                            resolvedValue = filterFn.resolveFilterValue?.invoke(d.value, null) ?: d.value,
                        ),
                    )
                }

                // Mutable so the global filter id can be appended below.
                val filterableIds = (columnFilters ?: emptyList()).map { d -> d.id }.toMutableList()

                val globalFilterFn = table.getGlobalFilterFn()

                val globallyFilterableColumns = table
                    .getAllLeafColumns()
                    .filter { column -> column.getCanGlobalFilter() }

                if (
                    isTruthy(globalFilter) &&
                    globalFilterFn != null &&
                    globallyFilterableColumns.isNotEmpty()
                ) {
                    filterableIds.add("__global__")

                    globallyFilterableColumns.forEach { column ->
                        resolvedGlobalFilters.add(
                            ResolvedColumnFilter(
                                id = column.id,
                                filterFn = globalFilterFn,
                                resolvedValue = globalFilterFn.resolveFilterValue
                                    ?.invoke(globalFilter, null) ?: globalFilter,
                            ),
                        )
                    }
                }

                var currentColumnFilter: ResolvedColumnFilter<TData>?
                var currentGlobalFilter: ResolvedColumnFilter<TData>?

                // Flag the prefiltered row model with each filter state.
                for (j in rowModel.flatRows.indices) {
                    val row = rowModel.flatRows[j]

                    row.columnFilters = mutableMapOf()

                    if (resolvedColumnFilters.isNotEmpty()) {
                        for (i in resolvedColumnFilters.indices) {
                            currentColumnFilter = resolvedColumnFilters[i]
                            val id = currentColumnFilter.id

                            // Tag the row with the column-filter state.
                            row.columnFilters[id] = currentColumnFilter.filterFn(
                                row,
                                id,
                                currentColumnFilter.resolvedValue,
                            ) { filterMeta ->
                                row.columnFiltersMeta[id] = filterMeta
                            }
                        }
                    }

                    if (resolvedGlobalFilters.isNotEmpty()) {
                        for (i in resolvedGlobalFilters.indices) {
                            currentGlobalFilter = resolvedGlobalFilters[i]
                            val id = currentGlobalFilter.id
                            // Tag the row with the first truthy global-filter state.
                            if (
                                currentGlobalFilter.filterFn(
                                    row,
                                    id,
                                    currentGlobalFilter.resolvedValue,
                                ) { filterMeta ->
                                    row.columnFiltersMeta[id] = filterMeta
                                }
                            ) {
                                row.columnFilters["__global__"] = true
                                break
                            }
                        }

                        if (row.columnFilters["__global__"] != true) {
                            row.columnFilters["__global__"] = false
                        }
                    }
                }

                // Predicate handed to `filterRows`. An explicit `false` for
                // any active filter id rejects the row; an absent id is
                // treated as a pass.
                val filterRowsImpl = filterRowsImpl@{ row: Row<TData> ->
                    // Horizontally filter rows through each column.
                    for (i in filterableIds.indices) {
                        if (row.columnFilters[filterableIds[i]] == false) {
                            return@filterRowsImpl false
                        }
                    }
                    true
                }

                // Filter final rows using all of the active filters.
                filterRows(rowModel.rows, filterRowsImpl, table)
            },
            opts = getMemoOptions(
                table.options,
                "debugTable",
                "getFilteredRowModel",
            ) { table._autoResetPageIndex() },
        )
                return@factory { memoized() }
    }
}
