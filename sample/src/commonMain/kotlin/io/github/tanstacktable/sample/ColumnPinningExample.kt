package io.github.tanstacktable.sample

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.tanstacktable.core.CellContext
import io.github.tanstacktable.core.Column
import io.github.tanstacktable.core.ColumnDef
import io.github.tanstacktable.core.HeaderContext
import io.github.tanstacktable.core.Row
import io.github.tanstacktable.core.Table
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.createColumnHelper
import io.github.tanstacktable.core.getCoreRowModel

/**
 * Per-column pin buttons (left / unpin / right) plus a "Split Mode" toggle
 * that splits the table into three side-by-side sections: pinned-left,
 * scrolling center, and pinned-right. Demonstrates `column.pin(...)`,
 * `table.getLeftHeaderGroups()` / `getCenterHeaderGroups()` /
 * `getRightHeaderGroups()`, and the matching `getLeftVisibleCells()` /
 * `getCenterVisibleCells()` / `getRightVisibleCells()` on each row.
 */

/**
 * 30 deterministic rows — enough to fill the table (which `.take(20)`s its
 * row model below) while staying emulator-friendly.
 */
private fun cpPerson(
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

private val cpFirstNames = listOf(
    "Tanner", "Kevin", "Linsley", "Harper", "Maya", "Owen", "Sofia", "Liam",
    "Ava", "Noah", "Emma", "Lucas", "Mia", "Ethan", "Zoe",
)
private val cpLastNames = listOf(
    "Linsley", "Vandy", "Stone", "Hart", "Frost", "Wells", "Pike", "Reed",
    "Lane", "Cole", "Vance", "Knox", "Dale", "Ford", "Gray",
)
private val cpStatuses = listOf("relationship", "complicated", "single")

private fun cpMakeData(count: Int, seed: Int = 0): List<Map<String, Any?>> = List(count) { i ->
    cpPerson(
        firstName = cpFirstNames[(i + seed) % cpFirstNames.size],
        lastName = cpLastNames[(i * 3 + seed * 5) % cpLastNames.size],
        age = (i * 7 + seed * 5) % 41,
        visits = (i * 137 + seed * 11) % 1000,
        progress = (i * 13 + seed * 7) % 101,
        status = cpStatuses[(i + seed) % cpStatuses.size],
    )
}

private val cpColumnHelper = createColumnHelper<Any?>()

/**
 * Two grouped header columns: `Name` (containing `firstName` + `lastName`) and
 * `Info` (containing `age` plus a nested `More Info` group with `visits`,
 * `status`, `progress`).
 */
private val cpColumns: List<ColumnDef<Any?, Any?>> = listOf(
    cpColumnHelper.group(
        ColumnDef<Any?, Any?>().apply {
            header = "Name"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
            columns = listOf(
                cpColumnHelper.accessor(
                    "firstName",
                    ColumnDef<Any?, Any?>().apply {
                        cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                ),
                cpColumnHelper.accessor(
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
    cpColumnHelper.group(
        ColumnDef<Any?, Any?>().apply {
            header = "Info"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
            columns = listOf(
                cpColumnHelper.accessor(
                    "age",
                    ColumnDef<Any?, Any?>().apply {
                        header = { _: Any? -> "Age" }
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                ),
                cpColumnHelper.group(
                    ColumnDef<Any?, Any?>().apply {
                        header = "More Info"
                        columns = listOf(
                            cpColumnHelper.accessor(
                                "visits",
                                ColumnDef<Any?, Any?>().apply {
                                    header = { _: Any? -> "Visits" }
                                    footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                                },
                            ),
                            cpColumnHelper.accessor(
                                "status",
                                ColumnDef<Any?, Any?>().apply {
                                    header = "Status"
                                    footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                                },
                            ),
                            cpColumnHelper.accessor(
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
fun ColumnPinningExample() {
    var refreshSeed by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(cpMakeData(30)) }

    var isSplit by remember { mutableStateOf(false) }

    // The engine manages `columnVisibility`, `columnOrder` and `columnPinning`
    // internally; the checkboxes, pin buttons, and Shuffle Columns button all
    // call into the engine directly.
    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = cpColumns,
            getCoreRowModel = getCoreRowModel(),
        ),
    )

    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Visibility toggles: a "Toggle All" master plus one per leaf column.
        Column(Modifier.border(1.dp, Color.Black).padding(4.dp)) {
            Row {
                Checkbox(
                    checked = table.getIsAllColumnsVisible(),
                    onCheckedChange = { table.toggleAllColumnsVisible(it) },
                )
                Text("Toggle All", Modifier.padding(start = 8.dp, top = 12.dp))
            }
            for (column in table.getAllLeafColumns()) {
                Row {
                    Checkbox(
                        checked = column.getIsVisible(),
                        onCheckedChange = { column.toggleVisibility(it) },
                    )
                    Text(column.id, Modifier.padding(start = 8.dp, top = 12.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row {
            Button(onClick = {
                refreshSeed++
                data = cpMakeData(30, seed = refreshSeed)
            }) { Text("Regenerate") }
            // Reorders the leaf columns by rotating them one slot left — a
            // deterministic stand-in for a true shuffle, so each press visibly
            // moves the columns without relying on a random source.
            Button(
                onClick = {
                    val ids = table.getAllLeafColumns().map { it.id }
                    val rotated = if (ids.isEmpty()) ids else ids.drop(1) + ids.first()
                    table.setColumnOrder(rotated)
                },
            ) { Text("Shuffle Columns") }
        }

        Spacer(Modifier.height(16.dp))

        Row {
            Checkbox(checked = isSplit, onCheckedChange = { isSplit = it })
            Text("Split Mode", Modifier.padding(start = 8.dp, top = 12.dp))
        }

        Row(Modifier.horizontalScroll(rememberScrollState())) {
            if (isSplit) {
                // Left pinned section: `getLeftHeaderGroups()` and per-row
                // `getLeftVisibleCells()`.
                ColumnPinningTable(
                    table = table,
                    headerGroups = table.getLeftHeaderGroups(),
                    cellsOf = { row -> row.getLeftVisibleCells() },
                )
                Spacer(Modifier.width(16.dp))
            }
            // Center section in split mode, or the full table otherwise.
            ColumnPinningTable(
                table = table,
                headerGroups = if (isSplit) table.getCenterHeaderGroups() else table.getHeaderGroups(),
                cellsOf = { row -> if (isSplit) row.getCenterVisibleCells() else row.getVisibleCells() },
            )
            if (isSplit) {
                Spacer(Modifier.width(16.dp))
                // Right pinned section.
                ColumnPinningTable(
                    table = table,
                    headerGroups = table.getRightHeaderGroups(),
                    cellsOf = { row -> row.getRightVisibleCells() },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        val columnPinning = table.getState().columnPinning
        Text(
            "columnPinning: left=${columnPinning.left ?: emptyList<String>()} " +
                "right=${columnPinning.right ?: emptyList<String>()}",
        )
    }
}

/**
 * Renders one of the (up to three) bordered table sections. The left, center,
 * and right sections share the same header markup and `getRowModel().rows.take(20)`
 * body shape; the caller passes in the appropriate header groups and the per-
 * row cell list.
 */
@Composable
private fun ColumnPinningTable(
    table: Table<Any?>,
    headerGroups: List<io.github.tanstacktable.core.HeaderGroup<Any?>>,
    cellsOf: (Row<Any?>) -> List<io.github.tanstacktable.core.Cell<Any?, Any?>>,
) {
    // Each (left/center/right) section is its own TableGrid; columns are
    // content-sized. A 2dp black frame wraps each section.
    Box(Modifier.border(2.dp, Color.Black)) {
        TableGrid {
            for (headerGroup in headerGroups) row {
                for (header in headerGroup.headers) {
                    cell(colSpan = header.colSpan) {
                        Column {
                            if (!header.isPlaceholder) {
                                TableCellText(
                                    flexRender(header.column.columnDef.header, header.getContext()),
                                    bold = true,
                                )
                            }
                            if (!header.isPlaceholder && header.column.getCanPin()) {
                                PinControls(header.column)
                            }
                        }
                    }
                }
            }
            for (dataRow in table.getRowModel().rows.take(20)) row {
                for (dataCell in cellsOf(dataRow)) cell {
                    TableCellText(flexRender(dataCell.column.columnDef.cell, dataCell.getContext()))
                }
            }
        }
    }
}

/**
 * The pin-control buttons drawn inside each pinnable header: `<=` (pin left),
 * `X` (unpin), `=>` (pin right), each shown only when applicable to the
 * column's current pinned position. Calls `column.pin(...)` directly.
 */
@Composable
private fun PinControls(column: Column<Any?, *>) {
    val pinned = column.getIsPinned()
    Row {
        if (pinned != "left") {
            TextButton(onClick = { column.pin("left") }) { Text("<=") }
        }
        if (pinned != false && pinned != null) {
            TextButton(onClick = { column.pin(false) }) { Text("X") }
        }
        if (pinned != "right") {
            TextButton(onClick = { column.pin("right") }) { Text("=>") }
        }
    }
}
