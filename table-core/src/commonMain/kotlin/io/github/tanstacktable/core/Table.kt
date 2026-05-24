package io.github.tanstacktable.core

/*
 * The `Table` object. A table combines a core part (`CoreInstance`) with
 * every feature's table extensions — visibility, ordering, pinning,
 * filtering, sorting, grouping, expanding, sizing, pagination and
 * selection. All members are authored on a single mutable class and
 * grouped by their originating concern via `// region` blocks.
 *
 * Function-valued members are `lateinit var` because the core `createTable`
 * and every feature's `createTable` hook assign them after construction.
 * Data members carry their initial value.
 *
 * Construction logic (`createTable` and the feature hooks) lives in
 * CoreTable.kt and the per-feature files.
 */

/**
 * The table instance — entry point to every accessor and setter. Build
 * one with `createTable(options)`; read state via `getState()` and the
 * `get*RowModel()` family; mutate via the typed setters
 * (`setSorting`, `setColumnFilters`, ...).
 */
class Table<TData> {

    // region CoreInstance
    lateinit var _features: List<TableFeature>

    lateinit var _getAllFlatColumnsById: () -> Map<String, Column<TData, Any?>>

    lateinit var _getColumnDefs: () -> List<ColumnDef<TData, Any?>>

        var _getCoreRowModel: (() -> RowModel<TData>)? = null

    lateinit var _getDefaultColumnDef: () -> ColumnDef<TData, Any?>

    lateinit var _getRowId: (row: TData, index: Int, parent: Row<TData>?) -> String

    lateinit var _queue: (cb: () -> Unit) -> Unit

    lateinit var getAllColumns: () -> List<Column<TData, Any?>>

    lateinit var getAllFlatColumns: () -> List<Column<TData, Any?>>

    lateinit var getAllLeafColumns: () -> List<Column<TData, Any?>>

    lateinit var getColumn: (columnId: String) -> Column<TData, Any?>?

    lateinit var getCoreRowModel: () -> RowModel<TData>

    lateinit var getRow: (id: String, searchAll: Boolean?) -> Row<TData>

    lateinit var getRowModel: () -> RowModel<TData>

    lateinit var getState: () -> TableState

    lateinit var initialState: TableState

    lateinit var options: TableOptionsResolved<TData>

    lateinit var reset: () -> Unit

    lateinit var setOptions: (newOptions: Updater) -> Unit

    lateinit var setState: (updater: Updater) -> Unit
    // endregion

    // region HeadersInstance (headers feature)
    lateinit var getHeaderGroups: () -> List<HeaderGroup<TData>>

    lateinit var getLeftHeaderGroups: () -> List<HeaderGroup<TData>>

    lateinit var getCenterHeaderGroups: () -> List<HeaderGroup<TData>>

    lateinit var getRightHeaderGroups: () -> List<HeaderGroup<TData>>

    lateinit var getFooterGroups: () -> List<HeaderGroup<TData>>

    lateinit var getLeftFooterGroups: () -> List<HeaderGroup<TData>>

    lateinit var getCenterFooterGroups: () -> List<HeaderGroup<TData>>

    lateinit var getRightFooterGroups: () -> List<HeaderGroup<TData>>

    lateinit var getFlatHeaders: () -> List<Header<TData, *>>

    lateinit var getLeftFlatHeaders: () -> List<Header<TData, *>>

    lateinit var getCenterFlatHeaders: () -> List<Header<TData, *>>

    lateinit var getRightFlatHeaders: () -> List<Header<TData, *>>

    lateinit var getLeafHeaders: () -> List<Header<TData, *>>

    lateinit var getLeftLeafHeaders: () -> List<Header<TData, *>>

    lateinit var getCenterLeafHeaders: () -> List<Header<TData, *>>

    lateinit var getRightLeafHeaders: () -> List<Header<TData, *>>
    // endregion

    // region VisibilityInstance (ColumnVisibility feature)
    lateinit var getCenterVisibleLeafColumns: () -> List<Column<TData, Any?>>

    lateinit var getIsAllColumnsVisible: () -> Boolean

    lateinit var getIsSomeColumnsVisible: () -> Boolean

    lateinit var getLeftVisibleLeafColumns: () -> List<Column<TData, Any?>>

    lateinit var getRightVisibleLeafColumns: () -> List<Column<TData, Any?>>

