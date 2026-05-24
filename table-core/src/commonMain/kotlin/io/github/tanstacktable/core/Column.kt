package io.github.tanstacktable.core

/*
 * The `Column` object. A column is assembled from a core part (`CoreColumn`)
 * and nine feature column extensions (`VisibilityColumn`, `ColumnOrderColumn`,
 * `ColumnPinningColumn`, `FacetedColumn`, `ColumnFiltersColumn`,
 * `GlobalFilterColumn`, `SortingColumn`, `GroupingColumn`, `ColumnSizingColumn`).
 * All members are authored on a single mutable class and grouped by their
 * originating concern via `// region` blocks.
 *
 * Function-valued members are `lateinit var` because the core `createColumn`
 * and every feature's `createColumn` hook assign them after construction —
 * they close over `column`/`table`. Data members with a defined initial value
 * carry that value.
 *
 * Construction logic (`createColumn` and the feature hooks) lives in
 * CoreColumn.kt and the per-feature files.
 */

/**
 * A column in the table. Integer-typed `depth`, `getIndex`, `getPinnedIndex`,
 * `getSortIndex`, `getGroupedIndex` and `getFilterIndex` use [Int]; sizing
 * values (`getSize`, `getStart`, `getAfter`) use [Double] because resize math
 * can produce fractional pixel values.
 */
class Column<TData, TValue> {

    // region CoreColumn
    /** Accessor function — present on accessor-fn columns; null otherwise. */
    var accessorFn: AccessorFn<TData, TValue>? = null

    /** The [ColumnDef] this column was built from. */
    lateinit var columnDef: ColumnDef<TData, TValue>

    /**
     * Direct child columns. Set to `[]` by `createColumn` and overwritten by
     * `getAllColumns`'s `recurseColumns` with the resolved children.
     */
    var columns: List<Column<TData, TValue>> = mutableListOf()

    /** Nesting level: 0 for a root column. */
    var depth: Int = 0

    /** Returns this column flattened (this column plus all descendants). */
    lateinit var getFlatColumns: () -> List<Column<TData, TValue>>

    /** Returns the leaf descendants of this column. */
    lateinit var getLeafColumns: () -> List<Column<TData, TValue>>

    /** Stable column id. */
    lateinit var id: String

    /** The parent column, or null for a root column. */
    var parent: Column<TData, TValue>? = null
    // endregion

    // region VisibilityColumn (ColumnVisibility feature)
    /** True when this column can be hidden. */
    lateinit var getCanHide: () -> Boolean

    /** True when this column is currently visible. */
    lateinit var getIsVisible: () -> Boolean

    /**
     * Returns a checkbox-style event handler that toggles visibility from
     * `event.target.checked`. The event parameter is platform-specific; here
     * it is `Any?`.
     */
    lateinit var getToggleVisibilityHandler: () -> (event: Any?) -> Unit

    /** Sets visibility; pass `null` to toggle. */
    lateinit var toggleVisibility: (value: Boolean?) -> Unit
    // endregion

    // region ColumnOrderColumn (ColumnOrdering feature)
    /**
     * Position-aware column index. [position] is one of
     * [ColumnPinningPosition], the string `"center"`, or null for the
     * unpinned/centered index.
     */
    lateinit var getIndex: (position: Any?) -> Int

    /** True when this column is the first column at [position]. */
    lateinit var getIsFirstColumn: (position: Any?) -> Boolean

    /** True when this column is the last column at [position]. */
    lateinit var getIsLastColumn: (position: Any?) -> Boolean
    // endregion

    // region ColumnPinningColumn (ColumnPinning feature)
    /** True when this column can be pinned. */
    lateinit var getCanPin: () -> Boolean

    /** The column's current pinned position. */
    lateinit var getIsPinned: () -> ColumnPinningPosition

    /** Index of this column within its pinned group. */
    lateinit var getPinnedIndex: () -> Int

    /** Pin this column to the given position. */
    lateinit var pin: (position: ColumnPinningPosition) -> Unit
    // endregion

    // region FacetedColumn (ColumnFaceting feature)
    /** Internal min/max-values getter, populated when `getFacetedMinMaxValues` is configured. */
    var _getFacetedMinMaxValues: (() -> Pair<Double, Double>?)? = null

    /** Internal faceted row-model getter, populated when `getFacetedRowModel` is configured. */
    var _getFacetedRowModel: (() -> RowModel<TData>)? = null

    /** Internal unique-values getter, populated when `getFacetedUniqueValues` is configured. */
    var _getFacetedUniqueValues: (() -> Map<Any?, Int>)? = null

    /** Returns the (min, max) of this column's faceted values, or null when none. */
    lateinit var getFacetedMinMaxValues: () -> Pair<Double, Double>?

