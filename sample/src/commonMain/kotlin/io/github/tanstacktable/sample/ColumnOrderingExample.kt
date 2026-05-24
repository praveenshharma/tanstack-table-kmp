package io.github.tanstacktable.sample

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import kotlin.random.Random

/**
 * Column visibility checkboxes plus a Shuffle Columns button that demonstrates
 * `table.setColumnOrder(...)` reordering the visible columns at runtime. The
 * column tree is grouped (Name + Info + nested More Info) so shuffling and
 * hiding columns can be seen both at the leaf level and through the group
 * headers above them. The engine owns both `columnVisibility` and
 * `columnOrder`; the screen reads them back from `table.getState()`.
 */

/**
 * Fixed pools used by [orderingMakeData] to build reproducible-yet-varied row
 * values across the 20 demo rows.
 */
private val orderingFirstNames = listOf(
    "Tanner", "Kevin", "Maria", "Liam", "Noah", "Olivia", "Emma", "Ava",
    "Sophia", "Lucas", "Mason", "Mia", "Ethan", "Isabella", "James", "Amelia",
    "Benjamin", "Harper", "Elijah", "Charlotte",
)
private val orderingLastNames = listOf(
    "Linsley", "Vandy", "Dirte", "Smith", "Johnson", "Williams", "Brown",
    "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez", "Lopez",
    "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson",
)

private val orderingStatuses = listOf("relationship", "complicated", "single")

/**
 * Builds 20 demo rows seeded by `seed` so pressing the "Regenerate" button
 * produces a fresh — but reproducible — data set. Rows are
 * `Map<String, Any?>` because the engine's `accessorKey` resolves a value via
 * `originalRow as? Map<String, Any?>`.
 */
private fun orderingMakeData(count: Int, seed: Int): List<Map<String, Any?>> {
    val random = Random(seed)
    return (0 until count).map {
        mapOf(
            "firstName" to orderingFirstNames[random.nextInt(orderingFirstNames.size)],
            "lastName" to orderingLastNames[random.nextInt(orderingLastNames.size)],
            "age" to random.nextInt(0, 41),
            "visits" to random.nextInt(0, 1001),
            "progress" to random.nextInt(0, 101),
            "status" to orderingStatuses[random.nextInt(orderingStatuses.size)],
        )
    }
}

/**
 * Plain `ColumnDef` object literals with nested `columns` group columns — two
 * top-level groups (`Name`, `Info`), with `Info` containing a nested `More
 * Info` sub-group. Defined separately from the visibility example's columns
 * because the engine mutates `Column` instances per table.
 */
private val orderingColumns: List<ColumnDef<Any?, Any?>> = listOf(
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
 * Renders the visibility-checkbox panel, the Regenerate / Shuffle Columns
 * buttons, the table itself, and a small readout of the current
 * `columnOrder` engine state.
 */
@Composable
fun ColumnOrderingExample() {
    // `seed` doubles as the data seed — pressing Regenerate bumps it, which
    // both reseeds `orderingMakeData` and triggers recomposition.
    var seed by remember { mutableStateOf(0) }
    val data = remember(seed) { orderingMakeData(20, seed) }

    val columns = remember { orderingColumns.toList() }

    // The engine manages `columnVisibility` and `columnOrder` internally; the
    // checkboxes and Shuffle Columns button call back into the engine directly.
    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = columns,
            getCoreRowModel = getCoreRowModel(),
        ),
    )

    val randomizeColumns = {
        table.setColumnOrder(table.getAllLeafColumns().map { it.id }.shuffled())
    }

    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Visibility-checkbox panel: a "Toggle All" master plus one per leaf
        // column, each bound directly to the engine's visibility API.
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { seed++ }) {
                Text("Regenerate")
            }
            Button(onClick = { randomizeColumns() }) {
                Text("Shuffle Columns")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Wrapped in a horizontal scroll: the columns are wider than a phone.
        // Each column is content-sized, and a group header's `colSpan` widens
        // to cover the columns it spans.
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

        // Small readout of the engine's current column order.
        Text(
            text = "columnOrder = ${table.getState().columnOrder}",
            fontWeight = FontWeight.Normal,
        )
    }
}

// `TableGrid` and `TableCellText` are shared across the example screens; see TableGrid.kt.
