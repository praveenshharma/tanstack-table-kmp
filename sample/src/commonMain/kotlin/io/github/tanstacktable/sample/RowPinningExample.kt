@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.tanstacktable.sample

import androidx.compose.foundation.background
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tanstacktable.compose.flexRender
import io.github.tanstacktable.compose.rememberTable
import io.github.tanstacktable.compose.TableCellText
import io.github.tanstacktable.compose.TableGrid
import io.github.tanstacktable.compose.TableGridScope
import io.github.tanstacktable.core.CellContext
import io.github.tanstacktable.core.ColumnDef
import io.github.tanstacktable.core.HeaderContext
import io.github.tanstacktable.core.InitialTableState
import io.github.tanstacktable.core.PaginationState
import io.github.tanstacktable.core.Row
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.createColumnHelper
import io.github.tanstacktable.core.getCoreRowModel
import io.github.tanstacktable.core.getExpandedRowModel
import io.github.tanstacktable.core.getFilteredRowModel
import io.github.tanstacktable.core.getPaginationRowModel

/**
 * Per-row pin (Up / Dn / X) buttons that surface `row.pin('top' | 'bottom' |
 * false, includeLeafRows, includeParentRows)`, plus four demo checkboxes that
 * toggle `keepPinnedRows`, the leaf/parent pin behaviour, and a "copy pinned
 * rows back into the main table" mode. The pinned rows render as fixed top
 * and bottom sections tinted light blue (Compose has no CSS `position:
 * sticky`); the scrolling center sits between them.
 */

/**
 * Builds nested rows using a recursive `lens`-driven generator. The demo
 * calls `rpMakeData()` with lens `(100, 2, 2)` — 100 top-level rows, each
 * with 2 sub-rows, each of those with 2 sub-rows — so expansion and
 * parent/leaf pinning are exercised together.
 */
private fun rpPerson(
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

private val rpFirstNames = listOf(
    "Tanner", "Kevin", "Linsley", "Harper", "Maya", "Owen", "Sofia", "Liam",
    "Ava", "Noah", "Emma", "Lucas",
)
private val rpLastNames = listOf(
    "Linsley", "Vandy", "Stone", "Hart", "Frost", "Wells", "Pike", "Reed",
    "Lane", "Cole", "Vance", "Knox",
)
private val rpStatuses = listOf("relationship", "complicated", "single")

/**
 * Recursive deterministic row builder. `seed` distributes varied values
 * across the tree so pressing "Refresh Data" visibly changes the result.
 */
private fun rpMakeDataLevel(lens: List<Int>, depth: Int, seedBase: Int): List<Map<String, Any?>> {
    val len = lens[depth]
    return List(len) { d ->
        val seed = seedBase * 31 + d + depth * 7
        rpPerson(
            firstName = rpFirstNames[seed % rpFirstNames.size],
            lastName = rpLastNames[(seed * 3) % rpLastNames.size],
            age = (seed * 7) % 41,
            visits = (seed * 137) % 1000,
            progress = (seed * 13) % 101,
            status = rpStatuses[seed % rpStatuses.size],
            subRows = if (depth + 1 < lens.size) {
                rpMakeDataLevel(lens, depth + 1, seed)
            } else {
                null
            },
        )
    }
}

private fun rpMakeData(seed: Int = 0): List<Map<String, Any?>> =
    rpMakeDataLevel(lens = listOf(100, 2, 2), depth = 0, seedBase = 1 + seed)

private val rpColumnHelper = createColumnHelper<Any?>()

/**
 * A `pin` display column plus a `firstName` accessor whose cell hosts an
 * expand toggle, plus `lastName` / `age` / `visits` / `status` / `progress`.
 * The `pin` cell and the `firstName` header/cell host interactive widgets, so
 * their templates resolve only to sentinels — the render loop draws the
 * actual buttons next to the resolved values. `size` hints on
 * `age` / `visits` / `progress` are carried on the `ColumnDef` for engines
 * that read them; this Compose renderer uses fixed-width cells regardless.
 */
private val rpColumns: List<ColumnDef<Any?, Any?>> = listOf(
    rpColumnHelper.display(
        ColumnDef<Any?, Any?>().apply {
            id = "pin"
            header = { _: Any? -> "Pin" }
            // Sentinel: the render loop draws the pin/unpin buttons.
            cell = { _: Any? -> "pin" }
        },
    ),
    rpColumnHelper.accessor(
        "firstName",
        ColumnDef<Any?, Any?>().apply {
            // Sentinel: the render loop draws the all-rows expand toggle +
            // "First Name" label.
            header = { _: Any? -> "firstName" }
            // The render loop draws the per-row expand toggle beside the
            // resolved value.
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    rpColumnHelper.accessor(
        { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] },
        ColumnDef<Any?, Any?>().apply {
            id = "lastName"
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            header = { _: Any? -> "Last Name" }
        },
    ),
    rpColumnHelper.accessor(
        "age",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Age" }
            size = 50.0
        },
    ),
    rpColumnHelper.accessor(
        "visits",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Visits" }
            size = 50.0
        },
    ),
    rpColumnHelper.accessor(
        "status",
        ColumnDef<Any?, Any?>().apply {
            header = "Status"
        },
    ),
    rpColumnHelper.accessor(
        "progress",
        ColumnDef<Any?, Any?>().apply {
            header = "Profile Progress"
            size = 80.0
        },
    ),
)

