package io.github.tanstacktable.sample

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Multi-row header built from nested column groups. Shows how
 * `columnHelper.group(...)` composes sub-`columns` lists so the engine emits
 * one `HeaderGroup` per nesting level, each group header's `colSpan` widening
 * to cover the leaf columns it contains. This screen is intentionally static —
 * no sorting, filtering, or other feature state — to keep the focus on the
 * grouped header layout itself.
 */

/** Rows are `Map<String, Any?>` so the engine's `accessorKey` can index them. */
private fun groupsPerson(
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

private val groupsDefaultData: List<Map<String, Any?>> = listOf(
    groupsPerson("tanner", "linsley", 24, 100, "In Relationship", 50),
    groupsPerson("tandy", "miller", 40, 40, "Single", 80),
    groupsPerson("joe", "dirte", 45, 20, "Complicated", 10),
)

private val groupsColumnHelper = createColumnHelper<Any?>()

/**
 * Two top-level group columns: `Hello` (containing `firstName` + `lastName`)
 * and `Info` (containing `age` plus a nested `More Info` sub-group with
 * `visits`, `status`, `progress`). `columnHelper.group` is the identity
 * function — a group column is a `ColumnDef` that carries a `columns` list and
 * no accessor.
 */
private val groupsColumns: List<ColumnDef<Any?, Any?>> = listOf(
    groupsColumnHelper.group(
        ColumnDef<Any?, Any?>().apply {
            id = "hello"
            header = { _: Any? -> "Hello" }
            columns = listOf(
                groupsColumnHelper.accessor(
                    "firstName",
                    ColumnDef<Any?, Any?>().apply {
                        cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                ),
                groupsColumnHelper.accessor(
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
    groupsColumnHelper.group(
        ColumnDef<Any?, Any?>().apply {
            header = "Info"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
            columns = listOf(
                groupsColumnHelper.accessor(
                    "age",
                    ColumnDef<Any?, Any?>().apply {
                        header = { _: Any? -> "Age" }
                        footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                    },
                ),
                groupsColumnHelper.group(
                    ColumnDef<Any?, Any?>().apply {
                        header = "More Info"
                        columns = listOf(
                            groupsColumnHelper.accessor(
                                "visits",
                                ColumnDef<Any?, Any?>().apply {
                                    header = { _: Any? -> "Visits" }
                                    footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                                },
                            ),
                            groupsColumnHelper.accessor(
                                "status",
                                ColumnDef<Any?, Any?>().apply {
                                    header = "Status"
                                    footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
                                },
                            ),
                            groupsColumnHelper.accessor(
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

/**
 * Renders the table: `thead` from `getHeaderGroups()` (MULTIPLE header rows
 * for the nested groups), `tbody` from `getRowModel().rows` × `getVisibleCells()`,
 * `tfoot` from `getFooterGroups()`, each cell resolved by `flexRender`.
 */
@Composable
fun ColumnGroupsExample() {
    val data = remember { groupsDefaultData.toList() }

    // Force-rerender handle for the demo "Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = groupsColumns,
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
        // Wrapped in a horizontal scroll — five leaf columns are wider than a
        // phone. Each column is content-sized; a group header's `colSpan`
        // widens to cover the leaves it contains.
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            TableGrid {
                // Nested column groups produce MULTIPLE `HeaderGroup`s from
                // `getHeaderGroups()`; each becomes its own header row.
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
    }
}

// `TableGrid` and `TableCellText` are shared across the example screens; see TableGrid.kt.
