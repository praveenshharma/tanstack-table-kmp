@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.tanstacktable.sample

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tanstacktable.compose.flexRender
import io.github.tanstacktable.compose.rememberTable
import io.github.tanstacktable.compose.TableCellText
import io.github.tanstacktable.compose.TableGrid
import io.github.tanstacktable.core.CellContext
import io.github.tanstacktable.core.ColumnDef
import io.github.tanstacktable.core.HeaderContext
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.createColumnHelper
import io.github.tanstacktable.core.getCoreRowModel
import io.github.tanstacktable.core.getFilteredRowModel
import io.github.tanstacktable.core.getPaginationRowModel
import io.github.tanstacktable.core.getSortedRowModel

/**
 * `<<` / `<` / `>` / `>>` pager buttons, a "Show N" row that swaps page size
 * via `table.setPageSize(...)`, plus the page index / count / row totals
 * readouts. The engine owns `pagination`; `getSortedRowModel` and
 * `getFilteredRowModel` are wired so `getRowModel()` runs the full
 * core → filtered → sorted → paginated pipeline, but the sort and filter UI
 * are omitted to keep the screen focused on the pager.
 */

/** Rows are `Map<String, Any?>` so the engine's `accessorKey` can index them. */
private fun paginationPerson(
    firstName: String,
    lastName: String,
    age: Int,
    visits: Int,
    progress: Int,
    status: String,
): Map<String, Any?> = mapOf(
    "firstName" to firstName,
    "lastName" to lastName,
    "age" to age,
    "visits" to visits,
    "progress" to progress,
    "status" to status,
)

/**
 * Pools used by [makeSamplePeople] to build 100 deterministic but varied demo
 * rows — enough to exercise paging at the default page size of 10.
 */
private val firstNamePool = listOf(
    "Tanner", "Kevin", "Maria", "Joe", "Sandra", "Harold", "Nadia", "Owen",
    "Priya", "Quinn", "Rosa", "Sven", "Tara", "Umar", "Vera", "Wade",
    "Xena", "Yusuf", "Zoe", "Aaron",
)
private val lastNamePool = listOf(
    "Linsley", "Miller", "Dirte", "Vance", "Holt", "Quill", "Reyes", "Snow",
    "Tate", "Underwood", "Voss", "West", "Yang", "Zimmer", "Abbott", "Boone",
    "Cruz", "Diaz", "Egan", "Frost",
)
private val statusPool = listOf("relationship", "complicated", "single")

private fun makeSamplePeople(count: Int, seed: Int = 0): List<Map<String, Any?>> =
    (0 until count).map { i ->
        paginationPerson(
            firstName = firstNamePool[(i + seed) % firstNamePool.size],
            lastName = lastNamePool[(i * 7 + seed * 3) % lastNamePool.size],
            age = (i * 13 + seed * 5) % 41,
            visits = (i * 137 + seed * 11) % 1001,
            progress = (i * 17 + seed * 7) % 101,
            status = statusPool[(i * 3 + seed) % statusPool.size],
        )
    }

private val paginationColumnHelper = createColumnHelper<Any?>()

private val paginationColumns: List<ColumnDef<Any?, Any?>> = listOf(
    paginationColumnHelper.accessor(
        "firstName",
        ColumnDef<Any?, Any?>().apply {
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    paginationColumnHelper.accessor(
        { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] },
        ColumnDef<Any?, Any?>().apply {
            id = "lastName"
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            header = { _: Any? -> "Last Name" }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    paginationColumnHelper.accessor(
        "age",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Age" }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    paginationColumnHelper.accessor(
        "visits",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Visits" }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    paginationColumnHelper.accessor(
        "status",
        ColumnDef<Any?, Any?>().apply {
            header = "Status"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    paginationColumnHelper.accessor(
        "progress",
        ColumnDef<Any?, Any?>().apply {
            header = "Profile Progress"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
)

/**
 * Renders the table, the pager controls (`<<`, `<`, `>`, `>>`), the page-size
 * row, plus row-count and pagination-state readouts.
 */
@Composable
fun PaginationExample() {
    // Bumping `refresh` rebuilds `data` with a new seed.
    var refresh by remember { mutableStateOf(0) }
    val data = remember(refresh) { makeSamplePeople(100, seed = refresh) }

    // Force-rerender handle for the demo "Force Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    // The engine manages `pagination` internally; the pager and page-size
    // buttons call back into `firstPage`, `nextPage`, `setPageIndex`,
    // `setPageSize` directly.
    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = paginationColumns,
            getCoreRowModel = getCoreRowModel(),
            getSortedRowModel = getSortedRowModel(),
            getFilteredRowModel = getFilteredRowModel(),
            getPaginationRowModel = getPaginationRowModel(),
        ),
    )

    @Suppress("UNUSED_EXPRESSION")
    rerender

    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Wrapped in a horizontal scroll; every column is content-sized.
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            TableGrid {
                for (headerGroup in table.getHeaderGroups()) row {
                    for (header in headerGroup.headers) cell(colSpan = header.colSpan) {
                        if (!header.isPlaceholder) {
                            TableCellText(
                                flexRender(header.column.columnDef.header, header.getContext()),
                                bold = true,
                            )
                        }
                    }
                }
                for (dataRow in table.getRowModel().rows) row {
                    for (dataCell in dataRow.getVisibleCells()) cell {
                        TableCellText(flexRender(dataCell.column.columnDef.cell, dataCell.getContext()))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Pager: first / previous / next / last + the current page readout.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = { table.firstPage() },
                enabled = table.getCanPreviousPage(),
            ) { Text("<<") }
            Button(
                onClick = { table.previousPage() },
                enabled = table.getCanPreviousPage(),
            ) { Text("<") }
            Button(
                onClick = { table.nextPage() },
                enabled = table.getCanNextPage(),
            ) { Text(">") }
            Button(
                onClick = { table.lastPage() },
                enabled = table.getCanNextPage(),
            ) { Text(">>") }
            TableCellText(
                "Page ${table.getState().pagination.pageIndex + 1} of ${table.getPageCount()}",
                bold = true,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Page-size buttons: one per supported page size; the active size is
        // disabled. Each click calls `table.setPageSize(n)`.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TableCellText("Show: ")
            for (pageSize in listOf(10, 20, 30, 40, 50)) {
                Button(
                    onClick = { table.setPageSize(pageSize) },
                    enabled = table.getState().pagination.pageSize != pageSize,
                ) { Text(pageSize.toString()) }
            }
        }

        Spacer(Modifier.height(8.dp))

        TableCellText("Showing ${table.getRowModel().rows.size} of ${table.getRowCount()} Rows")

        // Small readout of the engine's current pagination state.
        val pagination = table.getState().pagination
        TableCellText("pagination: { pageIndex: ${pagination.pageIndex}, pageSize: ${pagination.pageSize} }")

        Spacer(Modifier.height(16.dp))

        Button(onClick = { rerender++ }) {
            Text("Force Rerender")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { refresh++ }) {
            Text("Refresh Data")
        }
    }
}
