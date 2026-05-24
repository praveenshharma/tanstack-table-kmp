package io.github.tanstacktable.sample

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import io.github.tanstacktable.compose.flexRender
import io.github.tanstacktable.compose.rememberTable
import io.github.tanstacktable.compose.TableCellText
import io.github.tanstacktable.compose.TableGrid
import io.github.tanstacktable.core.CellContext
import io.github.tanstacktable.core.ColumnDef
import io.github.tanstacktable.core.HeaderContext
import io.github.tanstacktable.core.Row as TableRow
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.createColumnHelper
import io.github.tanstacktable.core.getCoreRowModel
import io.github.tanstacktable.core.getExpandedRowModel
import io.github.tanstacktable.core.getFilteredRowModel
import io.github.tanstacktable.core.getPaginationRowModel

/**
 * Hierarchical rows with per-row expand/collapse and a master expand toggle in
 * the header, layered with row-selection checkboxes (parent + sub-row). Wires
 * `getExpandedRowModel`, `getSubRows`, and `enableRowSelection`'s default
 * sub-row propagation; the engine owns `expanded` and `rowSelection`, and the
 * screen reads both back from `getState()`. Pagination and filtering row
 * models are wired so `getRowModel()` runs the full pipeline, but their UI is
 * omitted — this screen is about expansion, not paging.
 *
 * Sub-row indentation is `row.depth * 16.dp` so nested rows stay legible on a
 * phone. Material3 `Checkbox` has no indeterminate visual, so the "some
 * sub-rows selected" hint is omitted from the parent checkbox visuals.
 */

/**
 * Rows are `Map<String, Any?>`; `getSubRows` reads the `"subRows"` key and the
 * engine's `accessorKey` resolves values via `originalRow as? Map<String,
 * Any?>`.
 */
private fun expandingPerson(
    firstName: String,
    lastName: String,
    age: Int,
    visits: Int,
    progress: Int,
    status: String,
    subRows: List<Map<String, Any?>>?,
): Map<String, Any?> = mapOf(
    "firstName" to firstName,
    "lastName" to lastName,
    "age" to age,
    "visits" to visits,
    "progress" to progress,
    "status" to status,
    "subRows" to subRows,
)

private val expandingFirstNamePool =
    listOf("Tanner", "Kevin", "Maria", "Joe", "Sandra", "Harold", "Nadia", "Owen")
private val expandingLastNamePool =
    listOf("Linsley", "Miller", "Dirte", "Vance", "Holt", "Quill", "Reyes", "Snow")
private val expandingStatusPool = listOf("relationship", "complicated", "single")

/**
 * Recursively builds nested rows seeded by `seed`. `lens[depth]` controls the
 * fan-out at each depth — for example `(20, 5, 3)` gives 20 top-level rows,
 * each with 5 sub-rows, each of those with 3 sub-rows.
 */
private fun makeExpandingPeople(vararg lens: Int, seed: Int = 0): List<Map<String, Any?>> {
    // `nodeSeed` keeps deterministic variation distinct per node; the outer
    // `seed` shifts every node so pressing "Refresh Data" visibly changes the
    // tree.
    var nodeSeed = seed
    fun makeDataLevel(depth: Int): List<Map<String, Any?>> {
        val len = lens[depth]
        return (0 until len).map {
            val s = nodeSeed++
            expandingPerson(
                firstName = expandingFirstNamePool[(s + seed * 3) % expandingFirstNamePool.size],
                lastName = expandingLastNamePool[(s * 5 + seed * 7) % expandingLastNamePool.size],
                age = (s * 13 + seed * 5) % 41,
                visits = (s * 137 + seed * 11) % 1001,
                progress = (s * 17 + seed * 9) % 101,
                status = expandingStatusPool[(s * 2 + seed) % expandingStatusPool.size],
                subRows = if (depth + 1 < lens.size) makeDataLevel(depth + 1) else null,
            )
        }
    }
    return makeDataLevel(0)
}

private val expandingColumnHelper = createColumnHelper<Any?>()

/**
 * Six leaf columns. The `firstName` column's `cell` and `header` templates
 * resolve only to text — the interactive widgets (selection checkbox, expand
 * toggle) are drawn by the render loop around the resolved value, since a
 * `ColumnDefTemplate` resolves to a value via `flexRender` rather than to
 * Compose UI. The other five columns are plain accessors.
 */
