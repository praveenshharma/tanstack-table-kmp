package io.github.tanstacktable.core

private val builtInFeatures: List<TableFeature> = listOf(
    Headers,
    ColumnVisibility,
    ColumnOrdering,
    ColumnPinning,
    ColumnFaceting,
    ColumnFiltering,
    GlobalFaceting, // depends on ColumnFaceting
    GlobalFiltering, // depends on ColumnFiltering
    RowSorting,
    ColumnGrouping, // depends on RowSorting
    RowExpanding,
    RowPagination,
    RowPinning,
    RowSelection,
    ColumnSizing,
)

@Suppress("UNCHECKED_CAST")
fun <TData> createTable(options: TableOptionsResolved<TData>): Table<TData> {
    if (isTruthy(options.debugAll) || isTruthy(options.debugTable)) {
        println("Creating Table Instance...")
    }

    val features = builtInFeatures + (options._features ?: emptyList())

    val table = Table<TData>()
    table._features = features

    var defaultOptions: TableOptionsResolved<TData> = copyTableOptions(options)
    for (feature in features) {
        val fragment =
            feature.getDefaultOptions?.invoke(table as Table<Any?>) as TableOptionsResolved<TData>?
        defaultOptions = mergeTableOptions(defaultOptions, fragment)
    }

    val mergeOptions: (TableOptionsResolved<TData>) -> TableOptionsResolved<TData> =
        mergeOptionsArg@{ mergeOptionsArg ->
            if (table.options.mergeOptions != null) {
                return@mergeOptionsArg table.options.mergeOptions!!(defaultOptions, mergeOptionsArg)
            }

            mergeTableOptions(defaultOptions, mergeOptionsArg)
        }

    var initialState: TableState = options.initialState.toTableState()

    features.forEach { feature ->
        val asInitial = InitialTableState(
            columnVisibility = initialState.columnVisibility,
            columnOrder = initialState.columnOrder,
            columnPinning = initialState.columnPinning,
            rowPinning = initialState.rowPinning,
            columnFilters = initialState.columnFilters,
            globalFilter = initialState.globalFilter,
            sorting = initialState.sorting,
            expanded = initialState.expanded,
            grouping = initialState.grouping,
            columnSizing = initialState.columnSizing,
            columnSizingInfo = initialState.columnSizingInfo,
            pagination = initialState.pagination,
            rowSelection = initialState.rowSelection,
        )
        initialState = feature.getInitialState?.invoke(asInitial) ?: initialState
    }

    val queued = mutableListOf<() -> Unit>()
    var queuedTimeout = false

    // `_features` -- the source's `coreInstance` repeats it; already assigned
    // above, the value is identical.
    table._features = features

    table.options = mergeTableOptions(defaultOptions, options)

    table.initialState = initialState

    table._queue = { cb ->
        queued.add(cb)

        if (!queuedTimeout) {
            queuedTimeout = true

            // Schedule a microtask to run the queued callbacks after
            // the current call stack (render, etc) has finished.
            //

            while (queued.isNotEmpty()) {
                queued.removeAt(0).invoke()
            }
            queuedTimeout = false
        }
    }

    table.reset = {
        table.setState(table.initialState)
    }

    table.setOptions = { updater ->
        val newOptions = functionalUpdate<TableOptionsResolved<TData>>(updater, table.options)
        table.options = mergeOptions(newOptions)
    }

    table.getState = {
        table.options.state
    }

    table.setState = { updater ->
        table.options.onStateChange?.invoke(updater)
    }

    table._getRowId = { row, index, parent ->
        table.options.getRowId?.invoke(row, index, parent)
            ?: (if (parent != null) listOf(parent.id, index).joinToString(".") else "$index")
    }

    table.getCoreRowModel = {
        if (table._getCoreRowModel == null) {
            table._getCoreRowModel = table.options.getCoreRowModel(table as Table<Any?>) as () -> RowModel<TData>
        }

        table._getCoreRowModel!!.invoke()
    }

    // The final calls start at the bottom of the model,
    // expanded rows, which then work their way up

    table.getRowModel = {
        table.getPaginationRowModel()
    }

    // in next version, we should just pass in the row model as the optional 2nd arg
    table.getRow = getRow@{ id, searchAll ->
        var row: Row<TData>? =
            (
                if (isTruthy(searchAll)) {
                    table.getPrePaginationRowModel()
                } else {
                    table.getRowModel()
                }
                ).rowsById[id]

        if (row == null) {
            row = table.getCoreRowModel().rowsById[id]
            if (row == null) {
                throw IllegalStateException("getRow could not find row with ID: $id")
            }
        }

        row!!
    }

    table._getDefaultColumnDef = memo(
        getDeps = { listOf(table.options.defaultColumn) },
        fn = { deps ->
            val defaultColumn = (deps[0] as ColumnDef<TData, Any?>?) ?: ColumnDef()

            val result = ColumnDef<TData, Any?>()

            result.header = headerFn@{ props: HeaderContext<TData, Any?> ->
                val resolvedColumnDef = props.header.column.columnDef
                if (isTruthy(resolvedColumnDef.accessorKey)) {
                    return@headerFn resolvedColumnDef.accessorKey
                }
                if (isTruthy(resolvedColumnDef.accessorFn)) {
                    return@headerFn resolvedColumnDef.id
                }
                return@headerFn null
            }
            result.cell = { props: CellContext<TData, Any?> ->
                props.renderValue()?.toString() ?: null
            }

            for (feature in features) {
                val fragment = feature.getDefaultColumnDef?.invoke() as ColumnDef<TData, Any?>?
                if (fragment != null) {
                    if (fragment.getUniqueValues != null) result.getUniqueValues = fragment.getUniqueValues
                    if (fragment.footer != null) result.footer = fragment.footer
                    if (fragment.cell != null) result.cell = fragment.cell
                    if (fragment.meta != null) result.meta = fragment.meta
                    if (fragment.id != null) result.id = fragment.id
                    if (fragment.header != null) result.header = fragment.header
                    if (fragment.columns != null) result.columns = fragment.columns
                    if (fragment.accessorFn != null) result.accessorFn = fragment.accessorFn
                    if (fragment.accessorKey != null) result.accessorKey = fragment.accessorKey
                    if (fragment.enableHiding != null) result.enableHiding = fragment.enableHiding
                    if (fragment.enablePinning != null) result.enablePinning = fragment.enablePinning
                    if (fragment.enableColumnFilter != null) result.enableColumnFilter = fragment.enableColumnFilter
                    if (fragment.filterFn != null) result.filterFn = fragment.filterFn
                    if (fragment.enableGlobalFilter != null) result.enableGlobalFilter = fragment.enableGlobalFilter
                    if (fragment.enableMultiSort != null) result.enableMultiSort = fragment.enableMultiSort
                    if (fragment.enableSorting != null) result.enableSorting = fragment.enableSorting
                    if (fragment.invertSorting != null) result.invertSorting = fragment.invertSorting
                    if (fragment.sortDescFirst != null) result.sortDescFirst = fragment.sortDescFirst
                    if (fragment.sortingFn != null) result.sortingFn = fragment.sortingFn
                    if (fragment.sortUndefined != null) result.sortUndefined = fragment.sortUndefined
                    if (fragment.aggregatedCell != null) result.aggregatedCell = fragment.aggregatedCell
                    if (fragment.aggregationFn != null) result.aggregationFn = fragment.aggregationFn
                    if (fragment.enableGrouping != null) result.enableGrouping = fragment.enableGrouping
                    if (fragment.getGroupingValue != null) result.getGroupingValue = fragment.getGroupingValue
                    if (fragment.enableResizing != null) result.enableResizing = fragment.enableResizing
                    if (fragment.maxSize != null) result.maxSize = fragment.maxSize
                    if (fragment.minSize != null) result.minSize = fragment.minSize
                    if (fragment.size != null) result.size = fragment.size
                }
            }

            if (defaultColumn.getUniqueValues != null) result.getUniqueValues = defaultColumn.getUniqueValues
            if (defaultColumn.footer != null) result.footer = defaultColumn.footer
            if (defaultColumn.cell != null) result.cell = defaultColumn.cell
            if (defaultColumn.meta != null) result.meta = defaultColumn.meta
            if (defaultColumn.id != null) result.id = defaultColumn.id
            if (defaultColumn.header != null) result.header = defaultColumn.header
            if (defaultColumn.columns != null) result.columns = defaultColumn.columns
            if (defaultColumn.accessorFn != null) result.accessorFn = defaultColumn.accessorFn
            if (defaultColumn.accessorKey != null) result.accessorKey = defaultColumn.accessorKey
            if (defaultColumn.enableHiding != null) result.enableHiding = defaultColumn.enableHiding
            if (defaultColumn.enablePinning != null) result.enablePinning = defaultColumn.enablePinning
            if (defaultColumn.enableColumnFilter != null) result.enableColumnFilter = defaultColumn.enableColumnFilter
            if (defaultColumn.filterFn != null) result.filterFn = defaultColumn.filterFn
            if (defaultColumn.enableGlobalFilter != null) result.enableGlobalFilter = defaultColumn.enableGlobalFilter
            if (defaultColumn.enableMultiSort != null) result.enableMultiSort = defaultColumn.enableMultiSort
            if (defaultColumn.enableSorting != null) result.enableSorting = defaultColumn.enableSorting
            if (defaultColumn.invertSorting != null) result.invertSorting = defaultColumn.invertSorting
            if (defaultColumn.sortDescFirst != null) result.sortDescFirst = defaultColumn.sortDescFirst
            if (defaultColumn.sortingFn != null) result.sortingFn = defaultColumn.sortingFn
            if (defaultColumn.sortUndefined != null) result.sortUndefined = defaultColumn.sortUndefined
            if (defaultColumn.aggregatedCell != null) result.aggregatedCell = defaultColumn.aggregatedCell
            if (defaultColumn.aggregationFn != null) result.aggregationFn = defaultColumn.aggregationFn
            if (defaultColumn.enableGrouping != null) result.enableGrouping = defaultColumn.enableGrouping
            if (defaultColumn.getGroupingValue != null) result.getGroupingValue = defaultColumn.getGroupingValue
            if (defaultColumn.enableResizing != null) result.enableResizing = defaultColumn.enableResizing
            if (defaultColumn.maxSize != null) result.maxSize = defaultColumn.maxSize
            if (defaultColumn.minSize != null) result.minSize = defaultColumn.minSize
            if (defaultColumn.size != null) result.size = defaultColumn.size

            result
        },
        opts = getMemoOptions(options, "debugColumns", "_getDefaultColumnDef"),
    )

    table._getColumnDefs = {
        table.options.columns
    }

    table.getAllColumns = memo(
        getDeps = { listOf(table._getColumnDefs()) },
        fn = { deps ->
            val columnDefs = deps[0] as List<ColumnDef<TData, Any?>>

            fun recurseColumns(
                columnDefsArg: List<ColumnDef<TData, Any?>>,
                parent: Column<TData, Any?>? = null,
                depth: Int = 0,
            ): List<Column<TData, Any?>> {
                return columnDefsArg.map { columnDef ->
                    val column = createColumn(table, columnDef, depth, parent)

                    val groupingColumnDef = columnDef

                    column.columns =
                        if (groupingColumnDef.columns != null) {
                            recurseColumns(groupingColumnDef.columns!!, column, depth + 1)
                        } else {
                            emptyList()
                        }

                    column
                }
            }

            recurseColumns(columnDefs)
        },
        opts = getMemoOptions(options, "debugColumns", "getAllColumns"),
    )

    table.getAllFlatColumns = memo(
        getDeps = { listOf(table.getAllColumns()) },
        fn = { deps ->
            val allColumns = deps[0] as List<Column<TData, Any?>>
            allColumns.flatMap { column -> column.getFlatColumns() }
        },
        opts = getMemoOptions(options, "debugColumns", "getAllFlatColumns"),
    )

    table._getAllFlatColumnsById = memo(
        getDeps = { listOf(table.getAllFlatColumns()) },
        fn = { deps ->
            val flatColumns = deps[0] as List<Column<TData, Any?>>
            flatColumns.fold(mutableMapOf<String, Column<TData, Any?>>()) { acc, column ->
                acc[column.id] = column
                acc
            }
        },
        opts = getMemoOptions(options, "debugColumns", "getAllFlatColumnsById"),
    )

    table.getAllLeafColumns = memo(
        getDeps = { listOf(table.getAllColumns(), table._getOrderColumnsFn()) },
        fn = { deps ->
            val allColumns = deps[0] as List<Column<TData, Any?>>
            val orderColumns =
                deps[1] as (List<Column<TData, Any?>>) -> List<Column<TData, Any?>>
            var leafColumns = allColumns.flatMap { column -> column.getLeafColumns() }
            orderColumns(leafColumns)
        },
        opts = getMemoOptions(options, "debugColumns", "getAllLeafColumns"),
    )

    table.getColumn = { columnId ->
        val column = table._getAllFlatColumnsById()[columnId]

        if (column == null) {
            println("[Table] Column with id '$columnId' does not exist.")
        }

        column
    }

    // `Object.assign(table, coreInstance)` is realised above by assigning each
    // member directly onto `table`.

    for (index in table._features.indices) {
        val feature = table._features[index]
        feature.createTable?.invoke(table as Table<Any?>)
    }

    return table
}
