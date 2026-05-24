package io.github.tanstacktable.core

/*
 * Column-ordering feature. Tracks a `columnOrder` list and exposes the
 * column-level index/first/last accessors that respect it.
 */

/**
 * Ordered list of column ids that drive table column order. Columns not
 * mentioned here retain their natural order.
 */
typealias ColumnOrderState = List<String>

/**
 * The `ColumnOrdering` feature. Adds `columnOrder` state, the table-level
 * setter/reset, the per-column `getIndex`/`getIsFirstColumn`/
 * `getIsLastColumn` accessors, and the ordering function used to build
 * leaf column lists.
 */
object ColumnOrdering : TableFeature {

    override val getInitialState: ((initialState: InitialTableState?) -> TableState)?
        get() = { state ->
            TableState.fromInitialState(
                state,
                columnOrder = state?.columnOrder ?: emptyList(),
            )
        }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>)?
        get() = { table ->
            TableOptionsResolved<Any?>(
                onColumnOrderChange = makeStateUpdater("columnOrder", table),
            )
        }

    override val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = { column, table ->
            column.getIndex = memoWithArg(
                // Memoised by `position` because the visible leaf columns
                // depend on it.
                { position: Any? -> listOf(_getVisibleLeafColumns(table, position)) },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val columns = deps[0] as List<Column<Any?, Any?>>
                    columns.indexOfFirst { d -> d.id == column.id }
                },
                getMemoOptions(table.options, "debugColumns", "getIndex"),
            )
            column.getIsFirstColumn = { position ->
                val columns = _getVisibleLeafColumns(table, position)
                columns.getOrNull(0)?.id == column.id
            }
            column.getIsLastColumn = { position ->
                val columns = _getVisibleLeafColumns(table, position)
                columns.getOrNull(columns.size - 1)?.id == column.id
            }
        }

    override val createTable: ((table: Table<Any?>) -> Unit)?
        get() = { table ->
            table.setColumnOrder = { updater ->
                table.options.onColumnOrderChange?.invoke(updater)
            }
            table.resetColumnOrder = { defaultState ->
                table.setColumnOrder(
                    if (isTruthy(defaultState)) {
                        emptyList<String>()
                    } else {
                        table.initialState.columnOrder ?: emptyList()
                    },
                )
            }
            table._getOrderColumnsFn = memo(
                {
                    listOf(
                        table.getState().columnOrder,
                        table.getState().grouping,
                        table.options.groupedColumnMode,
                    )
                },
                { deps ->
                    @Suppress("UNCHECKED_CAST")
                    val columnOrder = deps[0] as ColumnOrderState?
                    @Suppress("UNCHECKED_CAST")
                    val grouping = deps[1] as GroupingState
                    val groupedColumnMode = deps[2]
                    // Returns a closure that orders a list of columns by the
                    // current `columnOrder` / grouping state. The closure
                    // itself is recomputed only when the deps change.
                    val orderingFn: (List<Column<Any?, Any?>>) -> List<Column<Any?, Any?>> =
                        fn@{ columns ->
                            // Sort grouped columns to the start of the column
                            // list before the headers are built.
                            var orderedColumns: List<Column<Any?, Any?>> = emptyList()

                            // If there is no order, return the normal columns.
                            if (columnOrder == null || columnOrder.isEmpty()) {
                                orderedColumns = columns
                            } else {
                                // Mutable copies so we can drain them while iterating.
                                val columnOrderCopy = columnOrder.toMutableList()

                                // If there is an order, make a copy of the columns.
                                val columnsCopy = columns.toMutableList()

                                // Build a new ordered array of the columns.
                                val orderedColumnsAcc = mutableListOf<Column<Any?, Any?>>()

                                // Loop over the columns and place them in order
                                // into the new array.
                                while (columnsCopy.isNotEmpty() && columnOrderCopy.isNotEmpty()) {
                                    val targetColumnId = columnOrderCopy.removeAt(0)
                                    val foundIndex = columnsCopy.indexOfFirst { d ->
                                        d.id == targetColumnId
                                    }
                                    if (foundIndex > -1) {
                                        orderedColumnsAcc.add(columnsCopy.removeAt(foundIndex))
                                    }
                                }

                                // If there are any columns left, add them to the end.
                                orderedColumns = orderedColumnsAcc + columnsCopy
                            }

                            orderColumns(orderedColumns, grouping, groupedColumnMode)
                        }
                    orderingFn
                },
                getMemoOptions(table.options, "debugTable", "_getOrderColumnsFn"),
            )
        }
}
