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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tanstacktable.compose.flexRender
import io.github.tanstacktable.compose.rememberTable
import io.github.tanstacktable.compose.ColumnWidth
import io.github.tanstacktable.compose.TableCellText
import io.github.tanstacktable.compose.TableGrid
import io.github.tanstacktable.core.CellContext
import io.github.tanstacktable.core.ColumnDef
import io.github.tanstacktable.core.HeaderContext
import io.github.tanstacktable.core.Row
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.createColumnHelper
import io.github.tanstacktable.core.getCoreRowModel
import io.github.tanstacktable.core.getFilteredRowModel
import io.github.tanstacktable.core.getPaginationRowModel

/**
 * Header + footer + per-row checkboxes wired to the engine's selection API,
 * with three-state visuals (on / off / indeterminate) via Material3
 * `TriStateCheckbox`: the header checkbox covers all rows, the footer covers
 * the current page, and each row toggles itself. A global search field is
 * wired through `setGlobalFilter` so filtering keeps row ids stable and
 * selection survives the filter pass. Rows additionally carry two sub-rows
 * each, exercising the engine's default `enableSubRowSelection` propagation.
 */

/**
 * Builds 24 top-level rows, each with two sub-rows, so the page-of-10 pager
 * exercises both the page/all selection distinction and the sub-row
 * propagation default.
 */
private fun rsPerson(
    firstName: String,
    lastName: String,
    age: Int,
    visits: Int,
    progress: Int,
    status: String,
    subRows: List<Map<String, Any?>>? = null,
): Map<String, Any?> = mapOf(
    "firstName" to firstName,
    "lastName" to lastName,
    "age" to age,
    "visits" to visits,
    "progress" to progress,
    "status" to status,
    "subRows" to subRows,
)

private val rsFirstNames = listOf(
    "Tanner", "Kevin", "Linsley", "Harper", "Maya", "Owen", "Sofia", "Liam",
    "Ava", "Noah", "Emma", "Lucas", "Mia", "Ethan", "Zoe", "Caleb",
    "Nora", "Eli", "Ruby", "Jude", "Iris", "Max", "Cora", "Finn",
)
private val rsLastNames = listOf(
    "Linsley", "Vandy", "Stone", "Hart", "Frost", "Wells", "Pike", "Reed",
    "Lane", "Cole", "Vance", "Knox", "Dale", "Ford", "Gray", "Hale",
    "Webb", "Nash", "Pace", "Rhodes", "Bond", "Voss", "Quinn", "Sharpe",
)
private val rsStatuses = listOf("relationship", "complicated", "single")

private fun rsMakeData(count: Int, seed: Int = 0): List<Map<String, Any?>> = List(count) { i ->
    rsPerson(
        firstName = rsFirstNames[(i + seed) % rsFirstNames.size],
        lastName = rsLastNames[(i + seed * 3) % rsLastNames.size],
        age = (i * 7 + seed * 5) % 41,
        visits = (i * 137 + seed * 11) % 1000,
        progress = (i * 13 + seed * 7) % 101,
        status = rsStatuses[(i + seed) % rsStatuses.size],
        subRows = List(2) { j ->
            rsPerson(
                firstName = rsFirstNames[(i + j + 1 + seed) % rsFirstNames.size],
                lastName = rsLastNames[(i + j + 1 + seed * 3) % rsLastNames.size],
                age = ((i + j + 1) * 7 + seed * 5) % 41,
                visits = ((i + j + 1) * 137 + seed * 11) % 1000,
                progress = ((i + j + 1) * 13 + seed * 7) % 101,
                status = rsStatuses[(i + j + 1 + seed) % rsStatuses.size],
            )
        },
    )
}

private val rsColumnHelper = createColumnHelper<Any?>()

/**
 * A `select` display column plus two grouped header columns (`Name`, `Info`).
 * The `select` column's `header` and `cell` templates resolve only to
 * sentinels — the render loop draws the actual `TriStateCheckbox` instances
 * bound to the engine's selection API.
 */
