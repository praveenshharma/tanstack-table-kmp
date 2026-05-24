package io.github.tanstacktable.core

/*
 * Global-filtering feature. Applies a single filter expression across every
 * filterable column rather than per-column.
 *
 * The state shape (`globalFilter`), column-def fields (`enableGlobalFilter`),
 * column extensions (`getCanGlobalFilter`), options (`globalFilterFn`,
 * `onGlobalFilterChange`, `getColumnCanGlobalFilter`) and table extensions
 * (`setGlobalFilter`, `resetGlobalFilter`, ...) are declared on the unified
 * `TableState` / `ColumnDef` / `Column` / `TableOptionsResolved` / `Table`
 * types and are wired up here.
 */

/**
 * The `GlobalFiltering` feature. Adds global-filter state, options and the
 * `Table`/`Column` accessors that drive it.
 */
object GlobalFiltering : TableFeature {

    override val getInitialState: ((initialState: InitialTableState?) -> TableState)?
        get() = { state ->
            // `globalFilter` defaults to null; an explicit value on the
            // initial state overrides it.
            state.toTableState().copy(
                globalFilter = state?.globalFilter
            )
        }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>)?
        get() = { table ->
            TableOptionsResolved<Any?>().apply {
                onGlobalFilterChange = makeStateUpdater("globalFilter", table)
                globalFilterFn = "auto"
                getColumnCanGlobalFilter = { column ->
                    // Probe the first flat row's cell for this column — if its
                    // value is a string or number, the column is globally
                    // filterable by default.
                    val value = table
                        .getCoreRowModel()
                        .flatRows.getOrNull(0)
                        ?._getAllCellsByColumnId()
                        ?.get(column.id)
                        ?.getValue()

                    value is String || value is Number
                }
            }
        }

    override val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = { column, table ->
            column.getCanGlobalFilter = {
                (column.columnDef.enableGlobalFilter ?: true) &&
                    (table.options.enableGlobalFilter ?: true) &&
                    (table.options.enableFilters ?: true) &&
                    (table.options.getColumnCanGlobalFilter?.invoke(column) ?: true) &&
                    column.accessorFn != null
            }
        }

    override val createTable: ((table: Table<Any?>) -> Unit)?
        get() = { table ->
            table.getGlobalAutoFilterFn = {
                filterFns.includesString
            }

            table.getGlobalFilterFn = {
                val globalFilterFn = table.options.globalFilterFn

                // Resolve `globalFilterFn`: a function passes through, `"auto"`
                // picks the auto filter, otherwise look up the name in the
                // user-provided `filterFns` map then in the built-ins.
                @Suppress("UNCHECKED_CAST")
                if (isFunction(globalFilterFn)) {
                    globalFilterFn as FilterFn<Any?>
                } else if (globalFilterFn == "auto") {
                    table.getGlobalAutoFilterFn()
                } else {
                    table.options.filterFns?.get(globalFilterFn as String)
                        ?: filterFns[globalFilterFn as String]
                }
            }

            table.setGlobalFilter = { updater ->
                table.options.onGlobalFilterChange?.invoke(updater)
            }

            table.resetGlobalFilter = { defaultState ->
                table.setGlobalFilter(
                    if (isTruthy(defaultState)) null else table.initialState.globalFilter
                )
            }
        }
}