private val expandingColumns: List<ColumnDef<Any?, Any?>> = listOf(
    expandingColumnHelper.accessor(
        "firstName",
        ColumnDef<Any?, Any?>().apply {
            // The render loop draws the all-rows expander + selection checkbox
            // beside this text.
            header = { _: Any? -> "First Name" }
            // The render loop draws the per-row checkbox + expander beside the
            // resolved value.
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    expandingColumnHelper.accessor(
        { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] },
        ColumnDef<Any?, Any?>().apply {
            id = "lastName"
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            header = { _: Any? -> "Last Name" }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    expandingColumnHelper.accessor(
        "age",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Age" }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    expandingColumnHelper.accessor(
        "visits",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Visits" }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    expandingColumnHelper.accessor(
        "status",
        ColumnDef<Any?, Any?>().apply {
            header = "Status"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    expandingColumnHelper.accessor(
        "progress",
        ColumnDef<Any?, Any?>().apply {
            header = "Profile Progress"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
)

/**
 * Renders the table: the header with the all-rows selection checkbox +
 * all-rows expander on the `firstName` column, and each row with the per-row
 * checkbox + expander indented by `row.depth`.
 */
@Composable
fun ExpandingExample() {
    // Bumping `refresh` rebuilds `data` with a new seed.
    var refresh by remember { mutableStateOf(0) }
    val data = remember(refresh) { makeExpandingPeople(20, 5, 3, seed = refresh) }

    // Force-rerender handle for the demo "Force Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    // The engine manages `expanded` and `rowSelection` internally; click and
    // checkbox lambdas call back into the engine directly.
    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = expandingColumns,
            getSubRows = { row: Any?, _: Int ->
                @Suppress("UNCHECKED_CAST")
                (row as Map<*, *>)["subRows"] as? List<Any?>
            },
            getCoreRowModel = getCoreRowModel(),
            getExpandedRowModel = getExpandedRowModel(),
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
                            // The `firstName` header carries the all-rows
                            // selection checkbox + all-rows expander; other
                            // headers are plain text.
                            if (header.column.id == "firstName") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = table.getIsAllRowsSelected(),
                                        onCheckedChange = {
                                            table.toggleAllRowsSelected(null)
                                        },
                                    )
                                    Text(
                                        text = if (table.getIsAllRowsExpanded()) "v" else ">",
                                        modifier = Modifier
                                            .clickable { table.toggleAllRowsExpanded(null) }
                                            .padding(horizontal = 4.dp),
                                    )
                                    TableCellText(
                                        flexRender(
                                            header.column.columnDef.header,
                                            header.getContext(),
                                        ),
                                        bold = true,
                                    )
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
                        }
                    }
                }
                for (dataRow in table.getRowModel().rows) row {
                    for (dataCell in dataRow.getVisibleCells()) cell {
                        // The `firstName` cell carries the per-row selection
                        // checkbox + expander, indented by depth; other cells
                        // render the plain `flexRender` value.
                        if (dataCell.column.id == "firstName") {
                            ExpandingFirstNameCell(
                                row = dataRow,
                                value = flexRender(
                                    dataCell.column.columnDef.cell,
                                    dataCell.getContext(),
                                ),
                            )
                        } else {
                            TableCellText(
                                flexRender(dataCell.column.columnDef.cell, dataCell.getContext()),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        TableCellText("${table.getRowModel().rows.size} Rows")

        // Small readouts of the engine's expansion + selection state.
        TableCellText("Expanded State: ${table.getState().expanded}")
        TableCellText("Row Selection State: ${table.getState().rowSelection}")

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

/**
 * The `firstName` cell content: a selection checkbox, an expand toggle (or a
 * leaf marker when the row has no children), and the resolved value, indented
 * by `row.depth`. Drawn outside the engine template because templates resolve
 * to a value, not Compose UI.
 */
@Composable
private fun ExpandingFirstNameCell(row: TableRow<Any?>, value: Any?) {
    Row(
        modifier = Modifier.padding(start = (row.depth * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Material3 Checkbox has no indeterminate visual, so
        // `row.getIsSomeSelected()` is not surfaced here.
        Checkbox(
            checked = row.getIsSelected(),
            onCheckedChange = { checked -> row.toggleSelected(checked, null) },
        )
        if (row.getCanExpand()) {
            Text(
                text = if (row.getIsExpanded()) "v" else ">",
                modifier = Modifier
                    .clickable { row.toggleExpanded(null) }
                    .padding(horizontal = 4.dp),
            )
        } else {
            // Leaf-row marker.
            Text(text = "*", modifier = Modifier.padding(horizontal = 4.dp))
        }
        TableCellText(value)
    }
}