    lateinit var getToggleAllColumnsVisibilityHandler: () -> (event: Any?) -> Unit

    lateinit var getVisibleFlatColumns: () -> List<Column<TData, Any?>>

    lateinit var getVisibleLeafColumns: () -> List<Column<TData, Any?>>

    lateinit var resetColumnVisibility: (defaultState: Boolean?) -> Unit

    lateinit var setColumnVisibility: (updater: Updater) -> Unit

    lateinit var toggleAllColumnsVisible: (value: Boolean?) -> Unit
    // endregion

    // region ColumnOrderInstance (ColumnOrdering feature)
    lateinit var _getOrderColumnsFn: () -> (columns: List<Column<TData, Any?>>) -> List<Column<TData, Any?>>

    lateinit var resetColumnOrder: (defaultState: Boolean?) -> Unit

    lateinit var setColumnOrder: (updater: Updater) -> Unit
    // endregion

    // region ColumnPinningInstance (ColumnPinning feature)
    lateinit var getCenterLeafColumns: () -> List<Column<TData, Any?>>

    lateinit var getIsSomeColumnsPinned: (position: ColumnPinningPosition?) -> Boolean

    lateinit var getLeftLeafColumns: () -> List<Column<TData, Any?>>

    lateinit var getRightLeafColumns: () -> List<Column<TData, Any?>>

    lateinit var resetColumnPinning: (defaultState: Boolean?) -> Unit

    lateinit var setColumnPinning: (updater: Updater) -> Unit
    // endregion

    // region RowPinningInstance (RowPinning feature)
    lateinit var _getPinnedRows: (
        visiblePinnedRows: List<Row<TData>>,
        pinnedRowIds: List<String>?,
        position: String,
    ) -> List<Row<TData>>

    lateinit var getBottomRows: () -> List<Row<TData>>

    lateinit var getCenterRows: () -> List<Row<TData>>

    lateinit var getIsSomeRowsPinned: (position: RowPinningPosition?) -> Boolean

    lateinit var getTopRows: () -> List<Row<TData>>

    lateinit var resetRowPinning: (defaultState: Boolean?) -> Unit

    lateinit var setRowPinning: (updater: Updater) -> Unit
    // endregion

    // region ColumnFiltersInstance (ColumnFiltering feature)
        var _getFilteredRowModel: (() -> RowModel<TData>)? = null

    lateinit var getFilteredRowModel: () -> RowModel<TData>

    lateinit var getPreFilteredRowModel: () -> RowModel<TData>

    lateinit var resetColumnFilters: (defaultState: Boolean?) -> Unit

    lateinit var resetGlobalFilter: (defaultState: Boolean?) -> Unit

    lateinit var setColumnFilters: (updater: Updater) -> Unit

    lateinit var setGlobalFilter: (updater: Updater) -> Unit
    // endregion

    // region GlobalFilterInstance (GlobalFiltering feature)
    lateinit var getGlobalAutoFilterFn: () -> FilterFn<TData>?

    lateinit var getGlobalFilterFn: () -> FilterFn<TData>?

    // region GlobalFacetingInstance (GlobalFaceting feature)
        var _getGlobalFacetedMinMaxValues: (() -> Pair<Double, Double>?)? = null

    var _getGlobalFacetedRowModel: (() -> RowModel<TData>)? = null

        var _getGlobalFacetedUniqueValues: (() -> Map<Any?, Int>)? = null

    lateinit var getGlobalFacetedMinMaxValues: () -> Pair<Double, Double>?

    lateinit var getGlobalFacetedRowModel: () -> RowModel<TData>

    lateinit var getGlobalFacetedUniqueValues: () -> Map<Any?, Int>
    // endregion

    // region SortingInstance (RowSorting feature)
    var _getSortedRowModel: (() -> RowModel<TData>)? = null

    lateinit var getPreSortedRowModel: () -> RowModel<TData>

    lateinit var getSortedRowModel: () -> RowModel<TData>

    lateinit var resetSorting: (defaultState: Boolean?) -> Unit

    lateinit var setSorting: (updater: Updater) -> Unit
    // endregion

    // region GroupingInstance (ColumnGrouping feature)
    var _getGroupedRowModel: (() -> RowModel<TData>)? = null

