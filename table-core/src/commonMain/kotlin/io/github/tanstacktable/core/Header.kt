package io.github.tanstacktable.core

/*
 * The `Header` and `HeaderGroup` objects. The header combines core members
 * with the [ColumnSizing] feature; `HeaderGroup` is core-only. Each is
 * authored as one mutable class with members grouped by originating concern.
 *
 * Construction logic (`createHeader`, the `Headers` feature, `buildHeaderGroups`)
 * lives in CoreHeaders.kt.
 */

/**
 * Context object passed to header renderers. Carries the column, header and
 * owning table.
 */
class HeaderContext<TData, TValue>(
    val column: Column<TData, TValue>,
    val header: Header<TData, TValue>,
    val table: Table<TData>,
)

/**
 * A column header cell. Combines the core header members with the
 * [ColumnSizing] feature members (`getResizeHandler`/`getSize`/`getStart`).
 *
 * Integer counts (`colSpan`, `rowSpan`, `depth`, `index`) are `Int`. Sizes
 * returned by `getSize`/`getStart` are `Double` because resize math can
 * produce fractional pixel values.
 */
class Header<TData, TValue> {

    // region CoreHeader
    /** Column span. Mutated by `buildHeaderGroups`; starts at 0. */
    var colSpan: Int = 0

    /** The column this header represents. */
    lateinit var column: Column<TData, TValue>

    /** Depth of this header in the header tree (root = 0). */
    var depth: Int = 0

    /** Returns the [HeaderContext] used by header renderers. */
    lateinit var getContext: () -> HeaderContext<TData, TValue>

    /** Returns the flattened list of leaf headers beneath this header. */
    lateinit var getLeafHeaders: () -> List<Header<TData, *>>

    /** The [HeaderGroup] this header belongs to. Assigned during `buildHeaderGroups`. */
    lateinit var headerGroup: HeaderGroup<TData>

    /** Stable header id. */
    lateinit var id: String

    /** Index of this header within its [HeaderGroup]. */
    var index: Int = 0

    /** True when this header is a placeholder (a non-leaf header that has no real column). */
    var isPlaceholder: Boolean = false

    /** Placeholder id used to keep React-style keys stable across renders. */
    var placeholderId: String? = null

    /** Row span. Mutated by `buildHeaderGroups`; starts at 0. */
    var rowSpan: Int = 0

    /** Sub-headers (immediate children in the header tree). Mutated during `buildHeaderGroups`. */
    var subHeaders: MutableList<Header<TData, TValue>> = mutableListOf()
    // endregion

    // region ColumnSizingHeader (ColumnSizing feature)
    /**
     * Returns a pointer/touch event handler that drives column resizing for
     * this header. The first parameter is the owner document (browser-only,
     * `Any?` in commonMain); the returned handler accepts a platform event
     * (also `Any?` here).
     */
    lateinit var getResizeHandler: (contextDocument: Any?) -> (event: Any?) -> Unit

    /** Returns the current rendered size of this header in pixels. */
    lateinit var getSize: () -> Double

    /** Returns the start offset of this header for the given pinned position (or unpinned when null). */
    lateinit var getStart: (position: ColumnPinningPosition?) -> Double
    // endregion
}

/**
 * A row of [Header]s — one entry per level in the header tree. `headers` is
 * mutated during construction by `buildHeaderGroups`.
 */
class HeaderGroup<TData>(
    var depth: Int,
    var id: String,
    val headers: MutableList<Header<TData, *>> = mutableListOf(),
)
