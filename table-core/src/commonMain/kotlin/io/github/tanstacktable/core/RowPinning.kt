package io.github.tanstacktable.core

/*
 * Row-pinning feature. Tracks rows pinned to the top or bottom of the
 * table, plus the row-level pin/getIsPinned accessors and the table-level
 * `getTopRows` / `getCenterRows` / `getBottomRows` partitioning.
 */

/**
 * Pinned position for a row. Either `"top"`, `"bottom"`, or the literal
 * `false` (unpinned). Typed as `Any?` because Kotlin cannot express that
 * mixed union directly.
 */
typealias RowPinningPosition = Any?

/**
 * Pinned-row ids partitioned by position. Both lists default to `null`
 * (no rows pinned in that position).
 */
class RowPinningState(
    val top: List<String>? = null,
    val bottom: List<String>? = null,
)

/**
 * Default pinning state — empty `top` and `bottom` lists. A fresh instance
 * is returned on each call so callers can mutate freely.
 */
private fun getDefaultRowPinningState(): RowPinningState =
    RowPinningState(
        top = emptyList(),
        bottom = emptyList(),
    )

/**
 * The `RowPinning` feature. Adds `rowPinning` state, the row-level
 * `pin` / `getIsPinned` / `getCanPin` / `getPinnedIndex` accessors, and
 * the table-level partitioning into top, center and bottom rows.
 */
@Suppress("UNCHECKED_CAST")
object RowPinning : TableFeature {

