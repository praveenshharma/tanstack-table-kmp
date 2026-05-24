package io.github.tanstacktable.core

/*
 * Column-grouping feature. Tracks an ordered list of grouped column ids
 * and exposes the column-level grouping accessors plus the table-level
 * `setGrouping` / `resetGrouping` / `getGroupedRowModel`.
 */

/**
 * Ordered list of grouped column ids — primary grouping first.
 */
typealias GroupingState = List<String>

/**
 * Aggregation function for a grouped column. Receives the column id, the
 * leaf rows of the group and the immediate child rows (or sub-aggregations
 * when grouping deeper than one level), and returns the aggregated value.
 */
typealias AggregationFn<TData> = (columnId: String, leafRows: List<Row<TData>>, childRows: List<Row<TData>>) -> Any?

/**
 * Caller-registered aggregation functions, keyed by name.
 */
typealias CustomAggregationFns = Map<String, AggregationFn<Any?>>

/**
 * Choice of aggregation function. Either `"auto"`, the name of a built-in
 * or registered fn (a [String]), or an [AggregationFn] value. Typed as
 * `Any?` because Kotlin cannot model that mixed union.
 */
typealias AggregationFnOption<TData> = Any?

/**
 * Where the grouped column appears in the table: `"reorder"`, `"remove"`,
 * or `null`. Typed as `Any?` for the same reason as [AggregationFnOption].
 */
typealias GroupingColumnMode = Any?

/**
 * The `ColumnGrouping` feature. Adds `grouping` state, the column-level
 * grouping accessors (`toggleGrouping`, `getIsGrouped`, ...) and the
 * table-level `setGrouping` / `resetGrouping` / `getGroupedRowModel`.
 */
@Suppress("UNCHECKED_CAST")
object ColumnGrouping : TableFeature {

    override val getDefaultColumnDef: (() -> ColumnDef<Any?, Any?>) = {
        ColumnDef<Any?, Any?>().also {
            it.aggregatedCell = { props: CellContext<Any?, Any?> ->
                props.getValue()?.toString() ?: null
            }
            it.aggregationFn = "auto"
        }
    }

    override val getInitialState: ((initialState: InitialTableState?) -> TableState) = { state ->
        TableState.fromInitialState(
            state,
            grouping = state?.grouping ?: emptyList(),
        )
    }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>) = { table ->
        TableOptionsResolved<Any?>().also {
            it.onGroupingChange = makeStateUpdater("grouping", table)
            it.groupedColumnMode = "reorder"
        }
    }

    override val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit) = { column, table ->
        column.toggleGrouping = {
            table.setGrouping { oldAny: Any? ->
                val old = oldAny as GroupingState?

                // Find any existing grouping for this column
                if (old?.contains(column.id) == true) {
                    old.filter { d -> d != column.id }
                } else {
                    (old ?: emptyList()) + column.id
                }
            }
        }

        column.getCanGroup = {
            (column.columnDef.enableGrouping ?: true) &&
                (table.options.enableGrouping ?: true) &&
                (column.accessorFn != null || column.columnDef.getGroupingValue != null)
        }

        column.getIsGrouped = {
            table.getState().grouping?.contains(column.id) ?: false
        }

        column.getGroupedIndex = {
            table.getState().grouping?.indexOf(column.id) ?: -1
        }

        column.getToggleGroupingHandler = {
            val canGroup = column.getCanGroup()

            val handler: () -> Unit = handler@{
                if (!canGroup) return@handler
                column.toggleGrouping?.invoke()
            }
            handler
        }
        column.getAutoAggregationFn = fn@{
            val firstRow = table.getCoreRowModel().flatRows.getOrNull(0)

            val value = firstRow?.getValue(column.id)

            if (value is Number) {
                return@fn aggregationFns.getValue("sum")
            }

            @Suppress("KotlinConstantConditions")
            if (false) {
                return@fn aggregationFns.getValue("extent")
            }

            null
        }
        column.getAggregationFn = {
            @Suppress("SENSELESS_COMPARISON")
            if (column == null) {
                throw RuntimeException()
            }

            if (isFunction(column.columnDef.aggregationFn)) {
                column.columnDef.aggregationFn as AggregationFn<Any?>?
            } else if (column.columnDef.aggregationFn == "auto") {
                column.getAutoAggregationFn()
            } else {
                table.options.aggregationFns?.get(column.columnDef.aggregationFn as String)
                    ?: aggregationFns.getValue(column.columnDef.aggregationFn as String)
            }
        }
    }

    override val createTable: ((table: Table<Any?>) -> Unit) = { table ->
        table.setGrouping = { updater -> table.options.onGroupingChange?.invoke(updater) }

        table.resetGrouping = { defaultState: Boolean? ->
            table.setGrouping(if (defaultState == true) emptyList<String>() else table.initialState.grouping)
        }

        table.getPreGroupedRowModel = { table.getFilteredRowModel() }
        table.getGroupedRowModel = {
            if (table._getGroupedRowModel == null && table.options.getGroupedRowModel != null) {
                table._getGroupedRowModel = table.options.getGroupedRowModel!!.invoke(table)
            }

            if (isTruthy(table.options.manualGrouping) || table._getGroupedRowModel == null) {
                table.getPreGroupedRowModel()
            } else {
                table._getGroupedRowModel!!.invoke()
            }
        }
    }

    override val createRow: ((row: Row<Any?>, table: Table<Any?>) -> Unit) = { row, table ->
        row.getIsGrouped = { isTruthy(row.groupingColumnId) }
        row.getGroupingValue = { columnId: String ->
            if (row._groupingValuesCache.containsKey(columnId)) {
                row._groupingValuesCache[columnId]
            } else {
                val column = table.getColumn(columnId)

                if (column?.columnDef?.getGroupingValue == null) {
                    row.getValue(columnId)
                } else {
                    row._groupingValuesCache[columnId] =
                        column.columnDef.getGroupingValue!!.invoke(row.original)

                    row._groupingValuesCache[columnId]
                }
            }
        }
        row._groupingValuesCache = mutableMapOf()
    }

    override val createCell: ((cell: Cell<Any?, Any?>, column: Column<Any?, Any?>, row: Row<Any?>, table: Table<Any?>) -> Unit) =
        { cell, column, row, table ->
            @Suppress("UNUSED_VARIABLE")
            val getRenderValue = { cell.getValue() ?: table.options.renderFallbackValue }

            cell.getIsGrouped = {
                column.getIsGrouped() && column.id == row.groupingColumnId
            }
            cell.getIsPlaceholder = { !cell.getIsGrouped() && column.getIsGrouped() }
            cell.getIsAggregated = {
                !cell.getIsGrouped() && !cell.getIsPlaceholder() && row.subRows.isNotEmpty()
            }
        }
}

fun <TData> orderColumns(
    leafColumns: List<Column<TData, Any?>>,
    grouping: List<String>,
    groupedColumnMode: GroupingColumnMode = null,
): List<Column<TData, Any?>> {
    if (grouping.isEmpty() || !isTruthy(groupedColumnMode)) {
        return leafColumns
    }

    val nonGroupingColumns = leafColumns.filter { col -> !grouping.contains(col.id) }

    if (groupedColumnMode == "remove") {
        return nonGroupingColumns
    }

    val groupingColumns = grouping
        .map { g -> leafColumns.find { col -> col.id == g } }
        .filterNotNull()

    return groupingColumns + nonGroupingColumns
}
