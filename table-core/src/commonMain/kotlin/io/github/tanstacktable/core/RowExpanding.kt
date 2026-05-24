package io.github.tanstacktable.core

/*
 * Row-expanding feature. Tracks per-row expansion state, the row-level
 * accessors and the table-level setters/resets.
 */

/**
 * Per-row expansion flags. An entry's value indicates whether the row is
 * expanded; missing rows are collapsed.
 */
typealias ExpandedStateList = Map<String, Boolean>

/**
 * Expansion state. Either the literal `true` (every row expanded) or a
 * [Map] of row-id to flag (most rows collapsed by default). Modelled as
 * `Any?` because Kotlin cannot express that union directly; the feature
 * code discriminates by runtime type.
 */
typealias ExpandedState = Any?

/**
 * The `RowExpanding` feature. Adds `expanded` state, the row-level
 * expansion accessors and the table-level `setExpanded` /
 * `toggleAllRowsExpanded` / `getExpandedRowModel`.
 */
@Suppress("UNCHECKED_CAST")
object RowExpanding : TableFeature {

    override val getInitialState: ((initialState: InitialTableState?) -> TableState) = { state ->
        TableState.fromInitialState(
            state,
            expanded = state?.expanded ?: emptyMap<String, Boolean>(),
        )
    }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>) = { table ->
        TableOptionsResolved<Any?>().also {
            it.onExpandedChange = makeStateUpdater("expanded", table)
            it.paginateExpandedRows = true
        }
    }

    override val createTable: ((table: Table<Any?>) -> Unit) = { table ->
        // Closure-captured flags used by `_autoResetExpanded` below.
        var registered = false
        var queued = false

        table._autoResetExpanded = autoReset@{
            if (!registered) {
                table._queue {
                    registered = true
                }
                return@autoReset
            }

            if (
                table.options.autoResetAll
                    ?: table.options.autoResetExpanded
                    ?: !isTruthy(table.options.manualExpanding)
            ) {
                if (queued) return@autoReset
                queued = true
                table._queue {
                    table.resetExpanded(null)
                    queued = false
                }
            }
        }
        table.setExpanded = { updater -> table.options.onExpandedChange?.invoke(updater) }
        table.toggleAllRowsExpanded = { expanded: Boolean? ->
            if (expanded ?: !table.getIsAllRowsExpanded()) {
                table.setExpanded(true)
            } else {
                table.setExpanded(emptyMap<String, Boolean>())
            }
        }
        table.resetExpanded = { defaultState: Boolean? ->
            table.setExpanded(if (defaultState == true) emptyMap<String, Boolean>() else table.initialState.expanded ?: emptyMap<String, Boolean>())
        }
        table.getCanSomeRowsExpand = {
            table.getPrePaginationRowModel().flatRows.any { row -> row.getCanExpand() }
        }
        table.getToggleAllRowsExpandedHandler = {
            { e: Any? ->
                // The original calls `event.persist?.()` for React's
                // synthetic-event pooling; commonMain has no equivalent.
                table.toggleAllRowsExpanded(null)
            }
        }
        table.getIsSomeRowsExpanded = {
            val expanded = table.getState().expanded
            // `expanded` is either the literal `true` or a row-id map.
            expanded == true ||
                (expanded as Map<String, Boolean>).values.any { isTruthy(it) }
        }
        table.getIsAllRowsExpanded = allExpanded@{
            val expanded = table.getState().expanded

            // If expanded is a boolean, save some cycles and return it.
            if (expanded is Boolean) {
                return@allExpanded expanded == true
            }

            if ((expanded as Map<String, Boolean>).keys.isEmpty()) {
                return@allExpanded false
            }

            // If any row is not expanded, return false.
            if (table.getRowModel().flatRows.any { row -> !row.getIsExpanded() }) {
                return@allExpanded false
            }

            // They must all be expanded.
            true
        }
        table.getExpandedDepth = {
            var maxDepth = 0

            val rowIds: Set<String> =
                if (table.getState().expanded == true) {
                    table.getRowModel().rowsById.keys
                } else {
                    (table.getState().expanded as Map<String, Boolean>).keys
                }

            rowIds.forEach { id ->
                val splitId = id.split(".")
                maxDepth = maxOf(maxDepth, splitId.size)
            }

            maxDepth
        }
        table.getPreExpandedRowModel = { table.getSortedRowModel() }
        table.getExpandedRowModel = {
            // Lazily instantiate the expanded row-model builder.
            if (table._getExpandedRowModel == null && table.options.getExpandedRowModel != null) {
                table._getExpandedRowModel = table.options.getExpandedRowModel!!.invoke(table)
            }

            if (isTruthy(table.options.manualExpanding) || table._getExpandedRowModel == null) {
                table.getPreExpandedRowModel()
            } else {
                table._getExpandedRowModel!!.invoke()
            }
        }
    }

    override val createRow: ((row: Row<Any?>, table: Table<Any?>) -> Unit) = { row, table ->
        row.toggleExpanded = { expanded: Boolean? ->
            table.setExpanded { oldAny: Any? ->
                val old = oldAny

                // `old === true` means "everything expanded"; otherwise look
                // the row id up in the map.
                val exists: Boolean =
                    if (old == true) {
                        true
                    } else {
                        isTruthy((old as? Map<String, Boolean>)?.get(row.id))
                    }

                var oldExpanded: MutableMap<String, Boolean> = mutableMapOf()

                if (old == true) {
                    // Materialise an explicit map of every row when the state
                    // collapses from "all expanded" to a per-row form.
                    table.getRowModel().rowsById.keys.forEach { rowId ->
                        oldExpanded[rowId] = true
                    }
                } else {
                    oldExpanded = (old as Map<String, Boolean>).toMutableMap()
                }

                val resolvedExpanded: Boolean = expanded ?: !exists

                if (!exists && resolvedExpanded) {
                    return@setExpanded oldExpanded.toMutableMap().also { it[row.id] = true }
                }

                if (exists && !resolvedExpanded) {
                    return@setExpanded oldExpanded.toMutableMap().also { it.remove(row.id) }
                }

                old
            }
        }
        row.getIsExpanded = {
            val expanded = table.getState().expanded

            // Defer to the user-supplied `getIsRowExpanded` when provided;
            // otherwise check the state directly.
            val orResult: Boolean =
                expanded == true || isTruthy((expanded as? Map<String, Boolean>)?.get(row.id))
            isTruthy(
                table.options.getIsRowExpanded?.invoke(row) ?: orResult,
            )
        }
        row.getCanExpand = {
            table.options.getRowCanExpand?.invoke(row)
                ?: ((table.options.enableExpanding ?: true) && row.subRows.isNotEmpty())
        }
        row.getIsAllParentsExpanded = {
            var isFullyExpanded = true
            var currentRow = row

            while (isFullyExpanded && isTruthy(currentRow.parentId)) {
                currentRow = table.getRow(currentRow.parentId!!, true)
                isFullyExpanded = currentRow.getIsExpanded()
            }

            isFullyExpanded
        }
        row.getToggleExpandedHandler = {
            val canExpand = row.getCanExpand()

            // Bound to a `val` so it is parsed as an expression value rather
            // than a labelled statement.
            val handler: () -> Unit = handler@{
                if (!canExpand) return@handler
                row.toggleExpanded(null)
            }
            handler
        }
    }
}