    override val getInitialState: ((initialState: InitialTableState?) -> TableState) = { state ->
        TableState.fromInitialState(
            state,
            rowPinning = state?.rowPinning ?: getDefaultRowPinningState(),
        )
    }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>) = { table ->
        TableOptionsResolved<Any?>().also {
            it.onRowPinningChange = makeStateUpdater("rowPinning", table)
        }
    }

    override val createRow: ((row: Row<Any?>, table: Table<Any?>) -> Unit) = { row, table ->
        row.pin = { position: RowPinningPosition, includeLeafRows: Boolean?, includeParentRows: Boolean? ->
            val leafRowIds = if (includeLeafRows == true) {
                row.getLeafRows().map { it.id }
            } else {
                emptyList()
            }
            val parentRowIds = if (includeParentRows == true) {
                row.getParentRows().map { it.id }
            } else {
                emptyList()
            }
            val rowIds: Set<String> = (parentRowIds + row.id + leafRowIds).toSet()

            table.setRowPinning { oldAny: Any? ->
                val old = oldAny as RowPinningState?

                if (position == "bottom") {
                    RowPinningState(
                        top = (old?.top ?: emptyList()).filter { d -> !rowIds.contains(d) },
                        bottom = (old?.bottom ?: emptyList()).filter { d -> !rowIds.contains(d) } +
                            rowIds.toList(),
                    )
                } else if (position == "top") {
                    RowPinningState(
                        top = (old?.top ?: emptyList()).filter { d -> !rowIds.contains(d) } +
                            rowIds.toList(),
                        bottom = (old?.bottom ?: emptyList()).filter { d -> !rowIds.contains(d) },
                    )
                } else {
                    RowPinningState(
                        top = (old?.top ?: emptyList()).filter { d -> !rowIds.contains(d) },
                        bottom = (old?.bottom ?: emptyList()).filter { d -> !rowIds.contains(d) },
                    )
                }
            }
        }
        row.getCanPin = {
            val enableRowPinning = table.options.enableRowPinning
            val enablePinning = table.options.enablePinning
            // `enableRowPinning` is either a boolean or a predicate
            // `(row) -> Boolean`.
            if (isFunction(enableRowPinning)) {
                (enableRowPinning as (Row<Any?>) -> Boolean)(row)
            } else {
                (enableRowPinning as Boolean?) ?: enablePinning ?: true
            }
        }
        row.getIsPinned = {
            val rowIds = listOf(row.id)

            val rowPinning = table.getState().rowPinning
            val top = rowPinning.top
            val bottom = rowPinning.bottom

            val isTop = rowIds.any { d -> top?.contains(d) == true }
            val isBottom = rowIds.any { d -> bottom?.contains(d) == true }

            if (isTop) "top" else if (isBottom) "bottom" else false
        }
        row.getPinnedIndex = {
            val position = row.getIsPinned()
            if (!isTruthy(position)) {
                -1
            } else {
                val visiblePinnedRowIds =
                    (if (position == "top") table.getTopRows() else table.getBottomRows())
                        .map { it.id }

                visiblePinnedRowIds.indexOf(row.id)
            }
        }
    }

    override val createTable: ((table: Table<Any?>) -> Unit) = { table ->
        table.setRowPinning = { updater -> table.options.onRowPinningChange?.invoke(updater) }

        table.resetRowPinning = { defaultState: Boolean? ->
            table.setRowPinning(
                if (defaultState == true) {
                    getDefaultRowPinningState()
                } else {
                    table.initialState.rowPinning
                },
            )
        }

        table.getIsSomeRowsPinned = { position: RowPinningPosition ->
            val pinningState = table.getState().rowPinning

            // When no position is supplied, report any pinned row at all.
            if (!isTruthy(position)) {
                isTruthy(pinningState.top?.size) || isTruthy(pinningState.bottom?.size)
            } else {
                // `RowPinningState` is a closed class; resolve the dynamic
                // position with a `when` over the two valid values.
                val list = when (position) {
                    "top" -> pinningState.top
                    "bottom" -> pinningState.bottom
                    else -> null
                }
                isTruthy(list?.size)
            }
        }

        table._getPinnedRows = { visibleRows: List<Row<Any?>>, pinnedRowIds: List<String>?, position: String ->
            val rows: List<Row<Any?>?> =
                if (table.options.keepPinnedRows ?: true) {
                    // Get all rows that are pinned even if they would not be
                    // otherwise visible — accounts for expanded parents but
                    // not pagination/filtering.
                    (pinnedRowIds ?: emptyList()).map { rowId ->
                        val row = table.getRow(rowId, true)
                        if (row.getIsAllParentsExpanded()) row else null
                    }
                } else {
                    // Otherwise, get only visible rows that are pinned.
                    (pinnedRowIds ?: emptyList()).map { rowId ->
                        visibleRows.find { row -> row.id == rowId }
                    }
                }

            // Drop missing rows, then attach the pinned [position] tag to
            // a shallow copy of each.
            rows.filterNotNull().map { d ->
                d.shallowCopy().also { it.position = position }
            }
        }

        table.getTopRows = memo(
            getDeps = { listOf(table.getRowModel().rows, table.getState().rowPinning.top) },
            fn = { deps ->
                val allRows = deps[0] as List<Row<Any?>>
                val topPinnedRowIds = deps[1] as List<String>?
                table._getPinnedRows(allRows, topPinnedRowIds, "top")
            },
            opts = getMemoOptions(table.options, "debugRows", "getTopRows"),
        )

        table.getBottomRows = memo(
            getDeps = { listOf(table.getRowModel().rows, table.getState().rowPinning.bottom) },
            fn = { deps ->
                val allRows = deps[0] as List<Row<Any?>>
                val bottomPinnedRowIds = deps[1] as List<String>?
                table._getPinnedRows(allRows, bottomPinnedRowIds, "bottom")
            },
            opts = getMemoOptions(table.options, "debugRows", "getBottomRows"),
        )

        table.getCenterRows = memo(
            getDeps = {
                listOf(
                    table.getRowModel().rows,
                    table.getState().rowPinning.top,
                    table.getState().rowPinning.bottom,
                )
            },
            fn = { deps ->
                val allRows = deps[0] as List<Row<Any?>>
                val top = deps[1] as List<String>?
                val bottom = deps[2] as List<String>?
                val topAndBottom: Set<String> = ((top ?: emptyList()) + (bottom ?: emptyList())).toSet()
                allRows.filter { d -> !topAndBottom.contains(d.id) }
            },
            opts = getMemoOptions(table.options, "debugRows", "getCenterRows"),
        )
    }
}
