package io.github.tanstacktable.core

/*
 * The `Row` object. A row is assembled from a core part (`CoreRow`) and
 * seven feature row extensions (`VisibilityRow`, `ColumnPinningRow`,
 * `RowPinningRow`, `ColumnFiltersRow`, `GroupingRow`, `RowSelectionRow`,
 * `ExpandedRow`). All members are authored on a single mutable class and
 * grouped by their originating concern via `// region` blocks.
 *
 * Function-valued members are `lateinit var` because the core `createRow`
 * and every feature's `createRow` hook assign them after construction —
 * they close over `row`/`table`. Data members carry their initial value.
 *
 * Construction logic (`createRow` and the feature hooks) lives in
 * CoreRow.kt and the per-feature files.
 */

/**
 * A row in the table. Integer-typed `depth` / `index` use [Int]; values
 * read through `getValue` / `getUniqueValues` / `renderValue` are typed
 * `Any?` because Kotlin cannot carry the original `<TValue>` per-call type
 * argument.
 */
class Row<TData> {

    // region CoreRow
    /** Returns this row's cells indexed by column id. */
    lateinit var _getAllCellsByColumnId: () -> Map<String, Cell<TData, Any?>>

    /** Internal cache backing `getUniqueValues`. Mutated by key. */
    var _uniqueValuesCache: MutableMap<String, Any?> = mutableMapOf()

    /** Internal cache backing `getValue`. Mutated by key. */
    var _valuesCache: MutableMap<String, Any?> = mutableMapOf()

    /** Nesting depth (0 for top-level rows). */
    var depth: Int = 0

    /** Returns every cell on this row in column order. */
    lateinit var getAllCells: () -> List<Cell<TData, Any?>>

    /** Returns every leaf descendant of this row (flattened). */
    lateinit var getLeafRows: () -> List<Row<TData>>

    /** Returns the parent row, or null when this is a root row. */
    lateinit var getParentRow: () -> Row<TData>?

    /** Returns every ancestor row from root down to the immediate parent. */
    lateinit var getParentRows: () -> List<Row<TData>>

    /** Returns the column-keyed unique values for [columnId]. */
    lateinit var getUniqueValues: (columnId: String) -> List<Any?>

    /** Returns the raw value at [columnId] for this row. */
    lateinit var getValue: (columnId: String) -> Any?

    /** Stable row id. */
    lateinit var id: String

    /** Index of this row within its parent's sub-rows (or top-level rows). */
    var index: Int = 0

    /**
     * The original data record this row was built from. Set by `createRow`
     * at construction time; the `null`-backed default is never observed.
     */
    @Suppress("UNCHECKED_CAST")
    var original: TData = null as TData

    /** Original (unfiltered) sub-row data, when known. */
    var originalSubRows: List<TData>? = null

    /** Parent row id, or null when this is a root row. */
    var parentId: String? = null

    /** Returns the value at [columnId], falling back to `renderFallbackValue` when null. */
    lateinit var renderValue: (columnId: String) -> Any?

    /** Sub-rows in their current order; may be re-sorted/filtered by row-model builders. */
    var subRows: List<Row<TData>> = mutableListOf()
    // endregion

    // region VisibilityRow (ColumnVisibility feature)
    /** Returns every cell on this row whose column is visible. */
    lateinit var _getAllVisibleCells: () -> List<Cell<TData, Any?>>

    /** Returns the visible cells (left + center + right pinned sections). */
    lateinit var getVisibleCells: () -> List<Cell<TData, Any?>>
    // endregion

    // region ColumnPinningRow (ColumnPinning feature)
    /** Returns the visible cells in the center (unpinned) section. */
    lateinit var getCenterVisibleCells: () -> List<Cell<TData, Any?>>

    /** Returns the visible cells in the left-pinned section. */
    lateinit var getLeftVisibleCells: () -> List<Cell<TData, Any?>>

    /** Returns the visible cells in the right-pinned section. */
    lateinit var getRightVisibleCells: () -> List<Cell<TData, Any?>>
    // endregion

    // region RowPinningRow (RowPinning feature)
    /** True when this row can be pinned. */
    lateinit var getCanPin: () -> Boolean

    /** Returns the row's current pinned position. */
    lateinit var getIsPinned: () -> RowPinningPosition

    /** Index of this row within its pinned group. */
    lateinit var getPinnedIndex: () -> Int

    /** Pin this row to [position], optionally pinning its leaves/parents too. */
    lateinit var pin: (
        position: RowPinningPosition,
        includeLeafRows: Boolean?,
        includeParentRows: Boolean?,
    ) -> Unit
    // endregion

    // region ColumnFiltersRow (ColumnFiltering feature)
    /** Per-column-filter pass/fail flags. Mutated by the filtering pipeline. */
    var columnFilters: MutableMap<String, Boolean> = mutableMapOf()

    /** Per-column-filter caller-supplied metadata. Mutated by filter fns. */
    var columnFiltersMeta: MutableMap<String, FilterMeta> = mutableMapOf()
    // endregion

    // region GroupingRow (ColumnGrouping feature)
    /** Internal cache backing aggregated `getValue` lookups. Mutated by key. */
    var _groupingValuesCache: MutableMap<String, Any?> = mutableMapOf()

    /** Returns the grouping key for [columnId] on this row. */
    lateinit var getGroupingValue: (columnId: String) -> Any?

    /** True when this row is a grouping parent (synthesised during grouping). */
    lateinit var getIsGrouped: () -> Boolean

    /** Column id that produced this group (non-null on grouped parent rows). */
    var groupingColumnId: String? = null

    /** Grouping value that produced this group (non-null on grouped parent rows). */
    var groupingValue: Any? = null

