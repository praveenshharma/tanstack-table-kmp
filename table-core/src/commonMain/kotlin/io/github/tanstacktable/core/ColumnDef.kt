package io.github.tanstacktable.core

/*
 * The `ColumnDef` and `ColumnDefResolved` types.
 *
 * Conceptually a column def is one of three shapes — display, group, or
 * accessor (key/fn) — combined with the seven feature def extensions
 * (`VisibilityColumnDef`, `ColumnPinningColumnDef`, `ColumnFiltersColumnDef`,
 * `GlobalFilterColumnDef`, `SortingColumnDef`, `GroupingColumnDef`,
 * `ColumnSizingColumnDef`). Kotlin has no union or intersection types, so
 * the variants are collapsed into one [ColumnDef] class whose every field
 * is nullable with a `null` default. This matches the runtime reality — a
 * column def is a plain object literal whose shape is discriminated at
 * runtime (`accessorFn != null`, `columns != null`, `accessorKey != null`).
 *
 * Trade-off: combinations that are illegal in the original union (such as
 * setting `accessorKey` and `accessorFn` on the same def) become a runtime
 * concern rather than a compile error.
 */

/**
 * Definition of a column. Carries the identifier (`id`/`header`), the
 * accessor (`accessorKey` or `accessorFn`), nested `columns` for group
 * columns, plus the feature-specific configuration (`enableSorting`,
 * `filterFn`, `size`, ...). All fields are nullable; which fields you set
 * determines what kind of column you build.
 */
class ColumnDef<TData, TValue> {

    // region ColumnDefBase
    /** Per-column override for `getUniqueValues`. */
    var getUniqueValues: AccessorFn<TData, List<Any?>>? = null

    /** Footer template — a string or a `(HeaderContext) -> Any?` renderer. */
    var footer: ColumnDefTemplate? = null

    /** Cell template — a string or a `(CellContext) -> Any?` renderer. */
    var cell: ColumnDefTemplate? = null

    /** Caller-supplied per-column metadata. */
    var meta: ColumnMeta<TData, TValue>? = null
    // endregion

    // region Identifiers
    /**
     * Explicit column id. Required for display columns; for accessor-key
     * columns it defaults to [accessorKey] when omitted.
     */
    var id: String? = null

    /** Header template — a string or a `(HeaderContext) -> Any?` renderer. */
    var header: StringOrTemplateHeader? = null
    // endregion

    // region Group columns
    /** Child column defs — non-null on group columns. */
    var columns: List<ColumnDef<TData, Any?>>? = null
    // endregion

    // region Accessor columns
    /** Accessor function — non-null on accessor-fn columns. */
    var accessorFn: AccessorFn<TData, TValue>? = null

    /**
     * Accessor key. Non-null on accessor-key columns. May be a dot-separated
     * path (`"user.address.city"`); the path is resolved at runtime.
     */
    var accessorKey: String? = null
    // endregion

    // region VisibilityColumnDef
    var enableHiding: Boolean? = null
    // endregion

    // region ColumnPinningColumnDef
    var enablePinning: Boolean? = null
    // endregion

    // region ColumnFiltersColumnDef
    var enableColumnFilter: Boolean? = null

    /** Filter fn for this column — see [FilterFnOption]. */
    var filterFn: FilterFnOption<TData>? = null
    // endregion

    // region GlobalFilterColumnDef
    var enableGlobalFilter: Boolean? = null
    // endregion

    // region SortingColumnDef
    var enableMultiSort: Boolean? = null
    var enableSorting: Boolean? = null
    var invertSorting: Boolean? = null
    var sortDescFirst: Boolean? = null

    /** Sort fn for this column — see [SortingFnOption]. */
    var sortingFn: SortingFnOption<TData>? = null

    /**
     * How `null`/undefined values are sorted. One of `false`, `-1`, `1`,
     * `"first"` or `"last"`. Typed as `Any?` because Kotlin cannot express
     * this mixed union directly.
     */
    var sortUndefined: Any? = null
    // endregion

    // region GroupingColumnDef
    /** Aggregated-cell template — used to render grouped/aggregated cells. */
    var aggregatedCell: ColumnDefTemplate? = null

    /** Aggregation fn for this column — see [AggregationFnOption]. */
    var aggregationFn: AggregationFnOption<TData>? = null

    var enableGrouping: Boolean? = null

    /** Returns the grouping key for a row. Defaults to the cell value when omitted. */
    var getGroupingValue: ((row: TData) -> Any?)? = null
    // endregion

    // region ColumnSizingColumnDef
    var enableResizing: Boolean? = null

    /** Maximum size in pixels. Defaults to the platform max-safe integer. */
    var maxSize: Double? = null

    /** Minimum size in pixels. */
    var minSize: Double? = null

    /** Preferred size in pixels. */
    var size: Double? = null
    // endregion

    companion object {
        /**
         * Builds a fresh [ColumnDef] whose [size]/[minSize]/[maxSize] are
         * copied from [defaultColumnSizing] and every other field is `null`.
         *
         * This is the contribution of the [ColumnSizing] feature's
         * `getDefaultColumnDef`: every column starts out carrying the
         * default sizing values, which are later spread into the resolved
         * def by `createTable._getDefaultColumnDef`.
         */
        fun <TData, TValue> fromDefaultSizing(
            defaultColumnSizing: DefaultColumnSizing,
        ): ColumnDef<TData, TValue> {
            val columnDef = ColumnDef<TData, TValue>()
            columnDef.size = defaultColumnSizing.size
            columnDef.minSize = defaultColumnSizing.minSize
            columnDef.maxSize = defaultColumnSizing.maxSize
            return columnDef
        }
    }
}

/**
 * A fully resolved column definition: structurally the same as [ColumnDef]
 * with every field optional, plus the explicit `accessorKey?: String` carry.
 * Aliased to [ColumnDef] since [ColumnDef] already exposes all fields as
 * nullable.
 */
typealias ColumnDefResolved<TData, TValue> = ColumnDef<TData, TValue>
