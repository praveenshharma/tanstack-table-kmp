package io.github.tanstacktable.sample

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tanstacktable.compose.flexRender
import io.github.tanstacktable.compose.rememberTable
import io.github.tanstacktable.compose.TableCellText
import io.github.tanstacktable.compose.TableGrid
import io.github.tanstacktable.core.CellContext
import io.github.tanstacktable.core.ColumnDef
import io.github.tanstacktable.core.HeaderContext
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.getCoreRowModel

/**
 * Per-column visibility checkboxes (a Toggle All master plus one per leaf
 * column) with a grouped header so hiding a leaf collapses its slot from
 * every header row above it. The engine owns `columnVisibility`; the
 * checkboxes call `column.toggleVisibility` / `table.toggleAllColumnsVisible`
 * directly and the screen reads the current map back from `getState()`.
 */

/** Rows are `Map<String, Any?>` so the engine's `accessorKey` can index them. */
private fun visibilityPerson(
    firstName: String,
    lastName: String,
    age: Int,
    visits: Int,
    status: String,
    progress: Int,
): Map<String, Any?> = mapOf(
    "firstName" to firstName,
    "lastName" to lastName,
    "age" to age,
    "visits" to visits,
    "status" to status,
    "progress" to progress,
)

private val visibilityDefaultData: List<Map<String, Any?>> = listOf(
    visibilityPerson("tanner", "linsley", 24, 100, "In Relationship", 50),
    visibilityPerson("tandy", "miller", 40, 40, "Single", 80),
    visibilityPerson("joe", "dirte", 45, 20, "Complicated", 10),
)

/**
 * Plain `ColumnDef` object literals — group columns carry a nested `columns`
 * list and no accessor; leaf columns carry `accessorKey` or `accessorFn`.
 */
private val visibilityColumns: List<ColumnDef<Any?, Any?>> = listOf(
    ColumnDef<Any?, Any?>().apply {
        header = "Name"
        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        columns = listOf(
            ColumnDef<Any?, Any?>().apply {
                accessorKey = "firstName"
                cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
                footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
            },
            ColumnDef<Any?, Any?>().apply {
                accessorFn = { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] }
                id = "lastName"
                cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
                header = { _: Any? -> "Last Name" }
                footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
            },
        )
    },
    ColumnDef<Any?, Any?>().apply {
        header = "Info"
        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        columns = listOf(
            ColumnDef<Any?, Any?>().apply {
                accessorKey = "age"
                header = { _: Any? -> "Age" }
                footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
            },
            ColumnDef<Any?, Any?>().apply {
                header = "More Info"
                columns = listOf(
                    ColumnDef<Any?, Any?>().apply {
                        accessorKey = "visits"
                        header = { _: Any? -> "Visits" }
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                    ColumnDef<Any?, Any?>().apply {
                        accessorKey = "status"
                        header = "Status"
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                    ColumnDef<Any?, Any?>().apply {
                        accessorKey = "progress"
                        header = "Profile Progress"
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                )
            },
        )
    },
)

/**
 * Renders the visibility-checkbox panel (Toggle All + one per leaf column),
 * the table itself, and a small readout of the engine's current
 * `columnVisibility` state.
 */
@Composable
fun ColumnVisibilityExample() {
    val data = remember { visibilityDefaultData.toList() }

    val columns = remember { visibilityColumns.toList() }

    // Force-rerender handle for the demo "Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    // The engine manages `columnVisibility` internally; the checkboxes call
    // back into `toggleVisibility` / `toggleAllColumnsVisible` directly.
    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = columns,
            getCoreRowModel = getCoreRowModel(),
        ),
    )

    @Suppress("UNUSED_EXPRESSION")
    rerender

    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Visibility-checkbox panel: a "Toggle All" master plus one per leaf
        // column.
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = table.getIsAllColumnsVisible(),
                    onCheckedChange = { table.toggleAllColumnsVisible(it) },
                )
                Text("Toggle All")
            }
            for (column in table.getAllLeafColumns()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = column.getIsVisible(),
                        onCheckedChange = { column.toggleVisibility(it) },
                    )
                    Text(column.id)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Wrapped in a horizontal scroll: the columns are wider than a phone.
        // Each column is content-sized; a group header's `colSpan` widens to
        // cover the columns it spans.
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            TableGrid {
                // Nested column groups produce MULTIPLE `HeaderGroup`s; each
                // becomes its own header row.
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
                for (footerGroup in table.getFooterGroups()) row {
                    for (header in footerGroup.headers) cell(colSpan = header.colSpan) {
                        if (!header.isPlaceholder) {
                            TableCellText(
                                flexRender(header.column.columnDef.footer, header.getContext()),
                                bold = true,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = { rerender++ }) {
            Text("Rerender")
        }

        Spacer(Modifier.height(16.dp))

        // Small readout of the engine's current `columnVisibility` map.
        Text(
            text = "columnVisibility = ${table.getState().columnVisibility}",
            fontWeight = FontWeight.Normal,
        )
    }
}

// `TableGrid` and `TableCellText` are shared across the example screens; see TableGrid.kt.
