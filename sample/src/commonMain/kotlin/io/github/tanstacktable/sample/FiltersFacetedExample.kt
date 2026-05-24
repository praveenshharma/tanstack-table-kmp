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
import io.github.tanstacktable.core.getFacetedMinMaxValues
import io.github.tanstacktable.core.getFacetedRowModel
import io.github.tanstacktable.core.getFacetedUniqueValues
import io.github.tanstacktable.core.getFilteredRowModel
import io.github.tanstacktable.core.getPaginationRowModel
import io.github.tanstacktable.core.getSortedRowModel

/**
 * The Filters example layered with faceting: the per-column filter widget
 * derives its select options from `column.getFacetedUniqueValues()` and the
 * range Min/Max placeholders from `column.getFacetedMinMaxValues()`. Wires
 * `getFacetedRowModel()` + `getFacetedUniqueValues()` +
 * `getFacetedMinMaxValues()` on top of the standard filtering/sorting/
 * pagination pipeline.
 */

/** Rows are `Map<String, Any?>` so the engine's `accessorKey` can index them. */
private fun facetedPerson(
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

private val facetedFirstNames = listOf(
    "Tanner", "Tandy", "Joe", "Maya", "Liam", "Noah", "Olivia", "Ava",
    "Ethan", "Sophia", "Lucas", "Mia", "Aiden", "Isla", "Caleb", "Zoe",
)
private val facetedLastNames = listOf(
    "Linsley", "Miller", "Dirte", "Stone", "Vance", "Quinn", "Reed", "Park",
    "Howe", "Cruz", "Bauer", "Frost", "Nash", "Wells", "Ortiz", "Day",
)
private val facetedStatuses = listOf("relationship", "complicated", "single")

/**
 * Builds 100 deterministic rows so faceting (unique-value sets, min/max) has
 * visible variation across the demo data set.
 */
private fun makeFacetedData(count: Int, seed: Int = 0): List<Map<String, Any?>> =
    (0 until count).map { i ->
        facetedPerson(
            firstName = facetedFirstNames[(i + seed) % facetedFirstNames.size],
            lastName = facetedLastNames[(i * 7 + seed * 3) % facetedLastNames.size],
            age = (i * 13 + seed * 5) % 41,
            visits = (i * 137 + seed * 11) % 1001,
            progress = (i * 29 + seed * 7) % 101,
            status = facetedStatuses[(i * 5 + seed) % facetedStatuses.size],
        )
    }

/**
 * Per-column filter variant. `ColumnMeta` is an empty interface in
 * `:table-core`, so this map lives outside the column definitions and is
 * consulted by [FacetedFilter] when rendering each header's input. Columns
 * with no entry default to the text variant.
 */
private val facetedVariant: Map<String, String> = mapOf(
    "age" to "range",
    "visits" to "range",
    "status" to "select",
    "progress" to "range",
)

private val facetedColumnHelper = createColumnHelper<Any?>()

private val facetedColumns: List<ColumnDef<Any?, Any?>> = listOf(
    facetedColumnHelper.accessor(
        "firstName",
        ColumnDef<Any?, Any?>().apply {
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
        },
    ),
    facetedColumnHelper.accessor(
        { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] },
        ColumnDef<Any?, Any?>().apply {
            id = "lastName"
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            header = { _: Any? -> "Last Name" }
        },
    ),
    facetedColumnHelper.accessor(
        "age",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Age" }
        },
    ),
    facetedColumnHelper.accessor(
        "visits",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Visits" }
        },
    ),
    facetedColumnHelper.accessor(
        "status",
        ColumnDef<Any?, Any?>().apply {
            header = "Status"
        },
    ),
    facetedColumnHelper.accessor(
        "progress",
        ColumnDef<Any?, Any?>().apply {
            header = "Profile Progress"
        },
    ),
)

@Composable
fun FiltersFacetedExample() {
    // Force-rerender handle for the demo "Force Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    var refreshSeed by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf(makeFacetedData(100)) }

    // The engine manages `columnFilters` internally; the per-column input
    // widgets call `column.setFilterValue(...)` directly. The faceted row
    // model + unique/min-max providers feed [FacetedFilter].
    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = facetedColumns,
            getCoreRowModel = getCoreRowModel(),
            getFilteredRowModel = getFilteredRowModel(), // client-side filtering
            getSortedRowModel = getSortedRowModel(),
            getPaginationRowModel = getPaginationRowModel(),
            getFacetedRowModel = getFacetedRowModel(), // client-side faceting
            getFacetedUniqueValues = getFacetedUniqueValues(), // unique values for select filter
            getFacetedMinMaxValues = getFacetedMinMaxValues(), // min/max for range filter
        ),
    )

    @Suppress("UNUSED_EXPRESSION")
    rerender

    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Plain fixed-width cell layout (see [filtersColWidth] in
        // FiltersExample.kt) so the `OutlinedTextField` filter inputs receive
        // text events; TableGrid's SubcomposeLayout would freeze them.
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
                                        FacetedFilter(column)
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
            data = makeFacetedData(100, seed = refreshSeed)
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
 * The per-column filter widget, driven by faceted data: the select options
 * come from `column.getFacetedUniqueValues().keys` (sorted, capped at 5000),
 * and the range Min/Max placeholders show `column.getFacetedMinMaxValues()`.
 *
 * Marked [NonRestartableComposable] for the same reason as `FiltersFilter` in
 * FiltersExample.kt — its single `TableColumn` param looks stable to Compose,
 * so the restart scope would skip the composable on every state change and
 * the controlled `OutlinedTextField`'s `value = column.getFilterValue()`
 * would never refresh.
 */
@Composable
@NonRestartableComposable
private fun FacetedFilter(column: TableColumn<Any?, *>) {
    val filterVariant = facetedVariant[column.id]
    val columnFilterValue = column.getFilterValue()

    val sortedUniqueValues: List<Any?> =
        if (filterVariant == "range") {
            emptyList()
        } else {
            column.getFacetedUniqueValues().keys
                .sortedBy { it?.toString() ?: "" }
                .take(5000)
        }

    when (filterVariant) {
        "range" -> {
            @Suppress("UNCHECKED_CAST")
            val range = columnFilterValue as? List<Any?>
            val minMax = column.getFacetedMinMaxValues()
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
                    placeholder = {
                        Text(if (minMax != null) "Min (${minMax.first})" else "Min")
                    },
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
                    placeholder = {
                        Text(if (minMax != null) "Max (${minMax.second})" else "Max")
                    },
                    modifier = Modifier.width(110.dp),
                )
            }
        }
        "select" -> {
            // Compose has no built-in `<select>`; a row of clickable options
            // stands in. The empty string clears the filter (the "All" option).
            val current = columnFilterValue?.toString() ?: ""
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = "All",
                    fontWeight = if (current == "") FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { column.setFilterValue("") }
                        .padding(2.dp),
                )
                Spacer(Modifier.width(4.dp))
                for (value in sortedUniqueValues) {
                    val label = value?.toString() ?: ""
                    Text(
                        text = label,
                        fontWeight = if (current == label) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { column.setFilterValue(value) }
                            .padding(2.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
        else -> {
            // Compose has no equivalent of an HTML `<datalist>` autocomplete,
            // so the unique-value count is surfaced in the placeholder instead.
            OutlinedTextField(
                value = (columnFilterValue ?: "").toString(),
                onValueChange = { text -> column.setFilterValue(text) },
                placeholder = { Text("Search... (${column.getFacetedUniqueValues().size})") },
                modifier = Modifier.width(220.dp),
            )
        }
    }
}
