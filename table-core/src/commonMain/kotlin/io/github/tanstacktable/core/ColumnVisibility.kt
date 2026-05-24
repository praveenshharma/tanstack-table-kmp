package io.github.tanstacktable.core

/*
 * Column-visibility feature. Tracks per-column visibility flags and the
 * derived visible-cell/visible-column accessors.
 */

/**
 * Per-column visibility flags. An absent entry means "visible".
 */
typealias VisibilityState = Map<String, Boolean>

/**
 * The `ColumnVisibility` feature. Adds `columnVisibility` state plus the
 * column-, row- and table-level visibility members.
 */
object ColumnVisibility : TableFeature {

    override val getInitialState: ((initialState: InitialTableState?) -> TableState)?
        get() = { state ->
            TableState.fromInitialState(
                state,
                columnVisibility = state?.columnVisibility ?: emptyMap(),
            )
        }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>)?
        get() = { table ->
            TableOptionsResolved<Any?>(
                onColumnVisibilityChange = makeStateUpdater("columnVisibility", table),
            )
        }

    override val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = { column, table ->
            column.toggleVisibility = { value ->
                if (column.getCanHide()) {
                    table.setColumnVisibility { old: VisibilityState ->
                        val oldMap = old
                        oldMap + (column.id to (value ?: !column.getIsVisible()))
                    }
                }
            }
            column.getIsVisible = {
                val childColumns = column.columns
                // A parent column is visible when any of its children is;
                // a leaf consults its own entry in the visibility map and
                // defaults to true when missing.
                (if (childColumns.isNotEmpty()) {
                    childColumns.any { c -> c.getIsVisible() }
                } else {
                    table.getState().columnVisibility[column.id]
                }) ?: true
            }

            column.getCanHide = {
                (column.columnDef.enableHiding ?: true) &&
                    (table.options.enableHiding ?: true)
            }
            column.getToggleVisibilityHandler = {
                { e: Any? ->
                    // The original reads the checkbox's `checked` property off
                    // a browser MouseEvent target — not reachable from
                    // commonMain. Platform adapters should pass the desired
                    // boolean directly.
                    column.toggleVisibility?.invoke(e as? Boolean)
                }
            }
        }

    override val createRow: ((row: Row<Any?>, table: Table<Any?>) -> Unit)?
        get() = { row, table ->
            row._getAllVisibleCells = memo(
                { listOf(row.getAllCells(), table.getState().columnVisibility) },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val cells = deps[0] as List<Cell<Any?, Any?>>
                    cells.filter { cell -> cell.column.getIsVisible() }
                },
                getMemoOptions(table.options, "debugRows", "_getAllVisibleCells"),
            )
            row.getVisibleCells = memo(
                {
                    listOf(
                        row.getLeftVisibleCells(),
                        row.getCenterVisibleCells(),
                        row.getRightVisibleCells(),
                    )
                },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val left = deps[0] as List<Cell<Any?, Any?>>
                    @Suppress("UNCHECKED_CAST")
                    val center = deps[1] as List<Cell<Any?, Any?>>
                    @Suppress("UNCHECKED_CAST")
                    val right = deps[2] as List<Cell<Any?, Any?>>
                    left + center + right
                },
                getMemoOptions(table.options, "debugRows", "getVisibleCells"),
            )
        }

    override val createTable: ((table: Table<Any?>) -> Unit)?
        get() = { table ->
            fun makeVisibleColumnsMethod(
                key: String,
                getColumns: () -> List<Column<Any?, Any?>>,
            ): () -> List<Column<Any?, Any?>> {
                return memo(
                    {
                        listOf(
                            getColumns(),
                            // String-keyed dep so the memo invalidates when the
                            // visible-column set changes shape.
                            getColumns()
                                .filter { d -> d.getIsVisible() }
                                .map { d -> d.id }
                                .joinToString("_"),
                        )
                    },
                    { deps ->
                        @Suppress("UNCHECKED_CAST")
                        val columns = deps[0] as List<Column<Any?, Any?>>
                        columns.filter { d -> d.getIsVisible() }
                    },
                    getMemoOptions(table.options, "debugColumns", key),
                )
            }

            table.getVisibleFlatColumns = makeVisibleColumnsMethod(
                "getVisibleFlatColumns",
            ) { table.getAllFlatColumns() }
            table.getVisibleLeafColumns = makeVisibleColumnsMethod(
                "getVisibleLeafColumns",
            ) { table.getAllLeafColumns() }
            table.getLeftVisibleLeafColumns = makeVisibleColumnsMethod(
                "getLeftVisibleLeafColumns",
            ) { table.getLeftLeafColumns() }
            table.getRightVisibleLeafColumns = makeVisibleColumnsMethod(
                "getRightVisibleLeafColumns",
            ) { table.getRightLeafColumns() }
            table.getCenterVisibleLeafColumns = makeVisibleColumnsMethod(
                "getCenterVisibleLeafColumns",
            ) { table.getCenterLeafColumns() }

            table.setColumnVisibility = { updater ->
                table.options.onColumnVisibilityChange?.invoke(updater)
            }

            table.resetColumnVisibility = { defaultState ->
                table.setColumnVisibility(
                    if (isTruthy(defaultState)) {
                        emptyMap<String, Boolean>()
                    } else {
                        table.initialState.columnVisibility ?: emptyMap()
                    },
                )
            }

            table.toggleAllColumnsVisible = { valueParam ->
                val value = valueParam ?: !table.getIsAllColumnsVisible()

                table.setColumnVisibility(
                    table.getAllLeafColumns().fold(emptyMap<String, Boolean>()) { obj, column ->
                        obj + (column.id to (if (!value) !column.getCanHide() else value))
                    },
                )
            }

            table.getIsAllColumnsVisible = {
                !table.getAllLeafColumns().any { column -> !column.getIsVisible() }
            }

            table.getIsSomeColumnsVisible = {
                table.getAllLeafColumns().any { column -> column.getIsVisible() }
            }

            table.getToggleAllColumnsVisibilityHandler = {
                { e: Any? ->
                    // See `getToggleVisibilityHandler` above — the browser
                    // event-target read is unreachable from commonMain.
                    table.toggleAllColumnsVisible(e as? Boolean)
                }
            }
        }
}

/**
 * Returns the visible leaf columns for [position], where `position` is
 * `null` / `false` (all visible columns), `"center"`, `"left"` or
 * `"right"`. Modelled with `Any?` because the original union mixes the
 * literal `false` with string literals.
 */
fun _getVisibleLeafColumns(
    table: Table<Any?>,
    position: Any? = null,
): List<Column<Any?, Any?>> {
    return if (!isTruthy(position)) {
        table.getVisibleLeafColumns()
    } else if (position == "center") {
        table.getCenterVisibleLeafColumns()
    } else if (position == "left") {
        table.getLeftVisibleLeafColumns()
    } else {
        table.getRightVisibleLeafColumns()
    }
}
