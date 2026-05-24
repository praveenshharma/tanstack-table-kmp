package io.github.tanstacktable.core

/*
 * The `Cell` object. A cell is assembled from a core part (`CoreCell`) and the
 * `ColumnGrouping` feature (`GroupingCell`), authored here as one mutable class
 * with members grouped by their originating concern. Function-valued members
 * are `lateinit var` because the core constructor and the feature `createCell`
 * hooks assign them after construction (they close over the cell/row/column/
 * table).
 *
 * Construction logic (`createCell`) lives in CoreCell.kt.
 */

/**
 * Context object passed to cell renderers. Carries the cell, its column, row
 * and owning table, plus value accessors. `getValue` returns the raw cell
 * value, while `renderValue` returns the value or the configured
 * `renderFallbackValue` when null.
 */
class CellContext<TData, TValue>(
    val cell: Cell<TData, TValue>,
    val column: Column<TData, TValue>,
    val getValue: () -> TValue,
    val renderValue: () -> TValue?,
    val row: Row<TData>,
    val table: Table<TData>,
)

/**
 * A single cell in the table — the intersection of a [Row] and a [Column].
 * Combines the core cell members with the [ColumnGrouping] feature members
 * (`getIsAggregated`/`getIsGrouped`/`getIsPlaceholder`).
 */
class Cell<TData, TValue> {

    // region CoreCell
    /** The column this cell belongs to. */
    lateinit var column: Column<TData, TValue>

    /** Returns the [CellContext] used by header/cell renderers. */
    lateinit var getContext: () -> CellContext<TData, TValue>

    /** Returns the raw value at this cell. */
    lateinit var getValue: () -> TValue

    /** Stable cell id of the form `"<rowId>_<columnId>"`. */
    lateinit var id: String

    /** Returns the value to render, falling back to `renderFallbackValue` when null. */
    lateinit var renderValue: () -> TValue?

    /** The row this cell belongs to. */
    lateinit var row: Row<TData>
    // endregion

    // region GroupingCell (ColumnGrouping feature)
    /** True when this cell holds an aggregated value for a grouped row. */
    lateinit var getIsAggregated: () -> Boolean

    /** True when this cell is the grouping cell (the one whose column drove the group). */
    lateinit var getIsGrouped: () -> Boolean

    /** True when this cell is a placeholder in a grouped row (neither grouped nor aggregated). */
    lateinit var getIsPlaceholder: () -> Boolean
    // endregion

    /**
     * Pinned-position tag attached to cells produced by the [ColumnPinning]
     * feature's `getLeftVisibleCells`/`getRightVisibleCells`. `null` for
     * unpinned cells; otherwise `"left"` or `"right"`.
     */
    var position: String? = null
}