    /** Returns the row model for this column's facets. */
    lateinit var getFacetedRowModel: () -> RowModel<TData>

    /** Returns a `value -> count` map of this column's facets. */
    lateinit var getFacetedUniqueValues: () -> Map<Any?, Int>
    // endregion

    // region ColumnFiltersColumn (ColumnFiltering feature)
    /** Returns the inferred filter fn for this column, or null when none can be inferred. */
    lateinit var getAutoFilterFn: () -> FilterFn<TData>?

    /** True when this column can be filtered. */
    lateinit var getCanFilter: () -> Boolean

    /** Returns the active filter fn for this column, or null when unfiltered. */
    lateinit var getFilterFn: () -> FilterFn<TData>?

    /** Index of this column in the column-filters state, or `-1` when absent. */
    lateinit var getFilterIndex: () -> Int

    /** Returns the current filter value for this column. */
    lateinit var getFilterValue: () -> Any?

    /** True when this column is currently filtered. */
    lateinit var getIsFiltered: () -> Boolean

    /** Updates this column's filter value via an [Updater]. */
    lateinit var setFilterValue: (updater: Updater) -> Unit
    // endregion

    // region GlobalFilterColumn (GlobalFiltering feature)
    /** True when this column participates in global filtering. */
    lateinit var getCanGlobalFilter: () -> Boolean
    // endregion

    // region SortingColumn (RowSorting feature)
    /** Clears any sorting on this column. */
    lateinit var clearSorting: () -> Unit

    /** Returns the inferred sort direction for this column. */
    lateinit var getAutoSortDir: () -> SortDirection

    /** Returns the inferred sort function for this column. */
    lateinit var getAutoSortingFn: () -> SortingFn<TData>

    /** True when this column can participate in multi-sort. */
    lateinit var getCanMultiSort: () -> Boolean

    /** True when this column can be sorted. */
    lateinit var getCanSort: () -> Boolean

    /** Returns the direction this column will sort in first. */
    lateinit var getFirstSortDir: () -> SortDirection

    /**
     * Returns the current sort state: either `false` (unsorted) or a
     * [SortDirection]. Typed as `Any?` because the runtime union cannot be
     * expressed directly in Kotlin.
     */
    lateinit var getIsSorted: () -> Any?

    /**
     * Returns the next sort direction in the sort cycle: either a
     * [SortDirection] or `false`. Typed as `Any?` for the same reason as
     * [getIsSorted].
     */
    lateinit var getNextSortingOrder: (multi: Boolean?) -> Any?

    /** Index of this column in the sorting state, or `-1` when absent. */
    lateinit var getSortIndex: () -> Int

    /** Returns the active sort function for this column. */
    lateinit var getSortingFn: () -> SortingFn<TData>

    /**
     * Returns a header click handler that toggles sorting, or null when the
     * column cannot be sorted. The event parameter is platform-specific.
     */
    lateinit var getToggleSortingHandler: () -> ((event: Any?) -> Unit)?

    /** Toggles sorting; pass [desc]/`null` to set/clear desc and [isMulti] for multi-sort. */
    lateinit var toggleSorting: (desc: Boolean?, isMulti: Boolean?) -> Unit
    // endregion

    // region GroupingColumn (ColumnGrouping feature)
    /** Returns the active aggregation fn for this column, or null when none. */
    lateinit var getAggregationFn: () -> AggregationFn<TData>?

    /** Returns the inferred aggregation fn for this column, or null when none. */
    lateinit var getAutoAggregationFn: () -> AggregationFn<TData>?

    /** True when this column can be grouped. */
    lateinit var getCanGroup: () -> Boolean

    /** Index of this column in the grouping state, or `-1` when absent. */
    lateinit var getGroupedIndex: () -> Int

    /** True when this column is currently grouped. */
    lateinit var getIsGrouped: () -> Boolean

    /** Returns a click handler that toggles grouping for this column. */
    lateinit var getToggleGroupingHandler: () -> () -> Unit

    /** Toggles grouping for this column. */
    lateinit var toggleGrouping: () -> Unit
    // endregion

    // region ColumnSizingColumn (ColumnSizing feature)
    /** True when this column can be resized. */
    lateinit var getCanResize: () -> Boolean

    /** True when this column is currently being resized. */
    lateinit var getIsResizing: () -> Boolean

    /** Returns the column's current rendered size in pixels. */
    lateinit var getSize: () -> Double

    /** Returns this column's start offset for the given [position]. */
    lateinit var getStart: (position: Any?) -> Double

    /** Returns this column's end offset for the given [position]. */
    lateinit var getAfter: (position: Any?) -> Double

    /** Resets this column's size to its default. */
    lateinit var resetSize: () -> Unit
    // endregion
}