    lateinit var getGroupedRowModel: () -> RowModel<TData>

    lateinit var getPreGroupedRowModel: () -> RowModel<TData>

    lateinit var resetGrouping: (defaultState: Boolean?) -> Unit

    lateinit var setGrouping: (updater: Updater) -> Unit
    // endregion

    // region ColumnSizingInstance (ColumnSizing feature)
    lateinit var getCenterTotalSize: () -> Double

    lateinit var getLeftTotalSize: () -> Double

    lateinit var getRightTotalSize: () -> Double

    lateinit var getTotalSize: () -> Double

    lateinit var resetColumnSizing: (defaultState: Boolean?) -> Unit

    lateinit var resetHeaderSizeInfo: (defaultState: Boolean?) -> Unit

    lateinit var setColumnSizing: (updater: Updater) -> Unit

    lateinit var setColumnSizingInfo: (updater: Updater) -> Unit
    // endregion

    // region ExpandedInstance (RowExpanding feature)
    lateinit var _autoResetExpanded: () -> Unit

    var _getExpandedRowModel: (() -> RowModel<TData>)? = null

    lateinit var getCanSomeRowsExpand: () -> Boolean

    lateinit var getExpandedDepth: () -> Int

    lateinit var getExpandedRowModel: () -> RowModel<TData>

    lateinit var getIsAllRowsExpanded: () -> Boolean

    lateinit var getIsSomeRowsExpanded: () -> Boolean

    lateinit var getPreExpandedRowModel: () -> RowModel<TData>

    lateinit var getToggleAllRowsExpandedHandler: () -> (event: Any?) -> Unit

    lateinit var resetExpanded: (defaultState: Boolean?) -> Unit

    lateinit var setExpanded: (updater: Updater) -> Unit

    lateinit var toggleAllRowsExpanded: (expanded: Boolean?) -> Unit
    // endregion

    // region PaginationInstance (RowPagination feature)
    lateinit var _autoResetPageIndex: () -> Unit

    var _getPaginationRowModel: (() -> RowModel<TData>)? = null

    lateinit var getCanNextPage: () -> Boolean

    lateinit var getCanPreviousPage: () -> Boolean

    lateinit var getPageCount: () -> Int

    lateinit var getRowCount: () -> Int

    lateinit var getPageOptions: () -> List<Int>

    lateinit var getPaginationRowModel: () -> RowModel<TData>

    lateinit var getPrePaginationRowModel: () -> RowModel<TData>

    lateinit var nextPage: () -> Unit

    lateinit var previousPage: () -> Unit

    lateinit var firstPage: () -> Unit

    lateinit var lastPage: () -> Unit

    lateinit var resetPageIndex: (defaultState: Boolean?) -> Unit

    lateinit var resetPageSize: (defaultState: Boolean?) -> Unit

    lateinit var resetPagination: (defaultState: Boolean?) -> Unit

    lateinit var setPageCount: (updater: Updater) -> Unit

    lateinit var setPageIndex: (updater: Updater) -> Unit

    lateinit var setPageSize: (updater: Updater) -> Unit

    lateinit var setPagination: (updater: Updater) -> Unit
    // endregion

    // region RowSelectionInstance (RowSelection feature)
    lateinit var getFilteredSelectedRowModel: () -> RowModel<TData>

    lateinit var getGroupedSelectedRowModel: () -> RowModel<TData>

    lateinit var getIsAllPageRowsSelected: () -> Boolean

    lateinit var getIsAllRowsSelected: () -> Boolean

    lateinit var getIsSomePageRowsSelected: () -> Boolean

    lateinit var getIsSomeRowsSelected: () -> Boolean

    lateinit var getPreSelectedRowModel: () -> RowModel<TData>

    lateinit var getSelectedRowModel: () -> RowModel<TData>

    lateinit var getToggleAllPageRowsSelectedHandler: () -> (event: Any?) -> Unit

    lateinit var getToggleAllRowsSelectedHandler: () -> (event: Any?) -> Unit

    lateinit var resetRowSelection: (defaultState: Boolean?) -> Unit

    lateinit var setRowSelection: (updater: Updater) -> Unit

    lateinit var toggleAllPageRowsSelected: (value: Boolean?) -> Unit

    lateinit var toggleAllRowsSelected: (value: Boolean?) -> Unit
    // endregion
}
