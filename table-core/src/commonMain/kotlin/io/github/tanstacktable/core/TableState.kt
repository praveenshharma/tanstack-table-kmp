package io.github.tanstacktable.core

/*
 * The `TableState` and `InitialTableState` types.
 *
 * `TableState` collects the per-feature state slices into one class so
 * features can share access to it. It is a `data class` to expose `copy()`,
 * which [makeStateUpdater] (via [copyWithKey] / [getByKey]) uses to update
 * one field at a time by name.
 *
 * Each field defaults to the canonical empty value for its slice — so
 * `TableState()` is the all-defaults state. The defaults duplicate the
 * private default factories from each feature file.
 */

/**
 * The unified table-state container. Carries one field per feature slice
 * (`columnVisibility`, `columnOrder`, `columnPinning`, ...). Each field
 * defaults to its slice's canonical empty value; an immutable [InitialTableState]
 * supplied by the caller overlays them at table construction time.
 */
data class TableState(
    var columnVisibility: VisibilityState = emptyMap(),
    var columnOrder: ColumnOrderState = emptyList(),
    var columnPinning: ColumnPinningState = ColumnPinningState(left = emptyList(), right = emptyList()),
    var rowPinning: RowPinningState = RowPinningState(top = emptyList(), bottom = emptyList()),
    var columnFilters: ColumnFiltersState = emptyList(),
    /** The active global filter value. Defaults to `null` (no global filter). */
    var globalFilter: Any? = null,
    var sorting: SortingState = emptyList(),
    /** Expansion state — either the literal `true` (all expanded) or a row-id map. */
    var expanded: ExpandedState = emptyMap<String, Boolean>(),
    var grouping: GroupingState = emptyList(),
    var columnSizing: ColumnSizingState = emptyMap(),
    var columnSizingInfo: ColumnSizingInfoState = ColumnSizingInfoState(
        startOffset = null,
        startSize = null,
        deltaOffset = null,
        deltaPercentage = null,
        isResizingColumn = false,
        columnSizingStart = emptyList(),
    ),
    var pagination: PaginationState = PaginationState(pageIndex = 0, pageSize = 10),
    var rowSelection: RowSelectionState = emptyMap(),
) {
    /**
     * Returns the current value of the named state slice. Used by
     * [makeStateUpdater] to read the old value before applying an [Updater].
     */
    fun getByKey(key: String): Any? = when (key) {
        "columnVisibility" -> columnVisibility
        "columnOrder" -> columnOrder
        "columnPinning" -> columnPinning
        "rowPinning" -> rowPinning
        "columnFilters" -> columnFilters
        "globalFilter" -> globalFilter
        "sorting" -> sorting
        "expanded" -> expanded
        "grouping" -> grouping
        "columnSizing" -> columnSizing
        "columnSizingInfo" -> columnSizingInfo
        "pagination" -> pagination
        "rowSelection" -> rowSelection
        else -> throw IllegalArgumentException("Unknown TableState key: $key")
    }

    /**
     * Returns a copy of this state with the named slice replaced by [value].
     * Used by [makeStateUpdater] to assemble the new state after applying
     * an [Updater].
     */
    @Suppress("UNCHECKED_CAST")
    fun copyWithKey(key: String, value: Any?): TableState = when (key) {
        "columnVisibility" -> copy(columnVisibility = value as VisibilityState)
        "columnOrder" -> copy(columnOrder = value as ColumnOrderState)
        "columnPinning" -> copy(columnPinning = value as ColumnPinningState)
        "rowPinning" -> copy(rowPinning = value as RowPinningState)
        "columnFilters" -> copy(columnFilters = value as ColumnFiltersState)
        "globalFilter" -> copy(globalFilter = value)
        "sorting" -> copy(sorting = value as SortingState)
        "expanded" -> copy(expanded = value as ExpandedState)
        "grouping" -> copy(grouping = value as GroupingState)
        "columnSizing" -> copy(columnSizing = value as ColumnSizingState)
        "columnSizingInfo" -> copy(columnSizingInfo = value as ColumnSizingInfoState)
        "pagination" -> copy(pagination = value as PaginationState)
        "rowSelection" -> copy(rowSelection = value as RowSelectionState)
        else -> throw IllegalArgumentException("Unknown TableState key: $key")
    }

    companion object {
        /**
         * Materialises [state] into a [TableState], then overrides the
         * passed named slices.
         *
         * Used by feature `getInitialState` implementations to express
         * "default first, then [state] wins, then any slice this feature is
         * authoritative for". Pass `null` for a slice to leave the value
         * produced from [state] in place.
         *
         * `globalFilter` is not exposed here because `Any?` cannot
         * distinguish "no override" from "override with null"; features
         * needing it use [toTableState] + `.copy` directly.
         */
        fun fromInitialState(
            state: InitialTableState?,
            columnVisibility: VisibilityState? = null,
            columnOrder: ColumnOrderState? = null,
            columnPinning: ColumnPinningState? = null,
            rowPinning: RowPinningState? = null,
            columnFilters: ColumnFiltersState? = null,
            sorting: SortingState? = null,
            expanded: ExpandedState? = null,
            grouping: GroupingState? = null,
            columnSizing: ColumnSizingState? = null,
            columnSizingInfo: ColumnSizingInfoState? = null,
            pagination: PaginationState? = null,
            rowSelection: RowSelectionState? = null,
        ): TableState {
            val base = state.toTableState()
            if (columnVisibility != null) base.columnVisibility = columnVisibility
            if (columnOrder != null) base.columnOrder = columnOrder
            if (columnPinning != null) base.columnPinning = columnPinning
            if (rowPinning != null) base.rowPinning = rowPinning
            if (columnFilters != null) base.columnFilters = columnFilters
            if (sorting != null) base.sorting = sorting
            if (expanded != null) base.expanded = expanded
            if (grouping != null) base.grouping = grouping
            if (columnSizing != null) base.columnSizing = columnSizing
            if (columnSizingInfo != null) base.columnSizingInfo = columnSizingInfo
            if (pagination != null) base.pagination = pagination
            if (rowSelection != null) base.rowSelection = rowSelection
            return base
        }
    }
}

