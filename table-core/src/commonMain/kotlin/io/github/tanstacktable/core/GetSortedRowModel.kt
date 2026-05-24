package io.github.tanstacktable.core

/*
 * Sorted row-model builder. Applies each active [ColumnSort] entry in order,
 * stably, and recurses into sub-rows.
 */

/**
 * Per-column sort info captured for one pass of sorting:
 *  - [sortUndefined] mirrors [ColumnDef.sortUndefined]: `false`, `-1`, `1`,
 *    `"first"` or `"last"`.
 *  - [invertSorting] negates the comparator output when `true`.
 *  - [sortingFn] is the resolved comparator for the column.
 */
private class SortColumnInfo<TData>(
    val sortUndefined: Any?,
    val invertSorting: Boolean?,
    val sortingFn: SortingFn<TData>,
)

/**
 * Builds the sorted row-model factory. The returned function is invoked
 * once per [Table] and produces a memoised getter for the sorted
 * [RowModel].
 *
 * When the pre-sorted model is empty, or no sorting is active, the
 * pre-sorted model is returned unchanged.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> getSortedRowModel(): (table: Table<TData>) -> () -> RowModel<TData> {
    return factory@{ table ->
        val memoized: () -> RowModel<TData> = memo<RowModel<TData>>(
            getDeps = { listOf(table.getState().sorting, table.getPreSortedRowModel()) },
            fn = { deps ->
                @Suppress("UNCHECKED_CAST")
                val sorting = deps[0] as SortingState

                @Suppress("UNCHECKED_CAST")
                val rowModel = deps[1] as RowModel<TData>

                if (rowModel.rows.isEmpty() || sorting.isEmpty()) {
                    return@memo rowModel
                }

                val sortingState = table.getState().sorting

                val sortedFlatRows = mutableListOf<Row<TData>>()

                // Filter out sortings that correspond to non-existing or
                // non-sortable columns.
                val availableSorting = sortingState.filter { sort ->
                    table.getColumn(sort.id)?.getCanSort() == true
                }

                val columnInfoById = mutableMapOf<String, SortColumnInfo<TData>>()

                availableSorting.forEach { sortEntry ->
                    val column = table.getColumn(sortEntry.id)
                    if (column == null) return@forEach

                    columnInfoById[sortEntry.id] = SortColumnInfo(
                        sortUndefined = column.columnDef.sortUndefined,
                        invertSorting = column.columnDef.invertSorting,
                        sortingFn = column.getSortingFn(),
                    )
                }

                fun sortData(rows: List<Row<TData>>): List<Row<TData>> {
                    // This will also perform a stable sort using the row index
                    // as the tie-breaker — `MutableList.sortWith` is stable.
                    val sortedData = rows.map { row -> row.shallowCopy() }.toMutableList()

                    fun compareRows(rowA: Row<TData>, rowB: Row<TData>): Int {
                        var i = 0
                        while (i < availableSorting.size) {
                            val sortEntry = availableSorting[i]
                            val columnInfo = columnInfoById[sortEntry.id]!!
                            val sortUndefined = columnInfo.sortUndefined
                            val isDesc = sortEntry.desc

                            var sortInt = 0

                            // All sorting ints should always return in ascending order.
                            // `sortUndefined` is truthy only for `-1`, `1`,
                            // `"first"` or `"last"` — `false` is not.
                            if (isTruthy(sortUndefined)) {
                                val aValue = rowA.getValue(sortEntry.id)
                                val bValue = rowB.getValue(sortEntry.id)

                                val aUndefined = aValue == null
                                val bUndefined = bValue == null

                                if (aUndefined || bUndefined) {
                                    if (sortUndefined == "first") return if (aUndefined) -1 else 1
                                    if (sortUndefined == "last") return if (aUndefined) 1 else -1
                                    // At this point `sortUndefined` is `-1` or `1`.
                                    sortInt = if (aUndefined && bUndefined) {
                                        0
                                    } else if (aUndefined) {
                                        (sortUndefined as Int)
                                    } else {
                                        -(sortUndefined as Int)
                                    }
                                }
                            }

                            if (sortInt == 0) {
                                sortInt = columnInfo.sortingFn(rowA, rowB, sortEntry.id)
                            }

                            // If sorting is non-zero, take care of desc and inversion.
                            if (sortInt != 0) {
                                if (isDesc) {
                                    sortInt *= -1
                                }

                                if (columnInfo.invertSorting == true) {
                                    sortInt *= -1
                                }

                                return sortInt
                            }

                            i += 1
                        }

                        return rowA.index - rowB.index
                    }

                    sortedData.sortWith(::compareRows)

                    // If there are sub-rows, sort them too.
                    sortedData.forEach { row ->
                        sortedFlatRows.add(row)
                        if (row.subRows.isNotEmpty()) {
                            row.subRows = sortData(row.subRows)
                        }
                    }

                    return sortedData
                }

                RowModel(
                    rows = sortData(rowModel.rows),
                    flatRows = sortedFlatRows,
                    rowsById = rowModel.rowsById,
                )
            },
            opts = getMemoOptions(
                table.options,
                "debugTable",
                "getSortedRowModel",
            ) { table._autoResetPageIndex() },
        )
                return@factory { memoized() }
    }
}
