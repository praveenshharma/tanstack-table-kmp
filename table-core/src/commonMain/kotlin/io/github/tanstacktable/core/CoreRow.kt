package io.github.tanstacktable.core

/*
 * Row construction. The `Row` class lives in Row.kt; this file holds
 * `createRow`, which assembles a row, wires its value/cell accessors and
 * runs each installed feature's `createRow` hook.
 */

/**
 * Constructs a [Row] for the row at [rowIndex] in the data array, then
 * runs every registered feature's `createRow` hook so features can attach
 * their own members.
 *
 *  - [id] is the row id resolved by `_getRowId`.
 *  - [original] is the source data record.
 *  - [depth] is the nesting depth (0 for top-level rows).
 *  - [subRows] is the already-constructed child rows when known; otherwise
 *    pass `null` to start with an empty list.
 *  - [parentId] is the parent row's id when this row is a sub-row.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> createRow(
    table: Table<TData>,
    id: String,
    original: TData,
    rowIndex: Int,
    depth: Int,
    subRows: List<Row<TData>>?,
    parentId: String?,
): Row<TData> {
    // Declared first so the member closures and memoised deps can capture it.
    val row = Row<TData>()

    row.id = id
    row.index = rowIndex
    row.original = original
    row.depth = depth
    row.parentId = parentId
    row._valuesCache = mutableMapOf()
    row._uniqueValuesCache = mutableMapOf()

    row.getValue = getValue@{ columnId ->
        if (row._valuesCache.containsKey(columnId)) {
            return@getValue row._valuesCache[columnId]
        }

        val column = table.getColumn(columnId)

        if (column?.accessorFn == null) {
            return@getValue null
        }

        row._valuesCache[columnId] = column.accessorFn!!(row.original, rowIndex)

        row._valuesCache[columnId]
    }

    row.getUniqueValues = getUniqueValues@{ columnId ->
        // `getUniqueValues` is typed as returning `List<Any?>` on [Row]. The
        // no-accessor path needs to surface "no values" and is bridged here
        // with `null as List<Any?>` to keep the lambda assignable.

        if (row._uniqueValuesCache.containsKey(columnId)) {
            return@getUniqueValues row._uniqueValuesCache[columnId] as List<Any?>
        }

        val column = table.getColumn(columnId)

        if (column?.accessorFn == null) {
            return@getUniqueValues null as List<Any?>
        }

        if (column.columnDef.getUniqueValues == null) {
            row._uniqueValuesCache[columnId] = listOf(row.getValue(columnId))
            return@getUniqueValues row._uniqueValuesCache[columnId] as List<Any?>
        }

        row._uniqueValuesCache[columnId] =
            column.columnDef.getUniqueValues!!(row.original, rowIndex)

        row._uniqueValuesCache[columnId] as List<Any?>
    }

    row.renderValue = { columnId ->
        row.getValue(columnId) ?: table.options.renderFallbackValue
    }

    row.subRows = subRows ?: emptyList()

    row.getLeafRows = { flattenBy(row.subRows) { d -> d.subRows } }

    // `parentId` is treated as absent when null or empty (mirroring a falsy
    // string check).
    row.getParentRow = {
        val parentId = row.parentId
        if (parentId != null && parentId.isNotEmpty()) {
            table.getRow(parentId, true)
        } else {
            null
        }
    }

    row.getParentRows = {
        val parentRows = mutableListOf<Row<TData>>()
        var currentRow = row
        while (true) {
            val parentRow = currentRow.getParentRow()
            if (parentRow == null) break
            parentRows.add(parentRow)
            currentRow = parentRow
        }
        // Use `reversed()` for a fresh reversed copy so the just-built list
        // is not mutated afterwards.
        parentRows.reversed()
    }

    row.getAllCells = memo(
        getDeps = { listOf(table.getAllLeafColumns()) },
        fn = { deps ->
            val leafColumns = deps[0] as List<Column<TData, Any?>>
            leafColumns.map { column ->
                createCell(table, row, column, column.id)
            }
        },
        opts = getMemoOptions(table.options, "debugRows", "getAllCells"),
    )

    row._getAllCellsByColumnId = memo(
        getDeps = { listOf(row.getAllCells()) },
        fn = { deps ->
            val allCells = deps[0] as List<Cell<TData, Any?>>
            allCells.fold(mutableMapOf<String, Cell<TData, Any?>>()) { acc, cell ->
                acc[cell.column.id] = cell
                acc
            }
        },
        opts = getMemoOptions(table.options, "debugRows", "getAllCellsByColumnId"),
    )

    for (i in table._features.indices) {
        val feature = table._features[i]
        feature.createRow?.invoke(row as Row<Any?>, table as Table<Any?>)
    }

    return row
}
