package io.github.tanstacktable.core

/*
 * Row-selection feature. Tracks per-row selection flags, the row-level
 * selection accessors and the table-level toggle/getIs* helpers.
 */

/**
 * Per-row selection flags. An absent entry means "unselected".
 */
typealias RowSelectionState = Map<String, Boolean>

/**
 * The `RowSelection` feature. Adds `rowSelection` state, the row-level
 * accessors (`getIsSelected`, `toggleSelected`, ...) and the table-level
 * `setRowSelection` / `toggleAllRowsSelected` / `getIsAllRowsSelected` /
 * `getSelectedRowModel`.
 */
@Suppress("UNCHECKED_CAST")
object RowSelection : TableFeature {

    override val getInitialState: ((initialState: InitialTableState?) -> TableState) = { state ->
        TableState.fromInitialState(
            state,
            rowSelection = state?.rowSelection ?: emptyMap<String, Boolean>(),
        )
    }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>) = { table ->
        TableOptionsResolved<Any?>().also {
            it.onRowSelectionChange = makeStateUpdater("rowSelection", table)
            it.enableRowSelection = true
            it.enableMultiRowSelection = true
            it.enableSubRowSelection = true
            // enableGroupingRowSelection: false,
            // isAdditiveSelectEvent: (e: unknown) => !!e.metaKey,
            // isInclusiveSelectEvent: (e: unknown) => !!e.shiftKey,
        }
    }

    override val createTable: ((table: Table<Any?>) -> Unit) = { table ->
        table.setRowSelection = { updater ->
            table.options.onRowSelectionChange?.invoke(updater)
        }
        table.resetRowSelection = { defaultState: Boolean? ->
            table.setRowSelection(
                if (defaultState == true) emptyMap<String, Boolean>() else table.initialState.rowSelection,
            )
        }
        table.toggleAllRowsSelected = { value: Boolean? ->
            table.setRowSelection { oldAny: Any? ->
                val old = oldAny as RowSelectionState

                val resolvedValue: Boolean = if (value != null) value else !table.getIsAllRowsSelected()

                val rowSelection: MutableMap<String, Boolean> = old.toMutableMap()

                val preGroupedFlatRows = table.getPreGroupedRowModel().flatRows

                // We don't use `mutateRowIsSelected` here for performance reasons.
                // All of the rows are flat already, so it wouldn't be worth it
                if (resolvedValue) {
                    preGroupedFlatRows.forEach forEach@{ row ->
                        if (!row.getCanSelect()) {
                            return@forEach
                        }
                        rowSelection[row.id] = true
                    }
                } else {
                    preGroupedFlatRows.forEach { row ->
                        rowSelection.remove(row.id)
                    }
                }

                rowSelection
            }
        }
        table.toggleAllPageRowsSelected = { value: Boolean? ->
            table.setRowSelection { oldAny: Any? ->
                val old = oldAny as RowSelectionState

                val resolvedValue: Boolean =
                    if (value != null) value else !table.getIsAllPageRowsSelected()

                val rowSelection: MutableMap<String, Boolean> = old.toMutableMap()

                table.getRowModel().rows.forEach { row ->
                    mutateRowIsSelected(rowSelection, row.id, resolvedValue, true, table)
                }

                rowSelection
            }
        }

        // addRowSelectionRange: rowId => {
        //   const {
        //     rows,
        //     rowsById,
        //     options: { selectGroupingRows, selectSubRows },
        //   } = table

        //   const findSelectedRow = (rows: Row[]) => {
        //     let found
        //     rows.find(d => {
        //       if (d.getIsSelected()) {
        //         found = d
        //         return true
        //       }
        //       const subFound = findSelectedRow(d.subRows || [])
        //       if (subFound) {
        //         found = subFound
        //         return true
        //       }
        //       return false
        //     })
        //     return found
        //   }

        //   const firstRow = findSelectedRow(rows) || rows[0]
        //   const lastRow = rowsById[rowId]

        //   let include = false
        //   const selectedRowIds = {}

        //   const addRow = (row: Row) => {
        //     mutateRowIsSelected(selectedRowIds, row.id, true, {
        //       rowsById,
        //       selectGroupingRows: selectGroupingRows!,
        //       selectSubRows: selectSubRows!,
        //     })
        //   }

        //   table.rows.forEach(row => {
        //     const isFirstRow = row.id === firstRow.id
        //     const isLastRow = row.id === lastRow.id

        //     if (isFirstRow || isLastRow) {
        //       if (!include) {
        //         include = true
        //       } else if (include) {
        //         addRow(row)
        //         include = false
        //       }
        //     }

        //     if (include) {
        //       addRow(row)
        //     }
        //   })

        //   table.setRowSelection(selectedRowIds)
        // },
        table.getPreSelectedRowModel = { table.getCoreRowModel() }
        table.getSelectedRowModel = memo(
            getDeps = { listOf(table.getState().rowSelection, table.getCoreRowModel()) },
            fn = { deps ->
                val rowSelection = deps[0] as RowSelectionState
                val rowModel = deps[1] as RowModel<Any?>
                if (rowSelection.keys.isEmpty()) {
                    RowModel<Any?>(
                        rows = emptyList(),
                        flatRows = emptyList(),
                        rowsById = emptyMap(),
                    )
                } else {
                    selectRowsFn(table, rowModel)
                }
            },
            opts = getMemoOptions(table.options, "debugTable", "getSelectedRowModel"),
        )

        table.getFilteredSelectedRowModel = memo(
            getDeps = { listOf(table.getState().rowSelection, table.getFilteredRowModel()) },
            fn = { deps ->
                val rowSelection = deps[0] as RowSelectionState
                val rowModel = deps[1] as RowModel<Any?>
                if (rowSelection.keys.isEmpty()) {
                    RowModel<Any?>(
                        rows = emptyList(),
                        flatRows = emptyList(),
                        rowsById = emptyMap(),
                    )
                } else {
                    selectRowsFn(table, rowModel)
                }
            },
            opts = getMemoOptions(table.options, "debugTable", "getFilteredSelectedRowModel"),
        )

        table.getGroupedSelectedRowModel = memo(
            getDeps = { listOf(table.getState().rowSelection, table.getSortedRowModel()) },
            fn = { deps ->
                val rowSelection = deps[0] as RowSelectionState
                val rowModel = deps[1] as RowModel<Any?>
                if (rowSelection.keys.isEmpty()) {
                    RowModel<Any?>(
                        rows = emptyList(),
                        flatRows = emptyList(),
                        rowsById = emptyMap(),
                    )
                } else {
                    selectRowsFn(table, rowModel)
                }
            },
            opts = getMemoOptions(table.options, "debugTable", "getGroupedSelectedRowModel"),
        )

        ///

        // getGroupingRowCanSelect: rowId => {
        //   const row = table.getRow(rowId)

        //   if (!row) {
        //     throw new Error()
        //   }

        //   if (typeof table.options.enableGroupingRowSelection === 'function') {
        //     return table.options.enableGroupingRowSelection(row)
        //   }

        //   return table.options.enableGroupingRowSelection ?? false
        // },

        table.getIsAllRowsSelected = {
            val preGroupedFlatRows = table.getFilteredRowModel().flatRows
            val rowSelection = table.getState().rowSelection

            var isAllRowsSelected: Boolean =
                preGroupedFlatRows.isNotEmpty() && rowSelection.keys.isNotEmpty()

            if (isAllRowsSelected) {
                if (
                    preGroupedFlatRows.any { row ->
                        row.getCanSelect() && !isTruthy(rowSelection[row.id])
                    }
                ) {
                    isAllRowsSelected = false
                }
            }

            isAllRowsSelected
        }

        table.getIsAllPageRowsSelected = {
            val paginationFlatRows = table
                .getPaginationRowModel()
                .flatRows.filter { row -> row.getCanSelect() }
            val rowSelection = table.getState().rowSelection

            var isAllPageRowsSelected: Boolean = paginationFlatRows.isNotEmpty()

            if (
                isAllPageRowsSelected &&
                paginationFlatRows.any { row -> !isTruthy(rowSelection[row.id]) }
            ) {
                isAllPageRowsSelected = false
            }

            isAllPageRowsSelected
        }

        table.getIsSomeRowsSelected = {
            val totalSelected = (table.getState().rowSelection ?: emptyMap<String, Boolean>()).keys.size
            totalSelected > 0 &&
                totalSelected < table.getFilteredRowModel().flatRows.size
        }

        table.getIsSomePageRowsSelected = {
            val paginationFlatRows = table.getPaginationRowModel().flatRows
            if (table.getIsAllPageRowsSelected()) {
                false
            } else {
                paginationFlatRows
                    .filter { row -> row.getCanSelect() }
                    .any { d -> d.getIsSelected() || d.getIsSomeSelected() }
            }
        }

        table.getToggleAllRowsSelectedHandler = {
            { e: Any? ->
                table.toggleAllRowsSelected(null)
            }
        }

        table.getToggleAllPageRowsSelectedHandler = {
            { e: Any? ->
                table.toggleAllPageRowsSelected(null)
            }
        }
    }

    override val createRow: ((row: Row<Any?>, table: Table<Any?>) -> Unit) = { row, table ->
        row.toggleSelected = { value: Boolean?, opts: RowSelectionToggleOpts? ->
            val isSelected = row.getIsSelected()

            table.setRowSelection { oldAny: Any? ->
                val old = oldAny as RowSelectionState

                val resolvedValue: Boolean = if (value != null) value else !isSelected

                if (row.getCanSelect() && isSelected == resolvedValue) {
                    return@setRowSelection old
                }

                val selectedRowIds: MutableMap<String, Boolean> = old.toMutableMap()

                mutateRowIsSelected(
                    selectedRowIds,
                    row.id,
                    resolvedValue,
                    opts?.selectChildren ?: true,
                    table,
                )

                selectedRowIds
            }
        }
        row.getIsSelected = {
            val rowSelection = table.getState().rowSelection
            isRowSelected(row, rowSelection)
        }

        row.getIsSomeSelected = {
            val rowSelection = table.getState().rowSelection
            isSubRowSelected(row, rowSelection, table) == "some"
        }

        row.getIsAllSubRowsSelected = {
            val rowSelection = table.getState().rowSelection
            isSubRowSelected(row, rowSelection, table) == "all"
        }

        row.getCanSelect = {
            if (isFunction(table.options.enableRowSelection)) {
                (table.options.enableRowSelection as (Row<Any?>) -> Boolean)(row)
            } else {
                (table.options.enableRowSelection as Boolean?) ?: true
            }
        }

        row.getCanSelectSubRows = {
            if (isFunction(table.options.enableSubRowSelection)) {
                (table.options.enableSubRowSelection as (Row<Any?>) -> Boolean)(row)
            } else {
                (table.options.enableSubRowSelection as Boolean?) ?: true
            }
        }

        row.getCanMultiSelect = {
            if (isFunction(table.options.enableMultiRowSelection)) {
                (table.options.enableMultiRowSelection as (Row<Any?>) -> Boolean)(row)
            } else {
                (table.options.enableMultiRowSelection as Boolean?) ?: true
            }
        }
        row.getToggleSelectedHandler = {
            val canSelect = row.getCanSelect()

            val handler: (Any?) -> Unit = handler@{ e: Any? ->
                if (!canSelect) return@handler
                row.toggleSelected(null, null)
            }
            handler
        }
    }
}

