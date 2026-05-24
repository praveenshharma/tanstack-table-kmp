package io.github.tanstacktable.core

/*
 * Shared row-tree filtering helpers used by the various filtered-row-model
 * builders. Recurses into sub-rows up to `maxLeafRowFilterDepth`.
 */

/**
 * Filters [rows] through [filterRowImpl], producing a new [RowModel].
 *
 * When `table.options.filterFromLeafRows` is `true`, rows are evaluated
 * leaf-first — a parent row is kept when any descendant passes (in
 * addition to itself passing). Otherwise rows are evaluated root-first and
 * a failing parent prunes its entire sub-tree.
 *
 * [filterRowImpl] returns `Any?` and is consumed via [isTruthy] at each
 * call site.
 */
fun <TData> filterRows(
    rows: List<Row<TData>>,
    filterRowImpl: (row: Row<TData>) -> Any?,
    table: Table<TData>,
): RowModel<TData> {
    if (table.options.filterFromLeafRows == true) {
        return filterRowModelFromLeafs(rows, filterRowImpl, table)
    }

    return filterRowModelFromRoot(rows, filterRowImpl, table)
}

/**
 * Leaf-first filtering: a row is kept when it passes the filter or when
 * any of its descendants does. Recurses to a maximum depth of
 * `table.options.maxLeafRowFilterDepth` (default `100`).
 */
private fun <TData> filterRowModelFromLeafs(
    rowsToFilter: List<Row<TData>>,
    filterRow: (row: Row<TData>) -> Any?,
    table: Table<TData>,
): RowModel<TData> {
    val newFilteredFlatRows = mutableListOf<Row<TData>>()
    val newFilteredRowsById = mutableMapOf<String, Row<TData>>()
    val maxDepth = table.options.maxLeafRowFilterDepth ?: 100

    fun recurseFilterRows(rowsToFilter: List<Row<TData>>, depth: Int = 0): List<Row<TData>> {
        val rows = mutableListOf<Row<TData>>()

        // Filter from children up first.
        for (i in rowsToFilter.indices) {
            var row = rowsToFilter[i]

            val newRow = createRow(
                table,
                row.id,
                row.original,
                row.index,
                row.depth,
                null,
                row.parentId,
            )
            newRow.columnFilters = row.columnFilters

            val subRows = row.subRows
            if (subRows.isNotEmpty() && depth < maxDepth) {
                newRow.subRows = recurseFilterRows(subRows, depth + 1)
                row = newRow

                if (isTruthy(filterRow(row)) && newRow.subRows.isEmpty()) {
                    rows.add(row)
                    newFilteredRowsById[row.id] = row
                    newFilteredFlatRows.add(row)
                    continue
                }

                if (isTruthy(filterRow(row)) || newRow.subRows.isNotEmpty()) {
                    rows.add(row)
                    newFilteredRowsById[row.id] = row
                    newFilteredFlatRows.add(row)
                    continue
                }
            } else {
                row = newRow
                if (isTruthy(filterRow(row))) {
                    rows.add(row)
                    newFilteredRowsById[row.id] = row
                    newFilteredFlatRows.add(row)
                }
            }
        }

        return rows
    }

    return RowModel(
        rows = recurseFilterRows(rowsToFilter),
        flatRows = newFilteredFlatRows,
        rowsById = newFilteredRowsById,
    )
}

/**
 * Root-first filtering: a failing parent prunes its entire sub-tree.
 * Recurses to a maximum depth of `table.options.maxLeafRowFilterDepth`
 * (default `100`).
 */
private fun <TData> filterRowModelFromRoot(
    rowsToFilter: List<Row<TData>>,
    filterRow: (row: Row<TData>) -> Any?,
    table: Table<TData>,
): RowModel<TData> {
    val newFilteredFlatRows = mutableListOf<Row<TData>>()
    val newFilteredRowsById = mutableMapOf<String, Row<TData>>()
    val maxDepth = table.options.maxLeafRowFilterDepth ?: 100

    // Filters top-level and nested rows.
    fun recurseFilterRows(rowsToFilter: List<Row<TData>>, depth: Int = 0): List<Row<TData>> {
        // Filter from parents downward first.

        val rows = mutableListOf<Row<TData>>()

        // Apply the filter to any sub-rows.
        for (i in rowsToFilter.indices) {
            var row = rowsToFilter[i]

            val pass = filterRow(row)

            if (isTruthy(pass)) {
                val subRows = row.subRows
                if (subRows.isNotEmpty() && depth < maxDepth) {
                    val newRow = createRow(
                        table,
                        row.id,
                        row.original,
                        row.index,
                        row.depth,
                        null,
                        row.parentId,
                    )
                    newRow.subRows = recurseFilterRows(subRows, depth + 1)
                    row = newRow
                }

                rows.add(row)
                newFilteredFlatRows.add(row)
                newFilteredRowsById[row.id] = row
            }
        }

        return rows
    }

    return RowModel(
        rows = recurseFilterRows(rowsToFilter),
        flatRows = newFilteredFlatRows,
        rowsById = newFilteredRowsById,
    )
}