/**
 * Materialises an [InitialTableState] into a [TableState]: every slice the
 * caller supplied is copied across; missing slices fall back to the
 * [TableState] defaults. A `null` receiver yields a fully-defaulted state.
 */
fun InitialTableState?.toTableState(): TableState {
    val s = this ?: return TableState()
    return TableState(
        columnVisibility = s.columnVisibility ?: TableState().columnVisibility,
        columnOrder = s.columnOrder ?: TableState().columnOrder,
        columnPinning = s.columnPinning ?: TableState().columnPinning,
        rowPinning = s.rowPinning ?: TableState().rowPinning,
        columnFilters = s.columnFilters ?: TableState().columnFilters,
        // `globalFilter` is `Any?` and may legitimately be `null`; the null
        // initial value collapses onto the (also-null) default.
        globalFilter = s.globalFilter,
        sorting = s.sorting ?: TableState().sorting,
        expanded = s.expanded ?: TableState().expanded,
        grouping = s.grouping ?: TableState().grouping,
        columnSizing = s.columnSizing ?: TableState().columnSizing,
        columnSizingInfo = s.columnSizingInfo ?: TableState().columnSizingInfo,
        pagination = s.pagination ?: TableState().pagination,
        rowSelection = s.rowSelection ?: TableState().rowSelection,
    )
}

/**
 * Builds a [TableState] by first running [applyDefaults] on a fresh
 * default-filled state, then overlaying every slice present on [state].
 *
 * Provided for features whose default contribution is a mutating block
 * rather than a set of named overrides, so they can express "my defaults
 * first, then `state` wins".
 */
fun applyTableStateDefaults(
    state: InitialTableState?,
    applyDefaults: (TableState) -> Unit,
): TableState {
    val defaults = TableState()
    applyDefaults(defaults)
    return TableState(
        columnVisibility = state?.columnVisibility ?: defaults.columnVisibility,
        columnOrder = state?.columnOrder ?: defaults.columnOrder,
        columnPinning = state?.columnPinning ?: defaults.columnPinning,
        rowPinning = state?.rowPinning ?: defaults.rowPinning,
        columnFilters = state?.columnFilters ?: defaults.columnFilters,
        globalFilter = state?.globalFilter ?: defaults.globalFilter,
        sorting = state?.sorting ?: defaults.sorting,
        expanded = state?.expanded ?: defaults.expanded,
        grouping = state?.grouping ?: defaults.grouping,
        columnSizing = state?.columnSizing ?: defaults.columnSizing,
        columnSizingInfo = state?.columnSizingInfo ?: defaults.columnSizingInfo,
        pagination = state?.pagination ?: defaults.pagination,
        rowSelection = state?.rowSelection ?: defaults.rowSelection,
    )
}

/**
 * All-optional counterpart of [TableState]. Pass to `options.initialState`
 * to seed the table; absent slices default to their [TableState] defaults.
 *
 * Note: [pagination] is the full [PaginationState] rather than a per-field
 * partial — Kotlin cannot model a `Partial<PaginationState>` directly.
 */
class InitialTableState(
    val columnVisibility: VisibilityState? = null,
    val columnOrder: ColumnOrderState? = null,
    val columnPinning: ColumnPinningState? = null,
    val rowPinning: RowPinningState? = null,
    val columnFilters: ColumnFiltersState? = null,
    val globalFilter: Any? = null,
    val sorting: SortingState? = null,
    val expanded: ExpandedState? = null,
    val grouping: GroupingState? = null,
    val columnSizing: ColumnSizingState? = null,
    val columnSizingInfo: ColumnSizingInfoState? = null,
    val pagination: PaginationState? = null,
    val rowSelection: RowSelectionState? = null,
)
