package io.github.tanstacktable.core

/*
 * Row-sorting feature. Tracks the `sorting` state, the per-column sort
 * accessors and the table-level setters/resets.
 */

/**
 * Sort direction. One of `"asc"` or `"desc"` at runtime; modelled as
 * [String] because Kotlin cannot express a closed union of string literals.
 */
typealias SortDirection = String

/**
 * One entry in [SortingState]. [id] identifies the column; [desc] is the
 * direction (`true` for descending).
 */
class ColumnSort(
    val desc: Boolean,
    val id: String,
)

/**
 * Ordered list of [ColumnSort] entries. Order is significant: entry 0 is
 * the primary sort key.
 */
typealias SortingState = List<ColumnSort>

/**
 * Comparator signature for sorting. Returns a negative number when
 * [rowA] should come before [rowB], zero for equal, positive otherwise.
 * Built-in sort fns return `-1` / `0` / `1`.
 */
typealias SortingFn<TData> = (rowA: Row<TData>, rowB: Row<TData>, columnId: String) -> Int

/**
 * Caller-registered sort functions, keyed by name.
 */
typealias CustomSortingFns<TData> = Map<String, SortingFn<TData>>

/**
 * Choice of sort function. Either `"auto"`, the name of a built-in or
 * registered fn (a [String]), or a [SortingFn] value. Typed as `Any?`
 * because Kotlin cannot model that mixed union.
 */
typealias SortingFnOption<TData> = Any?

/**
 * The `RowSorting` feature. Adds `sorting` state, the column-level sort
 * accessors (`getSortingFn`, `getIsSorted`, `toggleSorting`, ...) and the
 * table-level `setSorting` / `resetSorting` / `getSortedRowModel`.
 */
@Suppress("UNCHECKED_CAST")
object RowSorting : TableFeature {

    override val getInitialState: ((initialState: InitialTableState?) -> TableState) = { state ->
        TableState.fromInitialState(
            state,
            sorting = state?.sorting ?: emptyList(),
        )
    }

