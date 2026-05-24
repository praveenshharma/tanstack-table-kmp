package io.github.tanstacktable.core

/*
 * Column-pinning feature. Tracks columns pinned to the left or right of
 * the table, the per-column pin/getIsPinned accessors and the row/table
 * partitioning into left, center and right sections.
 */

/**
 * Pinned position for a column. Either `"left"`, `"right"`, or the
 * literal `false` (unpinned). Typed as `Any?` because Kotlin cannot
 * express that mixed union directly.
 */
typealias ColumnPinningPosition = Any?

/**
 * Pinned-column ids partitioned by position. Both lists default to `null`
 * (no columns pinned in that position).
 */
class ColumnPinningState(
    val left: List<String>? = null,
    val right: List<String>? = null,
)

private fun getDefaultColumnPinningState(): ColumnPinningState =
    ColumnPinningState(
        left = emptyList(),
        right = emptyList(),
    )

private fun pinnedCellCopy(d: Cell<Any?, Any?>, position: String): Cell<Any?, Any?> {
    val copy = Cell<Any?, Any?>()
    copy.column = d.column
    copy.getContext = d.getContext
    copy.getValue = d.getValue
    copy.id = d.id
    copy.renderValue = d.renderValue
    copy.row = d.row
    copy.getIsAggregated = d.getIsAggregated
    copy.getIsGrouped = d.getIsGrouped
    copy.getIsPlaceholder = d.getIsPlaceholder
    copy.position = position
    return copy
}

/**
 * The `ColumnPinning` feature. Adds `columnPinning` state, the column-
 * level pin/getIsPinned/getPinnedIndex accessors, and the row/table
 * partitioning into left, center and right pinned sections.
 */
object ColumnPinning : TableFeature {