@Composable
fun RowPinningExample() {
    // Force-rerender handle for the demo "Force Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    var keepPinnedRows by remember { mutableStateOf(true) }
    var includeLeafRows by remember { mutableStateOf(true) }
    var includeParentRows by remember { mutableStateOf(false) }
    var copyPinnedRows by remember { mutableStateOf(false) }

    var refreshSeed by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf(rpMakeData()) }

    // The engine manages `rowPinning` and `expanded` internally; the pin
    // buttons and expanders call back into the engine directly.
    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = rpColumns,
            initialState = InitialTableState(
                pagination = PaginationState(pageIndex = 0, pageSize = 20),
            ),
            getSubRows = { row, _ ->
                @Suppress("UNCHECKED_CAST")
                (row as Map<String, Any?>)["subRows"] as List<Any?>?
            },
            getCoreRowModel = getCoreRowModel(),
            getFilteredRowModel = getFilteredRowModel(),
            getExpandedRowModel = getExpandedRowModel(),
            getPaginationRowModel = getPaginationRowModel(),
            keepPinnedRows = keepPinnedRows,
        ),
    )

    @Suppress("UNUSED_EXPRESSION")
    rerender

    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        // The header and the three pinned sections (top / center / bottom)
        // share ONE TableGrid so the grid stays column-aligned. Compose has
        // no CSS `position: sticky`, so pinned rows render as fixed leading
        // and trailing sections rather than as scroll-sticky rows.
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            TableGrid {
                for (headerGroup in table.getHeaderGroups()) row {
                    for (header in headerGroup.headers) {
                        cell(colSpan = header.colSpan) {
                            if (!header.isPlaceholder) {
                                // The `firstName` header carries the all-rows
                                // expand toggle; other headers are plain text.
                                if (header.column.id == "firstName") {
                                    Row {
                                        TextButton(onClick = { table.toggleAllRowsExpanded(null) }) {
                                            Text(if (table.getIsAllRowsExpanded()) "v" else ">")
                                        }
                                        TableCellText("First Name", bold = true)
                                    }
                                } else {
                                    TableCellText(
                                        flexRender(
                                            header.column.columnDef.header,
                                            header.getContext(),
                                        ),
                                        bold = true,
                                    )
                                }
                                // Per-column filter inputs are intentionally
                                // omitted; the dedicated Filters screen
                                // demonstrates column filtering.
                                // `getFilteredRowModel` is still wired so the
                                // option list matches what filtering would
                                // produce.
                            }
                        }
                    }
                }

                // Top pinned rows render as a fixed leading section, tinted
                // light blue.
                for (dataRow in table.getTopRows()) {
                    pinnedDataRow(dataRow, pinned = true, includeLeafRows, includeParentRows)
                }
                // Center rows — or the full row model when `copyPinnedRows`
                // is set (so the pinned rows appear in both the pinned
                // sections AND the main body).
                val centerRows =
                    if (copyPinnedRows) table.getRowModel().rows else table.getCenterRows()
                for (dataRow in centerRows) {
                    pinnedDataRow(dataRow, pinned = false, includeLeafRows, includeParentRows)
                }
                // Bottom pinned rows.
                for (dataRow in table.getBottomRows()) {
                    pinnedDataRow(dataRow, pinned = true, includeLeafRows, includeParentRows)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Pager controls. FlowRow is the Compose equivalent of CSS
        // `flex-wrap: wrap` — buttons stay on one line when they fit, wrap to
        // the next when they don't.
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
        // Page-size buttons, FlowRow-wrapped so every "Show N" is reachable
        // on narrow widths.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (size in listOf(10, 20, 30, 40, 50)) {
                Button(onClick = { table.setPageSize(size) }) { Text("Show $size") }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Four demo checkboxes that drive the pin behaviour.
        DemoCheckbox(
            checked = keepPinnedRows,
            onCheckedChange = { keepPinnedRows = !keepPinnedRows },
            label = "Keep/Persist Pinned Rows across Pagination and Filtering",
        )
        DemoCheckbox(
            checked = includeLeafRows,
            onCheckedChange = { includeLeafRows = !includeLeafRows },
            label = "Include Leaf Rows When Pinning Parent",
        )
        DemoCheckbox(
            checked = includeParentRows,
            onCheckedChange = { includeParentRows = !includeParentRows },
            label = "Include Parent Rows When Pinning Child",
        )
        DemoCheckbox(
            checked = copyPinnedRows,
            onCheckedChange = { copyPinnedRows = !copyPinnedRows },
            label = "Duplicate/Keep Pinned Rows in main table",
        )

        Spacer(Modifier.height(8.dp))

        Button(onClick = { rerender++ }) { Text("Force Rerender") }
        Button(onClick = {
            refreshSeed++
            data = rpMakeData(seed = refreshSeed)
        }) { Text("Refresh Data") }

        Spacer(Modifier.height(8.dp))

        // Small readout of the engine's current row-pinning state.
        val rowPinning = table.getState().rowPinning
        Text(
            "rowPinning: top=${rowPinning.top ?: emptyList<String>()} " +
                "bottom=${rowPinning.bottom ?: emptyList<String>()}",
        )
    }
}

