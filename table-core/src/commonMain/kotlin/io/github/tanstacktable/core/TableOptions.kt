package io.github.tanstacktable.core

/*
 * The `TableOptionsResolved` and `TableOptions` types.
 *
 * Collects every option that any feature exposes into one class. Members
 * a caller does not need to supply are nullable with a `null` default;
 * the four members the core reads un-guarded (`columns`, `data`,
 * `getCoreRowModel`, `state`) carry non-null types with placeholder
 * defaults that are always replaced by the caller's real values during
 * `createTable`.
 *
 * Options are merged by `Object.assign`-style overlay: feature
 * `getDefaultOptions` fragments are layered onto an empty seed, then the
 * caller's options overlay both. The helpers [TableOptionsResolved.empty]
 * and [TableOptionsResolved.merge] reproduce that pattern in a typed form.
 */

class TableOptionsResolved<TData>(

    // region CoreOptions
    var _features: List<TableFeature>? = null,
    var autoResetAll: Boolean? = null,
    var columns: List<ColumnDef<TData, Any?>> = emptyList(),
    var data: List<TData> = emptyList(),
    var debugAll: Boolean? = null,
    var debugCells: Boolean? = null,
    var debugColumns: Boolean? = null,
    var debugHeaders: Boolean? = null,
    var debugRows: Boolean? = null,
    var debugTable: Boolean? = null,
    var defaultColumn: ColumnDef<TData, Any?>? = null,
    var getCoreRowModel: (table: Table<Any?>) -> () -> RowModel<Any?> =
        { throw IllegalStateException("getCoreRowModel option was not provided") },
    var getRowId: ((originalRow: TData, index: Int, parent: Row<TData>?) -> String)? = null,
    var getSubRows: ((originalRow: TData, index: Int) -> List<TData>?)? = null,
    var initialState: InitialTableState? = null,
    var mergeOptions: ((defaultOptions: TableOptionsResolved<TData>, options: TableOptionsResolved<TData>) -> TableOptionsResolved<TData>)? = null,
    var meta: TableMeta<TData>? = null,
    var onStateChange: OnChangeFn? = null,
    var renderFallbackValue: Any? = null,
    var state: TableState = TableState(),
    // endregion

    // region VisibilityOptions (ColumnVisibility feature)
    var enableHiding: Boolean? = null,
    var onColumnVisibilityChange: OnChangeFn? = null,
    // endregion

    // region ColumnOrderOptions (ColumnOrdering feature)
    var onColumnOrderChange: OnChangeFn? = null,
    // endregion

    // region ColumnPinningOptions (ColumnPinning feature)
    var enableColumnPinning: Boolean? = null,
    var enablePinning: Boolean? = null,
    var onColumnPinningChange: OnChangeFn? = null,
    // endregion

    // region RowPinningOptions (RowPinning feature)
    /** Either a boolean or `(row) -> Boolean`. */
    var enableRowPinning: Any? = null,
    var keepPinnedRows: Boolean? = null,
    var onRowPinningChange: OnChangeFn? = null,
    // endregion

    // region FacetedOptions (ColumnFaceting feature)
    var getFacetedMinMaxValues: ((table: Table<TData>, columnId: String) -> () -> Pair<Double, Double>?)? = null,
    var getFacetedRowModel: ((table: Table<TData>, columnId: String) -> () -> RowModel<TData>)? = null,
    var getFacetedUniqueValues: ((table: Table<TData>, columnId: String) -> () -> Map<Any?, Int>)? = null,
    // endregion

    // region ColumnFiltersOptions (ColumnFiltering feature)
    var enableColumnFilters: Boolean? = null,
    var enableFilters: Boolean? = null,
    var filterFromLeafRows: Boolean? = null,
    var getFilteredRowModel: ((table: Table<Any?>) -> () -> RowModel<Any?>)? = null,
    var manualFiltering: Boolean? = null,
    var maxLeafRowFilterDepth: Int? = null,
    var onColumnFiltersChange: OnChangeFn? = null,
    /** Caller-registered filter functions. Looked up by name from `filterFn`. */
    var filterFns: Map<String, FilterFn<Any?>>? = null,
    // endregion

    // region GlobalFilterOptions (GlobalFiltering feature)
    var enableGlobalFilter: Boolean? = null,
    var getColumnCanGlobalFilter: ((column: Column<TData, Any?>) -> Boolean)? = null,
    var globalFilterFn: FilterFnOption<TData>? = null,
    var onGlobalFilterChange: OnChangeFn? = null,
    // endregion

    // region SortingOptions (RowSorting feature)
    var enableMultiRemove: Boolean? = null,
    var enableMultiSort: Boolean? = null,
    var enableSorting: Boolean? = null,
    var enableSortingRemoval: Boolean? = null,
    var getSortedRowModel: ((table: Table<Any?>) -> () -> RowModel<Any?>)? = null,
    var isMultiSortEvent: ((e: Any?) -> Boolean)? = null,
    var manualSorting: Boolean? = null,
    var maxMultiSortColCount: Int? = null,
    var onSortingChange: OnChangeFn? = null,
    var sortDescFirst: Boolean? = null,
    /** Caller-registered sort functions. Looked up by name from `sortingFn`. */
    var sortingFns: Map<String, SortingFn<Any?>>? = null,
    // endregion

    // region GroupingOptions (ColumnGrouping feature)
    var enableGrouping: Boolean? = null,
    var getGroupedRowModel: ((table: Table<Any?>) -> () -> RowModel<Any?>)? = null,
    /** Where the grouped column appears: `"reorder"`, `"remove"` or `null`. */
    var groupedColumnMode: Any? = null,
    var manualGrouping: Boolean? = null,
    var onGroupingChange: OnChangeFn? = null,
    /** Caller-registered aggregation functions. Looked up by name from `aggregationFn`. */
    var aggregationFns: Map<String, AggregationFn<Any?>>? = null,
    // endregion

    // region ExpandedOptions (RowExpanding feature)
    var autoResetExpanded: Boolean? = null,
    var enableExpanding: Boolean? = null,
    var getExpandedRowModel: ((table: Table<Any?>) -> () -> RowModel<Any?>)? = null,
    var getIsRowExpanded: ((row: Row<TData>) -> Boolean)? = null,
    var getRowCanExpand: ((row: Row<TData>) -> Boolean)? = null,
    var manualExpanding: Boolean? = null,
    var onExpandedChange: OnChangeFn? = null,
    var paginateExpandedRows: Boolean? = null,
    // endregion

    // region ColumnSizingOptions (ColumnSizing feature)
    var columnResizeMode: ColumnResizeMode? = null,
    var enableColumnResizing: Boolean? = null,
    var columnResizeDirection: ColumnResizeDirection? = null,
    var onColumnSizingChange: OnChangeFn? = null,
    var onColumnSizingInfoChange: OnChangeFn? = null,
    // endregion

    // region PaginationOptions (RowPagination feature)
    var autoResetPageIndex: Boolean? = null,
    var getPaginationRowModel: ((table: Table<Any?>) -> () -> RowModel<Any?>)? = null,
    var manualPagination: Boolean? = null,
    var onPaginationChange: OnChangeFn? = null,
    var pageCount: Int? = null,
    var rowCount: Int? = null,
    // endregion

    // region RowSelectionOptions (RowSelection feature)
    /** Either a boolean or `(row) -> Boolean`. */
    var enableMultiRowSelection: Any? = null,
    /** Either a boolean or `(row) -> Boolean`. */
    var enableRowSelection: Any? = null,
    /** Either a boolean or `(row) -> Boolean`. */
    var enableSubRowSelection: Any? = null,
    var onRowSelectionChange: OnChangeFn? = null,
    // endregion
) {
    companion object {
        /**
         * The empty seed used by `createTable` before feature
         * `getDefaultOptions` fragments are layered on. Every optional
         * member is `null`; the four required core members carry their
         * placeholder defaults.
         */
        fun <TData> empty(): TableOptionsResolved<TData> = TableOptionsResolved()

        /**
         * Returns the field-by-field merge `{ ...a, ...b }` — [b] wins.
         *
         * Nullable members fall back to [a] when [b] leaves them null
         * (matching the "present key" rule). The non-null core members and
         * `renderFallbackValue` are always taken from [b]; the final
         * `merge(defaults, userOptions)` is the only call that carries
         * their real values, so feature-fragment merges only see
         * placeholders on both sides anyway.
         *
         * Passing `null` for [b] returns [a] unchanged — a feature whose
         * `getDefaultOptions` is absent contributes no overlay.
         */
        fun <TData> merge(
            a: TableOptionsResolved<TData>,
            b: TableOptionsResolved<TData>?,
        ): TableOptionsResolved<TData> {
            if (b == null) return a
            return TableOptionsResolved(
                _features = b._features ?: a._features,
                autoResetAll = b.autoResetAll ?: a.autoResetAll,
                columns = b.columns,
                data = b.data,
                debugAll = b.debugAll ?: a.debugAll,
                debugCells = b.debugCells ?: a.debugCells,
                debugColumns = b.debugColumns ?: a.debugColumns,
                debugHeaders = b.debugHeaders ?: a.debugHeaders,
                debugRows = b.debugRows ?: a.debugRows,
                debugTable = b.debugTable ?: a.debugTable,
                defaultColumn = b.defaultColumn ?: a.defaultColumn,
                getCoreRowModel = b.getCoreRowModel,
                getRowId = b.getRowId ?: a.getRowId,
                getSubRows = b.getSubRows ?: a.getSubRows,
                initialState = b.initialState ?: a.initialState,
                mergeOptions = b.mergeOptions ?: a.mergeOptions,
                meta = b.meta ?: a.meta,
                onStateChange = b.onStateChange ?: a.onStateChange,
                renderFallbackValue = b.renderFallbackValue,
                state = b.state,
                enableHiding = b.enableHiding ?: a.enableHiding,
                onColumnVisibilityChange = b.onColumnVisibilityChange ?: a.onColumnVisibilityChange,
                onColumnOrderChange = b.onColumnOrderChange ?: a.onColumnOrderChange,
                enableColumnPinning = b.enableColumnPinning ?: a.enableColumnPinning,
                enablePinning = b.enablePinning ?: a.enablePinning,
                onColumnPinningChange = b.onColumnPinningChange ?: a.onColumnPinningChange,
                enableRowPinning = b.enableRowPinning ?: a.enableRowPinning,
                keepPinnedRows = b.keepPinnedRows ?: a.keepPinnedRows,
                onRowPinningChange = b.onRowPinningChange ?: a.onRowPinningChange,
                getFacetedMinMaxValues = b.getFacetedMinMaxValues ?: a.getFacetedMinMaxValues,
                getFacetedRowModel = b.getFacetedRowModel ?: a.getFacetedRowModel,
                getFacetedUniqueValues = b.getFacetedUniqueValues ?: a.getFacetedUniqueValues,
                enableColumnFilters = b.enableColumnFilters ?: a.enableColumnFilters,
                enableFilters = b.enableFilters ?: a.enableFilters,
                filterFromLeafRows = b.filterFromLeafRows ?: a.filterFromLeafRows,
                getFilteredRowModel = b.getFilteredRowModel ?: a.getFilteredRowModel,
                manualFiltering = b.manualFiltering ?: a.manualFiltering,
                maxLeafRowFilterDepth = b.maxLeafRowFilterDepth ?: a.maxLeafRowFilterDepth,
                onColumnFiltersChange = b.onColumnFiltersChange ?: a.onColumnFiltersChange,
                filterFns = b.filterFns ?: a.filterFns,
                enableGlobalFilter = b.enableGlobalFilter ?: a.enableGlobalFilter,
                getColumnCanGlobalFilter = b.getColumnCanGlobalFilter ?: a.getColumnCanGlobalFilter,
                globalFilterFn = b.globalFilterFn ?: a.globalFilterFn,
                onGlobalFilterChange = b.onGlobalFilterChange ?: a.onGlobalFilterChange,
                enableMultiRemove = b.enableMultiRemove ?: a.enableMultiRemove,
                enableMultiSort = b.enableMultiSort ?: a.enableMultiSort,
                enableSorting = b.enableSorting ?: a.enableSorting,
                enableSortingRemoval = b.enableSortingRemoval ?: a.enableSortingRemoval,
                getSortedRowModel = b.getSortedRowModel ?: a.getSortedRowModel,
                isMultiSortEvent = b.isMultiSortEvent ?: a.isMultiSortEvent,
                manualSorting = b.manualSorting ?: a.manualSorting,
                maxMultiSortColCount = b.maxMultiSortColCount ?: a.maxMultiSortColCount,
                onSortingChange = b.onSortingChange ?: a.onSortingChange,
                sortDescFirst = b.sortDescFirst ?: a.sortDescFirst,
                sortingFns = b.sortingFns ?: a.sortingFns,
                enableGrouping = b.enableGrouping ?: a.enableGrouping,
                getGroupedRowModel = b.getGroupedRowModel ?: a.getGroupedRowModel,
                groupedColumnMode = b.groupedColumnMode ?: a.groupedColumnMode,
                manualGrouping = b.manualGrouping ?: a.manualGrouping,
                onGroupingChange = b.onGroupingChange ?: a.onGroupingChange,
                aggregationFns = b.aggregationFns ?: a.aggregationFns,
                autoResetExpanded = b.autoResetExpanded ?: a.autoResetExpanded,
                enableExpanding = b.enableExpanding ?: a.enableExpanding,
                getExpandedRowModel = b.getExpandedRowModel ?: a.getExpandedRowModel,
                getIsRowExpanded = b.getIsRowExpanded ?: a.getIsRowExpanded,
                getRowCanExpand = b.getRowCanExpand ?: a.getRowCanExpand,
                manualExpanding = b.manualExpanding ?: a.manualExpanding,
                onExpandedChange = b.onExpandedChange ?: a.onExpandedChange,
                paginateExpandedRows = b.paginateExpandedRows ?: a.paginateExpandedRows,
                columnResizeMode = b.columnResizeMode ?: a.columnResizeMode,
                enableColumnResizing = b.enableColumnResizing ?: a.enableColumnResizing,
                columnResizeDirection = b.columnResizeDirection ?: a.columnResizeDirection,
                onColumnSizingChange = b.onColumnSizingChange ?: a.onColumnSizingChange,
                onColumnSizingInfoChange = b.onColumnSizingInfoChange ?: a.onColumnSizingInfoChange,
                autoResetPageIndex = b.autoResetPageIndex ?: a.autoResetPageIndex,
                getPaginationRowModel = b.getPaginationRowModel ?: a.getPaginationRowModel,
                manualPagination = b.manualPagination ?: a.manualPagination,
                onPaginationChange = b.onPaginationChange ?: a.onPaginationChange,
                pageCount = b.pageCount ?: a.pageCount,
                rowCount = b.rowCount ?: a.rowCount,
                enableMultiRowSelection = b.enableMultiRowSelection ?: a.enableMultiRowSelection,
                enableRowSelection = b.enableRowSelection ?: a.enableRowSelection,
                enableSubRowSelection = b.enableSubRowSelection ?: a.enableSubRowSelection,
                onRowSelectionChange = b.onRowSelectionChange ?: a.onRowSelectionChange,
            )
        }
    }
}

/**
 * Caller-facing alias for [TableOptionsResolved]. The two are
 * structurally identical because every field on [TableOptionsResolved] is
 * either nullable or carries a default, so a caller can construct one
 * supplying only the options they care about.
 */
typealias TableOptions<TData> = TableOptionsResolved<TData>

/**
 * Field-by-field shallow copy of [options]. Used by `createTable` to seed
 * its defaults accumulator with the caller's required-field values before
 * feature `getDefaultOptions` fragments are layered on.
 */
fun <TData> copyTableOptions(
    options: TableOptionsResolved<TData>,
): TableOptionsResolved<TData> = TableOptionsResolved.merge(TableOptionsResolved.empty(), options)

/**
 * Field-by-field merge of [a] and [b], with [b] winning. Delegates to
 * [TableOptionsResolved.merge] — see its KDoc for per-field semantics.
 */
fun <TData> mergeTableOptions(
    a: TableOptionsResolved<TData>,
    b: TableOptionsResolved<TData>?,
): TableOptionsResolved<TData> = TableOptionsResolved.merge(a, b)
