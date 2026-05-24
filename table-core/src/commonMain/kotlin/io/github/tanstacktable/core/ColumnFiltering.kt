package io.github.tanstacktable.core

/*
 * Column-filtering feature. Tracks per-column filter values, exposes the
 * column-level filter accessors and the table-level setters/resets.
 */

/**
 * Ordered list of active per-column filter entries.
 */
typealias ColumnFiltersState = List<ColumnFilter>

/**
 * One entry in [ColumnFiltersState]. [id] identifies the column; [value]
 * is the raw filter value submitted by the caller (later normalised by the
 * column's [FilterFn.resolveFilterValue]).
 */
class ColumnFilter(
    val id: String,
    val value: Any?,
)

/**
 * A column filter resolved against its column — carries the column id,
 * the resolved filter fn, and the post-[FilterFn.resolveFilterValue]
 * value. Used internally by the filtering pipeline.
 */
class ResolvedColumnFilter<TData>(
    val filterFn: FilterFn<TData>,
    val id: String,
    val resolvedValue: Any?,
)

/**
 * Callable filter function for a column. [invoke] returns `true` to keep
 * the row, `false` to filter it out, and may push per-row metadata via
 * `addMeta`. Implementations may override [autoRemove] and
 * [resolveFilterValue] when needed.
 */
interface FilterFn<TData> {
    operator fun invoke(
        row: Row<TData>,
        columnId: String,
        filterValue: Any?,
        addMeta: (meta: FilterMeta) -> Unit,
    ): Boolean

    /** Optional predicate that auto-removes the filter when its value becomes empty. */
    val autoRemove: ColumnFilterAutoRemoveTestFn<TData>?
        get() = null

    /** Optional pre-processor applied to the raw filter value. */
    val resolveFilterValue: TransformFilterValueFn<TData>?
        get() = null
}

/**
 * Normalises a raw filter value before the filter fn sees it.
 */
typealias TransformFilterValueFn<TData> = (value: Any?, column: Column<TData, Any?>?) -> Any?

/**
 * Returns `true` when a filter value should be auto-removed (typically
 * because it has become empty).
 */
typealias ColumnFilterAutoRemoveTestFn<TData> = (value: Any?, column: Column<TData, Any?>?) -> Boolean

/**
 * Caller-registered filter functions, keyed by name.
 */
typealias CustomFilterFns<TData> = Map<String, FilterFn<TData>>

/**
 * Choice of filter function. Either `"auto"`, the name of a built-in or
 * registered fn (a [String]), or a [FilterFn] value. Typed as `Any?`
 * because Kotlin cannot model that mixed union.
 */
typealias FilterFnOption<TData> = Any?

/**
 * The `ColumnFiltering` feature. Adds `columnFilters` state, the column-
 * level filter accessors (`getFilterFn`, `getIsFiltered`, ...) and the
 * table-level `setColumnFilters` / `resetColumnFilters` /
 * `getFilteredRowModel`.
 */
object ColumnFiltering : TableFeature {

    override val getDefaultColumnDef: (() -> ColumnDef<Any?, Any?>)?
        get() = {
            ColumnDef<Any?, Any?>().apply {
                filterFn = "auto"
            }
        }

