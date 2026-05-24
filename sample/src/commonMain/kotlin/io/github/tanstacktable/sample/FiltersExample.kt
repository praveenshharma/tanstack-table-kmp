@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.tanstacktable.sample

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tanstacktable.compose.flexRender
import io.github.tanstacktable.compose.rememberTable
import io.github.tanstacktable.compose.TableCellText
import io.github.tanstacktable.compose.TableGrid
import io.github.tanstacktable.core.CellContext
import io.github.tanstacktable.core.Column as TableColumn
import io.github.tanstacktable.core.ColumnDef
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.createColumnHelper
import io.github.tanstacktable.core.getCoreRowModel
import io.github.tanstacktable.core.getFilteredRowModel
import io.github.tanstacktable.core.getPaginationRowModel
import io.github.tanstacktable.core.getSortedRowModel

/**
 * Per-column filter inputs (text, numeric range, single-select) wired to the
 * engine's column-filter API, with sortable headers and pagination layered on
 * top of `getFilteredRowModel` + `getSortedRowModel` + `getPaginationRowModel`.
 * The filter variant per column is held in a local map keyed by column id
 * (see [filtersVariant]); each input calls `column.setFilterValue(...)` and
 * the table reads its current `getState().columnFilters` for the readout.
 */

/** Rows are `Map<String, Any?>` so the engine's `accessorKey` can index them. */
private fun filtersPerson(
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

private val filtersFirstNames = listOf(
    "Tanner", "Tandy", "Joe", "Maya", "Liam", "Noah", "Olivia", "Ava",
    "Ethan", "Sophia", "Lucas", "Mia", "Aiden", "Isla", "Caleb", "Zoe",
)
private val filtersLastNames = listOf(
    "Linsley", "Miller", "Dirte", "Stone", "Vance", "Quinn", "Reed", "Park",
    "Howe", "Cruz", "Bauer", "Frost", "Nash", "Wells", "Ortiz", "Day",
)
private val filtersStatuses = listOf("relationship", "complicated", "single")

/**
 * Builds 100 deterministic rows; the seed shifts every field so pressing
 * "Refresh Data" visibly changes the data set while staying reproducible.
 */
private fun makeFiltersData(count: Int, seed: Int = 0): List<Map<String, Any?>> =
    (0 until count).map { i ->
        filtersPerson(
            firstName = filtersFirstNames[(i + seed) % filtersFirstNames.size],
            lastName = filtersLastNames[(i * 7 + seed * 3) % filtersLastNames.size],
            age = (i * 13 + seed * 5) % 41,
            visits = (i * 137 + seed * 11) % 1001,
            progress = (i * 29 + seed * 7) % 101,
            status = filtersStatuses[(i * 5 + seed) % filtersStatuses.size],
        )
    }

/**
 * Per-column filter variant. `ColumnMeta` is an empty interface in
 * `:table-core`, so this map lives outside the column definitions and is
 * consulted by [FiltersFilter] when rendering each header's input.
 */
private val filtersVariant: Map<String, String> = mapOf(
    "age" to "range",
    "visits" to "range",
    "status" to "select",
    "progress" to "range",
)

private val filtersColumnHelper = createColumnHelper<Any?>()

private val filtersColumns: List<ColumnDef<Any?, Any?>> = listOf(
    filtersColumnHelper.accessor(
        "firstName",
        ColumnDef<Any?, Any?>().apply {
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
        },
    ),
    filtersColumnHelper.accessor(
        { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] },
        ColumnDef<Any?, Any?>().apply {
            id = "lastName"
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            header = { _: Any? -> "Last Name" }
        },
    ),
    // A `fullName` column built from an `accessorFn` that concatenates the
    // first and last name values.
    filtersColumnHelper.accessor(
        { row: Any?, _: Int ->
            val m = row as Map<*, *>
            "${m["firstName"]} ${m["lastName"]}"
        },
        ColumnDef<Any?, Any?>().apply {
            id = "fullName"
            header = "Full Name"
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
        },
    ),
    filtersColumnHelper.accessor(
        "age",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Age" }
        },
    ),
    filtersColumnHelper.accessor(
        "visits",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Visits" }
        },
    ),
    filtersColumnHelper.accessor(
        "status",
        ColumnDef<Any?, Any?>().apply {
            header = "Status"
        },
    ),
    filtersColumnHelper.accessor(
        "progress",
        ColumnDef<Any?, Any?>().apply {
            header = "Profile Progress"
        },
    ),
)

/**
 * Fixed per-column width for the Filters table.
 *
 * This screen uses a plain fixed-width cell layout rather than the
 * content-sizing [TableGrid]: [TableGrid] measures cells through a
 * `SubcomposeLayout`, and a Material `OutlinedTextField` does not receive text
 * input when it lives inside a subcomposed-and-measured cell — so the
 * per-column filter inputs would freeze. The filter widget in each header
 * already pins the column width here, so the fixed widths cost nothing.
 */