    override val getInitialState: ((initialState: InitialTableState?) -> TableState)?
        get() = { state ->
            TableState.fromInitialState(
                state,
                columnPinning = state?.columnPinning ?: getDefaultColumnPinningState(),
            )
        }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>)?
        get() = { table ->
            TableOptionsResolved<Any?>(
                onColumnPinningChange = makeStateUpdater("columnPinning", table),
            )
        }

    override val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = { column, table ->
            column.pin = { position ->
                val columnIds: List<String> = column
                    .getLeafColumns()
                    .map { d -> d.id }
                    .filter { isTruthy(it) }

                table.setColumnPinning { old: ColumnPinningState? ->
                    val oldState = old
                    if (position == "right") {
                        ColumnPinningState(
                            left = (oldState?.left ?: emptyList())
                                .filter { d -> !columnIds.contains(d) },
                            right = (oldState?.right ?: emptyList())
                                .filter { d -> !columnIds.contains(d) } + columnIds,
                        )
                    } else if (position == "left") {
                        ColumnPinningState(
                            left = (oldState?.left ?: emptyList())
                                .filter { d -> !columnIds.contains(d) } + columnIds,
                            right = (oldState?.right ?: emptyList())
                                .filter { d -> !columnIds.contains(d) },
                        )
                    } else {
                        ColumnPinningState(
                            left = (oldState?.left ?: emptyList())
                                .filter { d -> !columnIds.contains(d) },
                            right = (oldState?.right ?: emptyList())
                                .filter { d -> !columnIds.contains(d) },
                        )
                    }
                }
            }

            column.getCanPin = {
                val leafColumns = column.getLeafColumns()

                leafColumns.any { d ->
                    (d.columnDef.enablePinning ?: true) &&
                        (
                            table.options.enableColumnPinning
                                ?: table.options.enablePinning
                                ?: true
                            )
                }
            }

            column.getIsPinned = {
                val leafColumnIds = column.getLeafColumns().map { d -> d.id }

                val columnPinning = table.getState().columnPinning
                val left = columnPinning.left
                val right = columnPinning.right

                val isLeft = leafColumnIds.any { d -> left?.contains(d) == true }
                val isRight = leafColumnIds.any { d -> right?.contains(d) == true }

                if (isLeft) "left" else if (isRight) "right" else false
            }

            column.getPinnedIndex = {
                val position = column.getIsPinned()

                if (isTruthy(position)) {
                    val columnPinning = table.getState().columnPinning
                    val pinnedList = when (position) {
                        "left" -> columnPinning.left
                        "right" -> columnPinning.right
                        else -> null
                    }
                    pinnedList?.indexOf(column.id) ?: -1
                } else {
                    0
                }
            }
        }

    override val createRow: ((row: Row<Any?>, table: Table<Any?>) -> Unit)?
        get() = { row, table ->
            row.getCenterVisibleCells = memo(
                {
                    listOf(
                        row._getAllVisibleCells(),
                        table.getState().columnPinning.left,
                        table.getState().columnPinning.right,
                    )
                },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val allCells = deps[0] as List<Cell<Any?, Any?>>
                    @Suppress("UNCHECKED_CAST")
                    val left = deps[1] as List<String>?
                    @Suppress("UNCHECKED_CAST")
                    val right = deps[2] as List<String>?
                    val leftAndRight: List<String> = (left ?: emptyList()) + (right ?: emptyList())

                    allCells.filter { d -> !leftAndRight.contains(d.column.id) }
                },
                getMemoOptions(table.options, "debugRows", "getCenterVisibleCells"),
            )
            row.getLeftVisibleCells = memo(
                { listOf(row._getAllVisibleCells(), table.getState().columnPinning.left) },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val allCells = deps[0] as List<Cell<Any?, Any?>>
                    @Suppress("UNCHECKED_CAST")
                    val left = deps[1] as List<String>?
                    val cells = (left ?: emptyList())
                        .map { columnId -> allCells.find { cell -> cell.column.id == columnId } }
                        .filter { isTruthy(it) }
                        .map { d -> pinnedCellCopy(d!!, "left") }

                    cells
                },
                getMemoOptions(table.options, "debugRows", "getLeftVisibleCells"),
            )
            row.getRightVisibleCells = memo(
                { listOf(row._getAllVisibleCells(), table.getState().columnPinning.right) },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val allCells = deps[0] as List<Cell<Any?, Any?>>
                    @Suppress("UNCHECKED_CAST")
                    val right = deps[1] as List<String>?
                    val cells = (right ?: emptyList())
                        .map { columnId -> allCells.find { cell -> cell.column.id == columnId } }
                        .filter { isTruthy(it) }
                        .map { d -> pinnedCellCopy(d!!, "right") }

                    cells
                },
                getMemoOptions(table.options, "debugRows", "getRightVisibleCells"),
            )
        }

    override val createTable: ((table: Table<Any?>) -> Unit)?
        get() = { table ->
            table.setColumnPinning = { updater ->
                table.options.onColumnPinningChange?.invoke(updater)
            }

            table.resetColumnPinning = { defaultState ->
                table.setColumnPinning(
                    if (isTruthy(defaultState)) {
                        getDefaultColumnPinningState()
                    } else {
                        table.initialState.columnPinning ?: getDefaultColumnPinningState()
                    },
                )
            }

            table.getIsSomeColumnsPinned = { position ->
                val pinningState = table.getState().columnPinning

                if (!isTruthy(position)) {
                    isTruthy(
                        if (isTruthy(pinningState.left?.size)) {
                            pinningState.left?.size
                        } else {
                            pinningState.right?.size
                        },
                    )
                } else {
                    val list = when (position) {
                        "left" -> pinningState.left
                        "right" -> pinningState.right
                        else -> null
                    }
                    isTruthy(list?.size)
                }
            }

            table.getLeftLeafColumns = memo(
                { listOf(table.getAllLeafColumns(), table.getState().columnPinning.left) },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val allColumns = deps[0] as List<Column<Any?, Any?>>
                    @Suppress("UNCHECKED_CAST")
                    val left = deps[1] as List<String>?
                    (left ?: emptyList())
                        .map { columnId -> allColumns.find { column -> column.id == columnId } }
                        .filter { isTruthy(it) }
                        .map { it!! }
                },
                getMemoOptions(table.options, "debugColumns", "getLeftLeafColumns"),
            )

            table.getRightLeafColumns = memo(
                { listOf(table.getAllLeafColumns(), table.getState().columnPinning.right) },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val allColumns = deps[0] as List<Column<Any?, Any?>>
                    @Suppress("UNCHECKED_CAST")
                    val right = deps[1] as List<String>?
                    (right ?: emptyList())
                        .map { columnId -> allColumns.find { column -> column.id == columnId } }
                        .filter { isTruthy(it) }
                        .map { it!! }
                },
                getMemoOptions(table.options, "debugColumns", "getRightLeafColumns"),
            )

            table.getCenterLeafColumns = memo(
                {
                    listOf(
                        table.getAllLeafColumns(),
                        table.getState().columnPinning.left,
                        table.getState().columnPinning.right,
                    )
                },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val allColumns = deps[0] as List<Column<Any?, Any?>>
                    @Suppress("UNCHECKED_CAST")
                    val left = deps[1] as List<String>?
                    @Suppress("UNCHECKED_CAST")
                    val right = deps[2] as List<String>?
                    val leftAndRight: List<String> = (left ?: emptyList()) + (right ?: emptyList())

                    allColumns.filter { d -> !leftAndRight.contains(d.id) }
                },
                getMemoOptions(table.options, "debugColumns", "getCenterLeafColumns"),
            )
        }
}
