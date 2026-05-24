package io.github.tanstacktable.core

/*
 * Row-pagination feature. Tracks `pagination` state (page index/size) and
 * exposes the page-navigation accessors and the paginated row-model
 * builder.
 */

/**
 * Pagination state: zero-based [pageIndex] and the page size in rows.
 */
class PaginationState(
    val pageIndex: Int,
    val pageSize: Int,
)

private const val defaultPageIndex: Int = 0
private const val defaultPageSize: Int = 10

/**
 * Default pagination state. A fresh instance is returned on each call.
 */
private fun getDefaultPaginationState(): PaginationState =
    PaginationState(
        pageIndex = defaultPageIndex,
        pageSize = defaultPageSize,
    )

/**
 * The `RowPagination` feature. Adds `pagination` state, the page-navigation
 * accessors (`nextPage`, `previousPage`, `setPageIndex`, `setPageSize`, ...)
 * and the paginated row-model accessor.
 */
@Suppress("UNCHECKED_CAST")
object RowPagination : TableFeature {

    override val getInitialState: ((initialState: InitialTableState?) -> TableState) = { state ->
        // Each field of an incoming `pagination` overrides the corresponding
        // default. Note: a missing partial means both defaults apply; when
        // present, both incoming fields are taken — Kotlin cannot express
        // the original per-field `Partial<PaginationState>`.
        val def = getDefaultPaginationState()
        val partial = state?.pagination
        TableState.fromInitialState(
            state,
            pagination = PaginationState(
                pageIndex = partial?.pageIndex ?: def.pageIndex,
                pageSize = partial?.pageSize ?: def.pageSize,
            ),
        )
    }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>) = { table ->
        TableOptionsResolved<Any?>().also {
            it.onPaginationChange = makeStateUpdater("pagination", table)
        }
    }

    override val createTable: ((table: Table<Any?>) -> Unit) = { table ->
        // Closure-captured flags used by `_autoResetPageIndex` below.
        var registered = false
        var queued = false

        table._autoResetPageIndex = autoReset@{
            if (!registered) {
                table._queue {
                    registered = true
                }
                return@autoReset
            }

            if (
                table.options.autoResetAll
                    ?: table.options.autoResetPageIndex
                    ?: !isTruthy(table.options.manualPagination)
            ) {
                if (queued) return@autoReset
                queued = true
                table._queue {
                    table.resetPageIndex(null)
                    queued = false
                }
            }
        }
        table.setPagination = { updater ->
            // Wrap `updater` so it is resolved against the current
            // pagination state via `functionalUpdate`.
            val safeUpdater: Updater = { old: PaginationState ->
                val newState = functionalUpdate(updater, old)
                newState
            }

            table.options.onPaginationChange?.invoke(safeUpdater)
        }
        table.resetPagination = { defaultState: Boolean? ->
            table.setPagination(
                if (defaultState == true) {
                    getDefaultPaginationState()
                } else {
                    table.initialState.pagination
                },
            )
        }
        table.setPageIndex = { updater ->
            table.setPagination { oldAny: Any? ->
                val old = oldAny as PaginationState

                var pageIndex: Int = functionalUpdate(updater, old.pageIndex)

                // `pageCount == -1` or `null` means "unknown total"; allow
                // unbounded forward navigation in that case.
                val maxPageIndex: Long =
                    if (table.options.pageCount == null || table.options.pageCount == -1) {
                        9007199254740991L
                    } else {
                        (table.options.pageCount!! - 1).toLong()
                    }

                // Clamp into `[0, maxPageIndex]` (computed in `Long` then
                // narrowed back to `Int`).
                pageIndex = pageIndex.toLong().coerceIn(0L, maxPageIndex).toInt()

                PaginationState(pageIndex = pageIndex, pageSize = old.pageSize)
            }
        }
        table.resetPageIndex = { defaultState: Boolean? ->
            table.setPageIndex(
                if (defaultState == true) {
                    defaultPageIndex
                } else {
                    table.initialState.pagination.pageIndex
                },
            )
        }
        table.resetPageSize = { defaultState: Boolean? ->
            table.setPageSize(
                if (defaultState == true) {
                    defaultPageSize
                } else {
                    table.initialState.pagination.pageSize
                },
            )
        }
        table.setPageSize = { updater ->
            table.setPagination { oldAny: Any? ->
                val old = oldAny as PaginationState

                // Always keep page size at >= 1.
                val pageSize: Int = functionalUpdate<Int>(updater, old.pageSize).coerceAtLeast(1)
                val topRowIndex: Int = old.pageSize * old.pageIndex
                // Recompute the page index so the same top row is still
                // visible at the new page size.
                val pageIndex: Int =
                    kotlin.math.floor(topRowIndex.toDouble() / pageSize.toDouble()).toInt()

                PaginationState(pageIndex = pageIndex, pageSize = pageSize)
            }
        }
        // Deprecated: page count no longer lives on pagination state.
        table.setPageCount = { updater ->
            table.setPagination { oldAny: Any? ->
                val old = oldAny as PaginationState

                var newPageCount: Any? = functionalUpdate<Any?>(updater, table.options.pageCount ?: -1)

                if (newPageCount is Number) {
                    newPageCount = maxOf(-1.0, newPageCount.toDouble())
                }

                // The original spreads a phantom `pageCount` key onto the
                // pagination state object. The closed Kotlin class cannot
                // carry undeclared keys; this branch returns a plain copy
                // (nothing in the codebase reads that phantom key).
                PaginationState(pageIndex = old.pageIndex, pageSize = old.pageSize)
            }
        }

        table.getPageOptions = memo(
            getDeps = { listOf(table.getPageCount()) },
            fn = { deps ->
                val pageCount = deps[0] as Int
                var pageOptions: List<Int> = emptyList()
                if (pageCount > 0) {
                    // Builds `[0, 1, ..., pageCount-1]`.
                    pageOptions = (0 until pageCount).toList()
                }
                pageOptions
            },
            opts = getMemoOptions(table.options, "debugTable", "getPageOptions"),
        )

        table.getCanPreviousPage = { table.getState().pagination.pageIndex > 0 }

        table.getCanNextPage = canNext@{
            val pageIndex = table.getState().pagination.pageIndex

            val pageCount = table.getPageCount()

            if (pageCount == -1) {
                return@canNext true
            }

            if (pageCount == 0) {
                return@canNext false
            }

            pageIndex < pageCount - 1
        }

        table.previousPage = {
            table.setPageIndex({ old: Int -> old - 1 })
        }

        table.nextPage = {
            table.setPageIndex({ old: Int -> old + 1 })
        }

        table.firstPage = {
            table.setPageIndex(0)
        }

        table.lastPage = {
            table.setPageIndex(table.getPageCount() - 1)
        }

        table.getPrePaginationRowModel = { table.getExpandedRowModel() }
        table.getPaginationRowModel = {
            // Lazily instantiate the paginated row-model builder.
            if (table._getPaginationRowModel == null && table.options.getPaginationRowModel != null) {
                table._getPaginationRowModel = table.options.getPaginationRowModel!!.invoke(table)
            }

            if (isTruthy(table.options.manualPagination) || table._getPaginationRowModel == null) {
                table.getPrePaginationRowModel()
            } else {
                table._getPaginationRowModel!!.invoke()
            }
        }

        table.getPageCount = {
            table.options.pageCount
                ?: kotlin.math.ceil(
                    table.getRowCount().toDouble() / table.getState().pagination.pageSize.toDouble(),
                ).toInt()
        }

        table.getRowCount = {
            table.options.rowCount ?: table.getPrePaginationRowModel().rows.size
        }
    }
}