private fun <TData> mutateRowIsSelected(
    selectedRowIds: MutableMap<String, Boolean>,
    id: String,
    value: Boolean,
    includeChildren: Boolean,
    table: Table<TData>,
) {
    val row = table.getRow(id, true)

    // const isGrouped = row.getIsGrouped()

    // if ( // TODO: enforce grouping row selection rules
    //   !isGrouped ||
    //   (isGrouped && table.options.enableGroupingRowSelection)
    // ) {
    if (value) {
        if (!row.getCanMultiSelect()) {
            selectedRowIds.keys.toList().forEach { key -> selectedRowIds.remove(key) }
        }
        if (row.getCanSelect()) {
            selectedRowIds[id] = true
        }
    } else {
        selectedRowIds.remove(id)
    }
    // }

    if (includeChildren && row.subRows.isNotEmpty() && row.getCanSelectSubRows()) {
        row.subRows.forEach { subRow ->
            mutateRowIsSelected(selectedRowIds, subRow.id, value, includeChildren, table)
        }
    }
}

fun <TData> selectRowsFn(
    table: Table<TData>,
    rowModel: RowModel<TData>,
): RowModel<TData> {
    val rowSelection = table.getState().rowSelection

    val newSelectedFlatRows: MutableList<Row<TData>> = mutableListOf()
    val newSelectedRowsById: MutableMap<String, Row<TData>> = mutableMapOf()

    // Filters top level and nested rows
    fun recurseRows(rows: List<Row<TData>>, depth: Int = 0): List<Row<TData>> {
        return rows
            .map { rowParam ->
                var row = rowParam
                val isSelected = isRowSelected(row, rowSelection)

                if (isSelected) {
                    newSelectedFlatRows.add(row)
                    newSelectedRowsById[row.id] = row
                }

                if (row.subRows.isNotEmpty()) {
                    row = row.shallowCopy().also {
                        it.subRows = recurseRows(row.subRows, depth + 1)
                    }
                }

                if (isSelected) {
                    row
                } else {
                    null
                }
            }
            .filterNotNull()
    }

    return RowModel(
        rows = recurseRows(rowModel.rows),
        flatRows = newSelectedFlatRows,
        rowsById = newSelectedRowsById,
    )
}

fun <TData> isRowSelected(
    row: Row<TData>,
    selection: Map<String, Boolean>,
): Boolean {
    return selection[row.id] ?: false
}

fun <TData> isSubRowSelected(
    row: Row<TData>,
    selection: Map<String, Boolean>,
    table: Table<TData>,
): Any? {
    if (row.subRows.isEmpty()) return false

    var allChildrenSelected = true
    var someSelected = false

    row.subRows.forEach forEach@{ subRow ->
        // Bail out early if we know both of these
        if (someSelected && !allChildrenSelected) {
            return@forEach
        }

        if (subRow.getCanSelect()) {
            if (isRowSelected(subRow, selection)) {
                someSelected = true
            } else {
                allChildrenSelected = false
            }
        }

        // Check row selection of nested subrows
        if (subRow.subRows.isNotEmpty()) {
            val subRowChildrenSelected = isSubRowSelected(subRow, selection, table)
            if (subRowChildrenSelected == "all") {
                someSelected = true
            } else if (subRowChildrenSelected == "some") {
                someSelected = true
                allChildrenSelected = false
            } else {
                allChildrenSelected = false
            }
        }
    }

    return if (allChildrenSelected) "all" else if (someSelected) "some" else false
}