/**
 * Emits one [TableGrid] row for any of the three sections (top pinned,
 * center, bottom pinned). When `pinned`, each cell is tinted light blue (a
 * TableGrid row has no per-row modifier, so the fill is applied behind each
 * cell's content). The `pin` and `firstName` cells host the interactive
 * buttons; the other cells just render `flexRender` values.
 *
 * `includeLeafRows` / `includeParentRows` are passed in and forwarded to
 * `row.pin(...)` rather than read from a shared closure.
 */
private fun TableGridScope.pinnedDataRow(
    dataRow: Row<Any?>,
    pinned: Boolean,
    includeLeafRows: Boolean,
    includeParentRows: Boolean,
) {
    row {
        for (dataCell in dataRow.getVisibleCells()) cell {
            // The pinned tint fills each cell, since a TableGrid row has no
            // per-row background modifier.
            Box(if (pinned) Modifier.background(Color(0xFFADD8E6)) else Modifier) {
                when (dataCell.column.id) {
                    "pin" -> {
                        // When pinned, show an unpin (X) button; otherwise
                        // show pin-up + pin-down buttons.
                        val pinnedPos = dataRow.getIsPinned()
                        if (pinnedPos != false && pinnedPos != null) {
                            TextButton(
                                onClick = {
                                    dataRow.pin(false, includeLeafRows, includeParentRows)
                                },
                            ) { Text("X") }
                        } else {
                            Row {
                                TextButton(
                                    onClick = {
                                        dataRow.pin("top", includeLeafRows, includeParentRows)
                                    },
                                ) { Text("Up") }
                                TextButton(
                                    onClick = {
                                        dataRow.pin("bottom", includeLeafRows, includeParentRows)
                                    },
                                ) { Text("Dn") }
                            }
                        }
                    }
                    "firstName" -> {
                        // Indented by depth; an expand toggle for expandable
                        // rows, a leaf marker otherwise.
                        Row(Modifier.padding(start = (dataRow.depth * 16).dp)) {
                            if (dataRow.getCanExpand()) {
                                TextButton(onClick = { dataRow.toggleExpanded(null) }) {
                                    Text(if (dataRow.getIsExpanded()) "v" else ">")
                                }
                            } else {
                                Text("- ")
                            }
                            TableCellText(
                                flexRender(dataCell.column.columnDef.cell, dataCell.getContext()),
                            )
                        }
                    }
                    else -> {
                        TableCellText(
                            flexRender(dataCell.column.columnDef.cell, dataCell.getContext()),
                        )
                    }
                }
            }
        }
    }
}

/** One of the four demo toggles — a Material3 `Checkbox` with a text label. */
@Composable
private fun DemoCheckbox(checked: Boolean, onCheckedChange: () -> Unit, label: String) {
    Row {
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })
        Text(label, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
    }
}