internal fun filtersColWidth(columnId: String): Int = if (columnId == "status") 400 else 280
@Composable
fun FiltersExample() {
    // Force-rerender handle for the demo "Force Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    var refreshSeed by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf(makeFiltersData(100)) }

    // The engine manages `columnFilters` internally; the per-column input
    // widgets call `column.setFilterValue(...)` directly.
    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = filtersColumns,
            getCoreRowModel = getCoreRowModel(),
            getFilteredRowModel = getFilteredRowModel(), // client-side filtering
            getSortedRowModel = getSortedRowModel(),
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
        // Plain fixed-width cell layout (see [filtersColWidth]) so the
        // `OutlinedTextField` filter inputs receive text events; TableGrid's
        // SubcomposeLayout would freeze them.
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            for (headerGroup in table.getHeaderGroups()) {
                Row {
                    for (header in headerGroup.headers) {
                        TableCellBox(width = filtersColWidth(header.column.id)) {
                            if (!header.isPlaceholder) {
                                val column = header.column
                                // Header click toggles sorting when the column
                                // supports it.
                                val canSort = column.getCanSort()
                                val sortModifier = if (canSort) {
                                    Modifier.clickable { column.toggleSorting(null, false) }
                                } else {
                                    Modifier
                                }
                                Column {
                                    Column(sortModifier) {
                                        val sortIndicator = when (column.getIsSorted()) {
                                            "asc" -> " ▲"
                                            "desc" -> " ▼"
                                            else -> ""
                                        }
                                        TableCellText(
                                            flexRender(
                                                column.columnDef.header,
                                                header.getContext(),
                                            ).toString() + sortIndicator,
                                            bold = true,
                                        )
                                    }
                                    if (column.getCanFilter()) {
                                        FiltersFilter(column)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (dataRow in table.getRowModel().rows) {
                Row {
                    for (dataCell in dataRow.getVisibleCells()) {
                        TableCellBox(width = filtersColWidth(dataCell.column.id)) {
                            TableCellText(flexRender(dataCell.column.columnDef.cell, dataCell.getContext()))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Pagination controls.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = { table.setPageIndex(0) },
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
                onClick = { table.setPageIndex(table.getPageCount() - 1) },
                enabled = table.getCanNextPage(),
            ) { Text(">>") }
        }
        Text(
            "Page ${table.getState().pagination.pageIndex + 1} of ${table.getPageCount()}",
            fontWeight = FontWeight.Bold,
        )

        Text("${table.getPrePaginationRowModel().rows.size} Rows")

        Button(onClick = { rerender++ }) { Text("Force Rerender") }

        Button(onClick = {
            refreshSeed++
            data = makeFiltersData(100, seed = refreshSeed)
        }) { Text("Refresh Data") }

        // Small readout of the engine's current column-filter state.
        Text(
            "columnFilters: " +
                table.getState().columnFilters
                    ?.joinToString { "${it.id}=${it.value}" }
                    .orEmpty(),
        )
    }
}

/**
 * The per-column filter widget. Dispatches on the column's variant
 * (see [filtersVariant]): `"range"` shows two numeric inputs, `"select"` shows
 * a row of options (All + the three status literals), and anything else falls
 * back to a text-search input. Each control calls `column.setFilterValue(...)`
 * directly.
 *
 * Marked [NonRestartableComposable] for the same reason as [TableGrid]: the
 * single `TableColumn` param looks stable to Compose, so the restart scope
 * would skip this composable when its caller recomposes after
 * `setFilterValue`. That skip would leave the controlled
 * `OutlinedTextField`'s `value = column.getFilterValue()` stale — the user's
 * typing would fire `onValueChange` (the engine still filters correctly), but
 * the text field would visibly revert to the placeholder because its `value`
 * parameter was never refreshed.
 */
@Composable
@NonRestartableComposable
private fun FiltersFilter(column: TableColumn<Any?, *>) {
    val filterVariant = filtersVariant[column.id]
    val columnFilterValue = column.getFilterValue()

    when (filterVariant) {
        "range" -> {
            // The engine stores the range as a `[min, max]` tuple.
            @Suppress("UNCHECKED_CAST")
            val range = columnFilterValue as? List<Any?>
            Row {
                OutlinedTextField(
                    value = (range?.getOrNull(0))?.toString() ?: "",
                    onValueChange = { text ->
                        val v = text.toIntOrNull()
                        column.setFilterValue { oldAny: Any? ->
                            @Suppress("UNCHECKED_CAST")
                            val old = oldAny as? List<Any?>
                            listOf<Any?>(v, old?.getOrNull(1))
                        }
                    },
                    placeholder = { Text("Min") },
                    modifier = Modifier.width(110.dp),
                )
                Spacer(Modifier.width(4.dp))
                OutlinedTextField(
                    value = (range?.getOrNull(1))?.toString() ?: "",
                    onValueChange = { text ->
                        val v = text.toIntOrNull()
                        column.setFilterValue { oldAny: Any? ->
                            @Suppress("UNCHECKED_CAST")
                            val old = oldAny as? List<Any?>
                            listOf<Any?>(old?.getOrNull(0), v)
                        }
                    },
                    placeholder = { Text("Max") },
                    modifier = Modifier.width(110.dp),
                )
            }
        }
        "select" -> {
            // Compose has no built-in `<select>`; a row of clickable options
            // stands in. The empty string clears the filter (the "All" option).
            val current = columnFilterValue?.toString() ?: ""
            Row {
                for (option in listOf("" to "All", "complicated" to "complicated",
                        "relationship" to "relationship", "single" to "single")) {
                    Text(
                        text = option.second,
                        fontWeight = if (current == option.first) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { column.setFilterValue(option.first) }
                            .padding(2.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
        else -> {
            OutlinedTextField(
                value = (columnFilterValue ?: "").toString(),
                onValueChange = { text -> column.setFilterValue(text) },
                placeholder = { Text("Search...") },
                modifier = Modifier.width(220.dp),
            )
        }
    }
}
