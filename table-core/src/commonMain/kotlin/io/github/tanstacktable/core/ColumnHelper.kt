package io.github.tanstacktable.core

/*
 * Column-helper builder. Produces a typed factory that turns a column-def
 * template plus an accessor function or accessor-key string into a
 * [ColumnDef], without forcing the caller to spell out the
 * accessor/display/group plumbing each time.
 */

/**
 * Typed helper for building column defs. The [accessor] member accepts
 * either an [AccessorFn] or an accessor-key [String] and produces the
 * corresponding accessor column def; [display] and [group] are the
 * identity for symmetry with the JS API.
 *
 * The first parameter of [accessor] is typed `Any?` because Kotlin cannot
 * model "function or string" as a single type — pass a function for an
 * accessor-fn column or a string for an accessor-key column.
 */
interface ColumnHelper<TData> {
    /**
     * Builds an accessor column def from [accessor] (a function or a key
     * string) and a base [column] template. The returned def is a shallow
     * copy of [column] with `accessorFn` or `accessorKey` filled in.
     */
    val accessor: (accessor: Any?, column: ColumnDef<TData, Any?>) -> ColumnDef<TData, Any?>

    /** Returns [column] unchanged — for display columns. */
    val display: (column: ColumnDef<TData, Any?>) -> ColumnDef<TData, Any?>

    /** Returns [column] unchanged — for group columns. */
    val group: (column: ColumnDef<TData, Any?>) -> ColumnDef<TData, Any?>
}

private class ColumnHelperImpl<TData>(
    override val accessor: (accessor: Any?, column: ColumnDef<TData, Any?>) -> ColumnDef<TData, Any?>,
    override val display: (column: ColumnDef<TData, Any?>) -> ColumnDef<TData, Any?>,
    override val group: (column: ColumnDef<TData, Any?>) -> ColumnDef<TData, Any?>,
) : ColumnHelper<TData>

/**
 * Creates a [ColumnHelper] for rows of [TData]. Use the helper's [accessor]
 * / [display] / [group] factories to build [ColumnDef]s for a table.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> createColumnHelper(): ColumnHelper<TData> {
    return ColumnHelperImpl(
        accessor = { accessor, column ->
            if (accessor is Function<*>) {
                shallowCopyColumnDef(column).also {
                    it.accessorFn = accessor as AccessorFn<TData, Any?>
                }
            } else {
                shallowCopyColumnDef(column).also {
                    it.accessorKey = accessor as String?
                }
            }
        },
        display = { column -> column },
        group = { column -> column },
    )
}

/**
 * Shallow copy of a [ColumnDef]. Field values (functions, the `columns`
 * list, `meta`, ...) are shared by reference. Keep in sync with
 * [ColumnDef] — any new field here also needs an explicit copy assignment.
 */
private fun <TData, TValue> shallowCopyColumnDef(
    column: ColumnDef<TData, TValue>,
): ColumnDef<TData, TValue> {
    val copy = ColumnDef<TData, TValue>()
    copy.getUniqueValues = column.getUniqueValues
    copy.footer = column.footer
    copy.cell = column.cell
    copy.meta = column.meta
    copy.id = column.id
    copy.header = column.header
    copy.columns = column.columns
    copy.accessorFn = column.accessorFn
    copy.accessorKey = column.accessorKey
    copy.enableHiding = column.enableHiding
    copy.enablePinning = column.enablePinning
    copy.enableColumnFilter = column.enableColumnFilter
    copy.filterFn = column.filterFn
    copy.enableGlobalFilter = column.enableGlobalFilter
    copy.enableMultiSort = column.enableMultiSort
    copy.enableSorting = column.enableSorting
    copy.invertSorting = column.invertSorting
    copy.sortDescFirst = column.sortDescFirst
    copy.sortingFn = column.sortingFn
    copy.sortUndefined = column.sortUndefined
    copy.aggregatedCell = column.aggregatedCell
    copy.aggregationFn = column.aggregationFn
    copy.enableGrouping = column.enableGrouping
    copy.getGroupingValue = column.getGroupingValue
    copy.enableResizing = column.enableResizing
    copy.maxSize = column.maxSize
    copy.minSize = column.minSize
    copy.size = column.size
    return copy
}
