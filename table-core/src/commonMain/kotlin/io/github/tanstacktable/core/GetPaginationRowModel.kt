package io.github.tanstacktable.core

/*
 * Pagination row-model builder. Slices the pre-pagination model into the
 * current page; recurses into sub-rows so paginated parents include their
 * children unless `paginateExpandedRows` reshapes that.
 */

/**
 * Options accepted by [getPaginationRowModel]. Currently only [initialSync]
 * is defined; it is reserved for future use and not read by the builder.
 */
class GetPaginationRowModelOptions(
    val initialSync: Boolean,
)

/**
 * Builds the pagination row-model factory. The returned function is
 * invoked once per [Table] and produces a memoised getter for the
 * paginated [RowModel].
 *
 * [opts] is accepted for parity with the JS API but is not consumed by
 * the builder.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> getPaginationRowModel(
    opts: GetPaginationRowModelOptions? = null,
): (table: Table<TData>) -> () -> RowModel<TData> {
    return factory@{ table ->
        val memoized: () -> RowModel<TData> = memo<RowModel<TData>>(
            getDeps = {
                listOf(
                    table.getState().pagination,
                    table.getPrePaginationRowModel(),
                    if (table.options.paginateExpandedRows == true) {
                        null
                    } else {
                        table.getState().expanded
                    },
                )
            },
            fn = { deps ->
                @Suppress("UNCHECKED_CAST")
                val pagination = deps[0] as PaginationState

                @Suppress("UNCHECKED_CAST")
                val rowModel = deps[1] as RowModel<TData>

                if (rowModel.rows.isEmpty()) {
                    return@memo rowModel
                }

                val pageSize = pagination.pageSize
                val pageIndex = pagination.pageIndex
                var rows = rowModel.rows
                val flatRows = rowModel.flatRows
                val rowsById = rowModel.rowsById
                val pageStart = pageSize * pageIndex
                val pageEnd = pageStart + pageSize

                // Clamp the page bounds — `subList` throws on out-of-range
                // indices, unlike the lenient JS `Array.prototype.slice`.
                val sliceStart = pageStart.coerceIn(0, rows.size)
                val sliceEnd = pageEnd.coerceIn(sliceStart, rows.size)
                rows = rows.subList(sliceStart, sliceEnd).toList()

                var paginatedRowModel: RowModel<TData>

                if (table.options.paginateExpandedRows != true) {
                    paginatedRowModel = expandRows(
                        RowModel(
                            rows = rows,
                            flatRows = flatRows,
                            rowsById = rowsById,
                        ),
                    )
                } else {
                    paginatedRowModel = RowModel(
                        rows = rows,
                        flatRows = flatRows,
                        rowsById = rowsById,
                    )
                }

                // `RowModel.flatRows` is immutable, so the recomputed list is
                // accumulated locally and the model is rebuilt at the end.
                val paginatedFlatRows = mutableListOf<Row<TData>>()

                fun handleRow(row: Row<TData>) {
                    paginatedFlatRows.add(row)
                    if (row.subRows.isNotEmpty()) {
                        row.subRows.forEach { handleRow(it) }
                    }
                }

                paginatedRowModel.rows.forEach { handleRow(it) }

                RowModel(
                    rows = paginatedRowModel.rows,
                    flatRows = paginatedFlatRows,
                    rowsById = paginatedRowModel.rowsById,
                )
            },
            opts = getMemoOptions(table.options, "debugTable", "getPaginationRowModel"),
        )
                return@factory { memoized() }
    }
}