    override val getInitialState: ((initialState: InitialTableState?) -> TableState)?
        get() = { state ->
            state.toTableState().copy(
                columnFilters = state?.columnFilters ?: emptyList()
            )
        }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>)?
        get() = { table ->
            TableOptionsResolved<Any?>().apply {
                onColumnFiltersChange = makeStateUpdater("columnFilters", table)
                filterFromLeafRows = false
                maxLeafRowFilterDepth = 100
            }
        }

    override val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = { column, table ->
            column.getAutoFilterFn = {
                val firstRow = table.getCoreRowModel().flatRows.getOrNull(0)

                val value = firstRow?.getValue(column.id)

                when {
                    value is String -> filterFns.includesString
                    value is Number -> filterFns.inNumberRange
                    value is Boolean -> filterFns.equals
                    value != null && (value !is Number && value !is String && value !is Boolean) -> filterFns.equals
                    value is List<*> -> filterFns.arrIncludes
                    else -> filterFns.weakEquals
                }
            }
            column.getFilterFn = {
                val cdFilterFn = column.columnDef.filterFn
                @Suppress("UNCHECKED_CAST")
                if (isFunction(cdFilterFn)) {
                    cdFilterFn as FilterFn<Any?>
                } else if (cdFilterFn == "auto") {
                    column.getAutoFilterFn()
                } else {
                    table.options.filterFns?.get(cdFilterFn as String)
                        ?: filterFns[cdFilterFn as String]
                }
            }
            column.getCanFilter = {
                (column.columnDef.enableColumnFilter ?: true) &&
                    (table.options.enableColumnFilters ?: true) &&
                    (table.options.enableFilters ?: true) &&
                    column.accessorFn != null
            }

            column.getIsFiltered = { column.getFilterIndex() > -1 }

            column.getFilterValue = {
                table.getState().columnFilters?.find { d -> d.id == column.id }?.value
            }

            column.getFilterIndex = {
                table.getState().columnFilters?.indexOfFirst { d -> d.id == column.id } ?: -1
            }

            column.setFilterValue = { value ->
                table.setColumnFilters { old: ColumnFiltersState? ->
                    val filterFn = column.getFilterFn()
                    val previousFilter = old?.find { d -> d.id == column.id }

                    val newFilter = functionalUpdate(
                        value,
                        if (previousFilter != null) previousFilter.value else null
                    )

                    //
                    if (
                        shouldAutoRemoveFilter(filterFn, newFilter, column)
                    ) {
                        old?.filter { d -> d.id != column.id } ?: emptyList()
                    } else {
                        val newFilterObj = ColumnFilter(id = column.id, value = newFilter)

                        if (previousFilter != null) {
                            old?.map { d ->
                                if (d.id == column.id) {
                                    newFilterObj
                                } else {
                                    d
                                }
                            } ?: emptyList()
                        } else if (isTruthy(old?.size)) {
                            old!! + newFilterObj
                        } else {
                            listOf(newFilterObj)
                        }
                    }
                }
            }
        }

    override val createRow: ((row: Row<Any?>, table: Table<Any?>) -> Unit)?
        get() = { row, _table ->
            row.columnFilters = mutableMapOf()
            row.columnFiltersMeta = mutableMapOf()
        }

    override val createTable: ((table: Table<Any?>) -> Unit)?
        get() = { table ->
            table.setColumnFilters = { updater ->
                val leafColumns = table.getAllLeafColumns()

                val updateFn = { old: ColumnFiltersState ->
                    @Suppress("UNCHECKED_CAST")
                    val updated = functionalUpdate<Any?>(updater, old) as ColumnFiltersState?
                    updated?.filter { filter ->
                        val column = leafColumns.find { d -> d.id == filter.id }

                        if (column != null) {
                            val filterFn = column.getFilterFn()

                            if (shouldAutoRemoveFilter(filterFn, filter.value, column)) {
                                return@filter false
                            }
                        }

                        true
                    }
                }

                table.options.onColumnFiltersChange?.invoke(updateFn)
            }

            table.resetColumnFilters = { defaultState ->
                table.setColumnFilters(
                    if (isTruthy(defaultState)) {
                        emptyList()
                    } else {
                        table.initialState?.columnFilters ?: emptyList()
                    }
                )
            }

            table.getPreFilteredRowModel = { table.getCoreRowModel() }
            table.getFilteredRowModel = {
                if (table._getFilteredRowModel == null && table.options.getFilteredRowModel != null) {
                    table._getFilteredRowModel = table.options.getFilteredRowModel!!(table)
                }

                if (isTruthy(table.options.manualFiltering) || table._getFilteredRowModel == null) {
                    table.getPreFilteredRowModel()
                } else {
                    table._getFilteredRowModel!!()
                }
            }
        }
}

/**
 * Returns `true` when a column filter should be auto-removed: when the
 * filter fn's own `autoRemove` test says so, when the value is `null`, or
 * when it is an empty string.
 */
fun <TData> shouldAutoRemoveFilter(
    filterFn: FilterFn<TData>? = null,
    value: Any? = null,
    column: Column<TData, Any?>? = null,
): Boolean {
    return (
        if (filterFn != null && filterFn.autoRemove != null) {
            filterFn.autoRemove!!(value, column)
        } else {
            false
        }
        ) ||
        value == null ||
        (value is String && value.isEmpty())
}