private val rsColumns: List<ColumnDef<Any?, Any?>> = listOf(
    rsColumnHelper.display(
        ColumnDef<Any?, Any?>().apply {
            id = "select"
            // Sentinels: the render loop draws the checkboxes.
            header = { _: Any? -> "select" }
            cell = { _: Any? -> "select" }
        },
    ),
    rsColumnHelper.group(
        ColumnDef<Any?, Any?>().apply {
            header = "Name"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
            columns = listOf(
                rsColumnHelper.accessor(
                    "firstName",
                    ColumnDef<Any?, Any?>().apply {
                        cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                ),
                rsColumnHelper.accessor(
                    { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] },
                    ColumnDef<Any?, Any?>().apply {
                        id = "lastName"
                        cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
                        header = { _: Any? -> "Last Name" }
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                ),
            )
        },
    ),
    rsColumnHelper.group(
        ColumnDef<Any?, Any?>().apply {
            header = "Info"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
            columns = listOf(
                rsColumnHelper.accessor(
                    "age",
                    ColumnDef<Any?, Any?>().apply {
                        header = { _: Any? -> "Age" }
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                ),
                rsColumnHelper.group(
                    ColumnDef<Any?, Any?>().apply {
                        header = "More Info"
                        columns = listOf(
                            rsColumnHelper.accessor(
                                "visits",
                                ColumnDef<Any?, Any?>().apply {
                                    header = { _: Any? -> "Visits" }
                                    footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                                },
                            ),
                            rsColumnHelper.accessor(
                                "status",
                                ColumnDef<Any?, Any?>().apply {
                                    header = "Status"
                                    footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                                },
                            ),
                            rsColumnHelper.accessor(
                                "progress",
                                ColumnDef<Any?, Any?>().apply {
                                    header = "Profile Progress"
                                    footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                                },
                            ),
                        )
                    },
                ),
            )
        },
    ),
)

@Composable
fun RowSelectionExample() {
    // Force-rerender handle for the demo "Force Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    // `globalFilter` lives in the engine's state — see the search box below.

    var refreshSeed by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf(rsMakeData(24)) }

    // The engine manages `rowSelection` and `globalFilter` internally; the
    // checkboxes and search field call back directly. `getSubRows` lets the
    // engine recurse into rsMakeData's sub-rows so the selection feature's
    // default sub-row propagation is exercised.
    val table = rememberTable(
        TableOptions(
            data = data,
            columns = rsColumns,
            enableRowSelection = true,
            getSubRows = { row, _ ->
                @Suppress("UNCHECKED_CAST")
                (row as Map<String, Any?>)["subRows"] as List<Any?>?
            },
            getCoreRowModel = getCoreRowModel(),
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
        // `getFilteredRowModel()` keeps row ids stable, so `rowSelection`
        // (keyed by row id) is not corrupted when the filter changes.
        OutlinedTextField(
            value = (table.getState().globalFilter as? String) ?: "",
            onValueChange = { table.setGlobalFilter(it) },
            label = { Text("Search all columns...") },
        )

        Spacer(Modifier.height(8.dp))

        // Every column is content-sized; the leading `select` checkbox column
        // is pinned narrow via `columnWidth`.
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            TableGrid(
                columnWidth = { col -> if (col == 0) ColumnWidth.Fixed(56.dp) else ColumnWidth.Auto },
            ) {
                for (headerGroup in table.getHeaderGroups()) row {
                    for (header in headerGroup.headers) {
                        // `colSpan` widens grouped headers.
                        cell(colSpan = header.colSpan) {
                            if (!header.isPlaceholder) {
                                // The `select` column's header is the all-rows
                                // checkbox; every other header renders via
                                // flexRender.
                                if (header.column.id == "select") {
                                    SelectAllCheckbox(
                                        checked = table.getIsAllRowsSelected(),
                                        someSelected = table.getIsSomeRowsSelected(),
                                        onCheckedChange = { table.toggleAllRowsSelected(it) },
                                    )
                                } else {
                                    TableCellText(
                                        flexRender(
                                            header.column.columnDef.header,
                                            header.getContext(),
                                        ),
                                        bold = true,
                                    )
                                }
                            }
                        }
                    }
                }
                for (dataRow in table.getRowModel().rows) row {
                    for (dataCell in dataRow.getVisibleCells()) cell {
                        // The `select` column's cell hosts the per-row
                        // checkbox; other cells are text via flexRender.
                        if (dataCell.column.id == "select") {
                            RowSelectCheckbox(dataRow)
                        } else {
                            TableCellText(
                                flexRender(dataCell.column.columnDef.cell, dataCell.getContext()),
                            )
                        }
                    }
                }
                // Footer row: the all-page-rows checkbox + a row count
                // spanning the six non-select leaf columns.
                row {
                    cell {
                        SelectAllCheckbox(
                            checked = table.getIsAllPageRowsSelected(),
                            someSelected = table.getIsSomePageRowsSelected(),
                            onCheckedChange = { table.toggleAllPageRowsSelected(it) },
                        )
                    }
                    cell(colSpan = 6) {
                        TableCellText("Page Rows (${table.getRowModel().rows.size})")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Pager controls.
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
        // Page-size buttons.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (size in listOf(10, 20, 30, 40, 50)) {
                Button(onClick = { table.setPageSize(size) }) { Text("Show $size") }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "${table.getState().rowSelection.size} of " +
                "${table.getPreFilteredRowModel().rows.size} Total Rows Selected",
        )

        Spacer(Modifier.height(8.dp))

        Button(onClick = { rerender++ }) { Text("Force Rerender") }
        Button(onClick = {
            refreshSeed++
            data = rsMakeData(24, seed = refreshSeed)
        }) { Text("Refresh Data") }

        Spacer(Modifier.height(8.dp))

        // Small readout of the engine's current row-selection state.
        Text("Row Selection State:", fontWeight = FontWeight.Bold)
        Text(table.getState().rowSelection.toString())
    }
}

/**
 * Per-row selection checkbox bound to `row.toggleSelected(...)`. Uses
 * Material3 `TriStateCheckbox` so `row.getIsSomeSelected()` can drive the
 * indeterminate visual when only some sub-rows are selected.
 *
 * Marked [NonRestartableComposable] for the same reason as [TableGrid]: the
 * single `Row<Any?>` param is the SAME row instance across recompositions and
 * Compose's stability inference would treat it as stable. With its own
 * restart scope the call would be SKIPPED whenever the caller re-renders
 * (for example, after `toggleAllRowsSelected`), so the cell checkbox would
 * not pick up the new `row.getIsSelected()` value. Removing the restart
 * scope makes the body always re-run from the caller's composition.
 */
@Composable
@NonRestartableComposable
private fun RowSelectCheckbox(row: Row<Any?>) {
    val isSelected = row.getIsSelected()
    val isSome = row.getIsSomeSelected()
    TriStateCheckbox(
        state = when {
            isSelected -> ToggleableState.On
            isSome -> ToggleableState.Indeterminate
            else -> ToggleableState.Off
        },
        onClick = { row.toggleSelected(!isSelected, null) },
        enabled = row.getCanSelect(),
    )
}

/**
 * Header and footer "all rows" / "all page rows" tri-state checkbox bound to
 * the engine's selection API.
 */
@Composable
private fun SelectAllCheckbox(
    checked: Boolean,
    someSelected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    TriStateCheckbox(
        state = when {
            checked -> ToggleableState.On
            someSelected -> ToggleableState.Indeterminate
            else -> ToggleableState.Off
        },
        onClick = { onCheckedChange(!checked) },
    )
}
