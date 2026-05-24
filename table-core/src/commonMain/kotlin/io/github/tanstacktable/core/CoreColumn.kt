package io.github.tanstacktable.core

/*
 * Column construction. The `Column` class lives in Column.kt; this file
 * holds `createColumn`, which resolves the column def against the table's
 * defaults, derives the column id and accessor function, builds the
 * memoised tree accessors and runs each installed feature's `createColumn`
 * hook.
 */

/**
 * Constructs a [Column] from [columnDef]. Resolves the def against the
 * table's default column def (per-field merge — only non-null fields on
 * [columnDef] override the defaults), derives an id from
 * `id`/`accessorKey`/string `header`, builds an accessor function for
 * accessor-key/-fn columns, installs the memoised `getFlatColumns` and
 * `getLeafColumns`, and runs every registered feature's `createColumn` hook.
 *
 * Throws [IllegalStateException] when no id can be derived.
 */
@Suppress("UNCHECKED_CAST")
fun <TData, TValue> createColumn(
    table: Table<TData>,
    columnDef: ColumnDef<TData, TValue>,
    depth: Int,
    parent: Column<TData, TValue>?,
): Column<TData, TValue> {
    val defaultColumn = table._getDefaultColumnDef()

    // Per-field merge of `defaultColumn` and `columnDef`. The first pass
    // copies every default; the second overwrites with each non-null field
    // from `columnDef` — an unconditional copy would clobber inherited
    // defaults with the caller's nulls.
    val resolvedColumnDef = ColumnDef<TData, Any?>()
    // ...defaultColumn
    resolvedColumnDef.getUniqueValues = defaultColumn.getUniqueValues
    resolvedColumnDef.footer = defaultColumn.footer
    resolvedColumnDef.cell = defaultColumn.cell
    resolvedColumnDef.meta = defaultColumn.meta
    resolvedColumnDef.id = defaultColumn.id
    resolvedColumnDef.header = defaultColumn.header
    resolvedColumnDef.columns = defaultColumn.columns
    resolvedColumnDef.accessorFn = defaultColumn.accessorFn
    resolvedColumnDef.accessorKey = defaultColumn.accessorKey
    resolvedColumnDef.enableHiding = defaultColumn.enableHiding
    resolvedColumnDef.enablePinning = defaultColumn.enablePinning
    resolvedColumnDef.enableColumnFilter = defaultColumn.enableColumnFilter
    resolvedColumnDef.filterFn = defaultColumn.filterFn
    resolvedColumnDef.enableGlobalFilter = defaultColumn.enableGlobalFilter
    resolvedColumnDef.enableMultiSort = defaultColumn.enableMultiSort
    resolvedColumnDef.enableSorting = defaultColumn.enableSorting
    resolvedColumnDef.invertSorting = defaultColumn.invertSorting
    resolvedColumnDef.sortDescFirst = defaultColumn.sortDescFirst
    resolvedColumnDef.sortingFn = defaultColumn.sortingFn
    resolvedColumnDef.sortUndefined = defaultColumn.sortUndefined
    resolvedColumnDef.aggregatedCell = defaultColumn.aggregatedCell
    resolvedColumnDef.aggregationFn = defaultColumn.aggregationFn
    resolvedColumnDef.enableGrouping = defaultColumn.enableGrouping
    resolvedColumnDef.getGroupingValue = defaultColumn.getGroupingValue
    resolvedColumnDef.enableResizing = defaultColumn.enableResizing
    resolvedColumnDef.maxSize = defaultColumn.maxSize
    resolvedColumnDef.minSize = defaultColumn.minSize
    resolvedColumnDef.size = defaultColumn.size
    // ...columnDef — overwrites only where `columnDef` provides a non-null value.
    resolvedColumnDef.getUniqueValues = columnDef.getUniqueValues ?: resolvedColumnDef.getUniqueValues
    resolvedColumnDef.footer = columnDef.footer ?: resolvedColumnDef.footer
    resolvedColumnDef.cell = columnDef.cell ?: resolvedColumnDef.cell
    resolvedColumnDef.meta = (columnDef.meta as ColumnMeta<TData, Any?>?) ?: resolvedColumnDef.meta
    resolvedColumnDef.id = columnDef.id ?: resolvedColumnDef.id
    resolvedColumnDef.header = columnDef.header ?: resolvedColumnDef.header
    resolvedColumnDef.columns = columnDef.columns ?: resolvedColumnDef.columns
    resolvedColumnDef.accessorFn = (columnDef.accessorFn as AccessorFn<TData, Any?>?) ?: resolvedColumnDef.accessorFn
    resolvedColumnDef.accessorKey = columnDef.accessorKey ?: resolvedColumnDef.accessorKey
    resolvedColumnDef.enableHiding = columnDef.enableHiding ?: resolvedColumnDef.enableHiding
    resolvedColumnDef.enablePinning = columnDef.enablePinning ?: resolvedColumnDef.enablePinning
    resolvedColumnDef.enableColumnFilter = columnDef.enableColumnFilter ?: resolvedColumnDef.enableColumnFilter
    resolvedColumnDef.filterFn = (columnDef.filterFn as FilterFnOption<TData>?) ?: resolvedColumnDef.filterFn
    resolvedColumnDef.enableGlobalFilter = columnDef.enableGlobalFilter ?: resolvedColumnDef.enableGlobalFilter
    resolvedColumnDef.enableMultiSort = columnDef.enableMultiSort ?: resolvedColumnDef.enableMultiSort
    resolvedColumnDef.enableSorting = columnDef.enableSorting ?: resolvedColumnDef.enableSorting
    resolvedColumnDef.invertSorting = columnDef.invertSorting ?: resolvedColumnDef.invertSorting
    resolvedColumnDef.sortDescFirst = columnDef.sortDescFirst ?: resolvedColumnDef.sortDescFirst
    resolvedColumnDef.sortingFn = (columnDef.sortingFn as SortingFnOption<TData>?) ?: resolvedColumnDef.sortingFn
    resolvedColumnDef.sortUndefined = columnDef.sortUndefined ?: resolvedColumnDef.sortUndefined
    resolvedColumnDef.aggregatedCell = columnDef.aggregatedCell ?: resolvedColumnDef.aggregatedCell
    resolvedColumnDef.aggregationFn = (columnDef.aggregationFn as AggregationFnOption<TData>?) ?: resolvedColumnDef.aggregationFn
    resolvedColumnDef.enableGrouping = columnDef.enableGrouping ?: resolvedColumnDef.enableGrouping
    resolvedColumnDef.getGroupingValue = columnDef.getGroupingValue ?: resolvedColumnDef.getGroupingValue
    resolvedColumnDef.enableResizing = columnDef.enableResizing ?: resolvedColumnDef.enableResizing
    resolvedColumnDef.maxSize = columnDef.maxSize ?: resolvedColumnDef.maxSize
    resolvedColumnDef.minSize = columnDef.minSize ?: resolvedColumnDef.minSize
    resolvedColumnDef.size = columnDef.size ?: resolvedColumnDef.size

    val accessorKey = resolvedColumnDef.accessorKey

    // Resolve the id from `id`, falling back to the accessor key (dots
    // replaced with underscores), then to a string header.
    val id: String? =
        resolvedColumnDef.id
            ?: (if (isTruthy(accessorKey)) accessorKey!!.replace(".", "_") else null)
            ?: (if (resolvedColumnDef.header is String) resolvedColumnDef.header as String else null)

    var accessorFn: AccessorFn<TData, Any?>? = null

    if (resolvedColumnDef.accessorFn != null) {
        accessorFn = resolvedColumnDef.accessorFn
    } else if (isTruthy(accessorKey)) {
        // Support deep accessor keys (dotted paths).
        if (accessorKey!!.contains(".")) {
            accessorFn = fn@{ originalRow: TData, _: Int ->
                // Walks the row as a string-keyed map down the dotted path.
                var result: Any? = originalRow

                for (key in accessorKey.split(".")) {
                    result = (result as? Map<String, Any?>)?.get(key)
                    if (result == null) {
                        println(
                            "\"$key\" in deeply nested key \"$accessorKey\" returned undefined.",
                        )
                    }
                }

                return@fn result
            }
        } else {
            accessorFn = { originalRow: TData, _: Int ->
                (originalRow as? Map<String, Any?>)?.get(resolvedColumnDef.accessorKey)
            }
        }
    }

    if (id == null) {
        throw IllegalStateException(
            if (resolvedColumnDef.accessorFn != null) {
                "Columns require an id when using an accessorFn"
            } else {
                "Columns require an id when using a non-string header"
            },
        )
    }

    // Declared first so the memoised closures below can capture it.
    val column = Column<TData, TValue>()

    column.id = "$id"
    column.accessorFn = accessorFn as AccessorFn<TData, TValue>?
    column.parent = parent
    column.depth = depth
    column.columnDef = resolvedColumnDef as ColumnDef<TData, TValue>
    column.columns = emptyList()

    column.getFlatColumns = memo(
        getDeps = { listOf(true) },
        fn = {
            buildList {
                add(column)
                addAll(column.columns.flatMap { d -> d.getFlatColumns() })
            }
        },
        opts = getMemoOptions(table.options, "debugColumns", "column.getFlatColumns"),
    )

    column.getLeafColumns = memo(
        getDeps = { listOf(table._getOrderColumnsFn()) },
        fn = { deps ->
            val orderColumns =
                deps[0] as (List<Column<TData, Any?>>) -> List<Column<TData, Any?>>
            if (column.columns.isNotEmpty()) {
                val leafColumns = column.columns.flatMap { c -> c.getLeafColumns() }

                orderColumns(leafColumns as List<Column<TData, Any?>>) as List<Column<TData, TValue>>
            } else {
                listOf(column)
            }
        },
        opts = getMemoOptions(table.options, "debugColumns", "column.getLeafColumns"),
    )

    for (feature in table._features) {
        feature.createColumn?.invoke(column as Column<Any?, Any?>, table as Table<Any?>)
    }

    // Yes, we have to convert table to unknown, because we know more than the
    // compiler here.
    return column
}