    /**
     * Leaf rows under this group, populated by `getGroupedRowModel`. Null
     * for non-grouped rows.
     */
    var leafRows: List<Row<TData>>? = null
    // endregion

    // region RowSelectionRow (RowSelection feature)
    /** True when multi-select is allowed for this row. */
    lateinit var getCanMultiSelect: () -> Boolean

    /** True when this row can be selected at all. */
    lateinit var getCanSelect: () -> Boolean

    /** True when this row's sub-rows can be selected. */
    lateinit var getCanSelectSubRows: () -> Boolean

    /** True when every sub-row of this row is selected. */
    lateinit var getIsAllSubRowsSelected: () -> Boolean

    /** True when this row is currently selected. */
    lateinit var getIsSelected: () -> Boolean

    /** True when some, but not all, of this row's sub-rows are selected. */
    lateinit var getIsSomeSelected: () -> Boolean

    /**
     * Returns an event handler that toggles this row's selection from a
     * checkbox-style event target. The event parameter is platform-specific.
     */
    lateinit var getToggleSelectedHandler: () -> (event: Any?) -> Unit

    /**
     * Toggles selection. Pass `null` for [value] to flip the current state;
     * use [RowSelectionToggleOpts] to control whether sub-rows are also
     * toggled.
     */
    lateinit var toggleSelected: (value: Boolean?, opts: RowSelectionToggleOpts?) -> Unit
    // endregion

    // region ExpandedRow (RowExpanding feature)
    /** True when this row can be expanded. */
    lateinit var getCanExpand: () -> Boolean

    /** True when every ancestor up to the root is expanded. */
    lateinit var getIsAllParentsExpanded: () -> Boolean

    /** True when this row is currently expanded. */
    lateinit var getIsExpanded: () -> Boolean

    /** Returns a click handler that toggles expansion for this row. */
    lateinit var getToggleExpandedHandler: () -> () -> Unit

    /** Sets the expanded state; pass `null` to toggle. */
    lateinit var toggleExpanded: (expanded: Boolean?) -> Unit
    // endregion

    /**
     * Pinned-position tag attached to rows produced by the [RowPinning]
     * feature's `_getPinnedRows`. `null` for unpinned rows; otherwise
     * `"top"` or `"bottom"`.
     */
    var position: String? = null

    /**
     * Returns a shallow copy of this row. The copy carries every property
     * by reference (function members close over the original `row`); the
     * caller is free to overwrite specific fields (such as `position` or
     * `subRows`) on the returned instance.
     *
     * Keep in sync with the field list — any new property added to [Row]
     * also needs a copy assignment here.
     */
    fun shallowCopy(): Row<TData> {
        val copy = Row<TData>()
        // CoreRow
        copy._getAllCellsByColumnId = this._getAllCellsByColumnId
        copy._uniqueValuesCache = this._uniqueValuesCache
        copy._valuesCache = this._valuesCache
        copy.depth = this.depth
        copy.getAllCells = this.getAllCells
        copy.getLeafRows = this.getLeafRows
        copy.getParentRow = this.getParentRow
        copy.getParentRows = this.getParentRows
        copy.getUniqueValues = this.getUniqueValues
        copy.getValue = this.getValue
        copy.id = this.id
        copy.index = this.index
        copy.original = this.original
        copy.originalSubRows = this.originalSubRows
        copy.parentId = this.parentId
        copy.renderValue = this.renderValue
        copy.subRows = this.subRows
        // VisibilityRow
        copy._getAllVisibleCells = this._getAllVisibleCells
        copy.getVisibleCells = this.getVisibleCells
        // ColumnPinningRow
        copy.getCenterVisibleCells = this.getCenterVisibleCells
        copy.getLeftVisibleCells = this.getLeftVisibleCells
        copy.getRightVisibleCells = this.getRightVisibleCells
        // RowPinningRow
        copy.getCanPin = this.getCanPin
        copy.getIsPinned = this.getIsPinned
        copy.getPinnedIndex = this.getPinnedIndex
        copy.pin = this.pin
        // ColumnFiltersRow
        copy.columnFilters = this.columnFilters
        copy.columnFiltersMeta = this.columnFiltersMeta
        // GroupingRow
        copy._groupingValuesCache = this._groupingValuesCache
        copy.getGroupingValue = this.getGroupingValue
        copy.getIsGrouped = this.getIsGrouped
        copy.groupingColumnId = this.groupingColumnId
        copy.groupingValue = this.groupingValue
        copy.leafRows = this.leafRows
        // RowSelectionRow
        copy.getCanMultiSelect = this.getCanMultiSelect
        copy.getCanSelect = this.getCanSelect
        copy.getCanSelectSubRows = this.getCanSelectSubRows
        copy.getIsAllSubRowsSelected = this.getIsAllSubRowsSelected
        copy.getIsSelected = this.getIsSelected
        copy.getIsSomeSelected = this.getIsSomeSelected
        copy.getToggleSelectedHandler = this.getToggleSelectedHandler
        copy.toggleSelected = this.toggleSelected
        // ExpandedRow
        copy.getCanExpand = this.getCanExpand
        copy.getIsAllParentsExpanded = this.getIsAllParentsExpanded
        copy.getIsExpanded = this.getIsExpanded
        copy.getToggleExpandedHandler = this.getToggleExpandedHandler
        copy.toggleExpanded = this.toggleExpanded
        copy.position = this.position
        return copy
    }
}

/**
 * Options accepted by [Row.toggleSelected]. [selectChildren] controls
 * whether the toggle cascades to sub-rows; null leaves the default in
 * place.
 */
class RowSelectionToggleOpts(
    val selectChildren: Boolean? = null,
)