    override val getDefaultColumnDef: (() -> ColumnDef<Any?, Any?>) = {
        ColumnDef<Any?, Any?>().also {
            it.sortingFn = "auto"
            // `1` means "sort undefined values last in ascending order"; see
            // [ColumnDef.sortUndefined].
            it.sortUndefined = 1
        }
    }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>) = { table ->
        TableOptionsResolved<Any?>().also {
            it.onSortingChange = makeStateUpdater("sorting", table)
            it.isMultiSortEvent = { e: Any? ->
                // Default returns `false` because shift-key detection on the
                // event is browser-DOM-specific. Adapters that own real events
                // override this option.
                false
            }
        }
    }

    override val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit) = { column, table ->
        column.getAutoSortingFn = getAutoSortingFn@{
            // Sample beyond the first 10 rows so empty/sparse leading rows
            // do not skew detection.
            val firstRows = table.getFilteredRowModel().flatRows.drop(10)

            var isString = false

            for (row in firstRows) {
                val value = row.getValue(column.id)

                // The original probes for a JS `Date` here. commonMain has no
                // single date type, so detection is left to adapters that
                // configure a date-aware `sortingFn` explicitly.
                @Suppress("KotlinConstantConditions")
                if (false) {
                    return@getAutoSortingFn sortingFns.getValue("datetime")
                }

                if (value is String) {
                    isString = true

                    // The original splits with a capturing group, which in JS
                    // retains the delimiters; Kotlin's `split(Regex)` drops
                    // them. The downstream test is only `> 1`, which still
                    // holds when any match occurred — so the predicate is
                    // preserved either way.
                    if (value.split(reSplitAlphaNumeric).size > 1) {
                        return@getAutoSortingFn sortingFns.getValue("alphanumeric")
                    }
                }
            }

            if (isString) {
                return@getAutoSortingFn sortingFns.getValue("text")
            }

            sortingFns.getValue("basic")
        }
        column.getAutoSortDir = {
            val firstRow = table.getFilteredRowModel().flatRows.getOrNull(0)

            val value = firstRow?.getValue(column.id)

            if (value is String) {
                "asc"
            } else {
                "desc"
            }
        }
        column.getSortingFn = {
            // Defensive null check; `column` is non-null here.
            @Suppress("SENSELESS_COMPARISON")
            if (column == null) {
                throw RuntimeException()
            }

            // Resolve `sortingFn`: a function passes through; `"auto"` picks
            // the auto sort fn; otherwise look up the name in the user
            // registry first, then the built-ins.
            if (isFunction(column.columnDef.sortingFn)) {
                column.columnDef.sortingFn as SortingFn<Any?>
            } else if (column.columnDef.sortingFn == "auto") {
                column.getAutoSortingFn()
            } else {
                table.options.sortingFns?.get(column.columnDef.sortingFn as String)
                    ?: sortingFns.getValue(column.columnDef.sortingFn as String)
            }
        }
        column.toggleSorting = { desc: Boolean?, multi: Boolean? ->
            // if (column.columns.length) {
            //   column.columns.forEach((c, i) => {
            //     if (c.id) {
            //       table.toggleColumnSorting(c.id, undefined, multi || !!i)
            //     }
            //   })
            //   return
            // }

            // This needs to be outside `table.setSorting` to stay in sync
            // with the rerender.
            val nextSortingOrder = column.getNextSortingOrder(null)
            val hasManualValue = desc != null

            table.setSorting { oldAny: Any? ->
                // `old` may arrive `null` if no sorting state exists yet.
                val old = oldAny as SortingState?

                // Find any existing sorting for this column.
                val existingSorting = old?.find { d -> d.id == column.id }
                val existingIndex = old?.indexOfFirst { d -> d.id == column.id }

                var newSorting: SortingState

                // What should we do with this sort action?
                val sortAction: String
                val nextDesc: Boolean? = if (hasManualValue) desc else nextSortingOrder == "desc"

                // Multi-mode.
                if (!old.isNullOrEmpty() && column.getCanMultiSort() && multi == true) {
                    sortAction = if (existingSorting != null) {
                        "toggle"
                    } else {
                        "add"
                    }
                } else {
                    // Normal mode.
                    sortAction = if (!old.isNullOrEmpty() && existingIndex != old.size - 1) {
                        "replace"
                    } else if (existingSorting != null) {
                        "toggle"
                    } else {
                        "replace"
                    }
                }

                // Handle toggle states that will remove the sorting.
                var resolvedSortAction = sortAction
                if (resolvedSortAction == "toggle") {
                    // If we are "actually" toggling (not a manual set value),
                    // should we remove the sorting?
                    if (!hasManualValue) {
                        // Is our intention to remove?
                        if (!isTruthy(nextSortingOrder)) {
                            resolvedSortAction = "remove"
                        }
                    }
                }

                if (resolvedSortAction == "add") {
                    newSorting = old!! + ColumnSort(id = column.id, desc = nextDesc!!)
                    // Take latest n columns. `maxMultiSortColCount` defaults
                    // to the platform max-safe integer (no cap).
                    val deleteCount =
                        (newSorting.size -
                            (table.options.maxMultiSortColCount?.toLong() ?: 9007199254740991L))
                    val removeFromStart = deleteCount.coerceIn(0L, newSorting.size.toLong()).toInt()
                    newSorting = newSorting.drop(removeFromStart)
                } else if (resolvedSortAction == "toggle") {
                    // Flips (or sets) the desc flag on the existing entry.
                    newSorting = old!!.map { d ->
                        if (d.id == column.id) {
                            ColumnSort(id = d.id, desc = nextDesc!!)
                        } else {
                            d
                        }
                    }
                } else if (resolvedSortAction == "remove") {
                    newSorting = old!!.filter { d -> d.id != column.id }
                } else {
                    newSorting = listOf(
                        ColumnSort(id = column.id, desc = nextDesc!!),
                    )
                }

                newSorting
            }
        }

        column.getFirstSortDir = {
            val sortDescFirst: Boolean =
                column.columnDef.sortDescFirst
                    ?: table.options.sortDescFirst
                    ?: (column.getAutoSortDir() == "desc")
            if (sortDescFirst) "desc" else "asc"
        }

        column.getNextSortingOrder = { multi: Boolean? ->
            val firstSortDirection = column.getFirstSortDir()
            val isSorted = column.getIsSorted()

            if (!isTruthy(isSorted)) {
                firstSortDirection
            } else if (
                isSorted != firstSortDirection &&
                (table.options.enableSortingRemoval ?: true) && // If enableSortRemove, enable in general.
                (if (multi == true) table.options.enableMultiRemove ?: true else true) // If multi, don't allow if enableMultiRemove.
            ) {
                false
            } else {
                if (isSorted == "desc") "asc" else "desc"
            }
        }

        column.getCanSort = {
            (column.columnDef.enableSorting ?: true) &&
                (table.options.enableSorting ?: true) &&
                column.accessorFn != null
        }

        column.getCanMultiSort = {
            column.columnDef.enableMultiSort
                ?: table.options.enableMultiSort
                ?: (column.accessorFn != null)
        }

        column.getIsSorted = {
            val columnSort = table.getState().sorting?.find { d -> d.id == column.id }

            if (columnSort == null) false else if (columnSort.desc) "desc" else "asc"
        }

        column.getSortIndex = {
            table.getState().sorting?.indexOfFirst { d -> d.id == column.id } ?: -1
        }

        column.clearSorting = {
            // Clear sorting for just this column.
            table.setSorting { oldAny: Any? ->
                val old = oldAny as SortingState?
                if (!old.isNullOrEmpty()) old.filter { d -> d.id != column.id } else emptyList()
            }
        }

        column.getToggleSortingHandler = {
            val canSort = column.getCanSort()

            // The handler is bound to a `val` so it is parsed as an
            // expression value rather than a labelled statement.
            val handler: (Any?) -> Unit = handler@{ e: Any? ->
                if (!canSort) return@handler
                // The original calls `event.persist?.()` for React's
                // synthetic-event pooling; commonMain has no equivalent.
                column.toggleSorting?.invoke(
                    null,
                    if (column.getCanMultiSort()) {
                        table.options.isMultiSortEvent?.invoke(e)
                    } else {
                        false
                    },
                )
            }
            handler
        }
    }

    override val createTable: ((table: Table<Any?>) -> Unit) = { table ->
        table.setSorting = { updater -> table.options.onSortingChange?.invoke(updater) }
        table.resetSorting = { defaultState: Boolean? ->
            table.setSorting(if (defaultState == true) emptyList<ColumnSort>() else table.initialState.sorting)
        }
        table.getPreSortedRowModel = { table.getGroupedRowModel() }
        table.getSortedRowModel = {
            // Lazily instantiate the sorted row-model builder.
            if (table._getSortedRowModel == null && table.options.getSortedRowModel != null) {
                table._getSortedRowModel = table.options.getSortedRowModel!!.invoke(table)
            }

            if (isTruthy(table.options.manualSorting) || table._getSortedRowModel == null) {
                table.getPreSortedRowModel()
            } else {
                table._getSortedRowModel!!.invoke()
            }
        }
    }
}
