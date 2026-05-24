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
 * The simplest end-to-end demo: a static, three-row table rendered through the
 * full engine pipeline (header groups, row model, visible cells, footer groups,
 * all resolved by `flexRender`). Use this as the starting point for reading the
 * other examples — every other screen layers a feature (sorting, filtering,
 * grouping, …) on top of this same skeleton.
 */

/**
 * A row is a `Map<String, Any?>` rather than a typed Kotlin class. The engine's
 * `accessorKey` resolves a value via `originalRow as? Map<String, Any?>`, so a
 * string-keyed map is the natural row shape; a regular Kotlin object is not
 * string-indexable.
 */
private fun person(
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

private val defaultData: List<Map<String, Any?>> = listOf(
    person("tanner", "linsley", 24, 100, "In Relationship", 50),
    person("tandy", "miller", 40, 40, "Single", 80),
    person("joe", "dirte", 45, 20, "Complicated", 10),
)

/**
 * `TData` is `Any?` so rows can be heterogeneous `Map<String, Any?>` values.
 */
private val columnHelper = createColumnHelper<Any?>()

/**
 * Six leaf columns: an `accessorKey` for `firstName`, an `accessorFn` for
 * `lastName`, then four more `accessorKey` columns. Each column gives the
 * engine its `header`, `cell`, and `footer` templates; `header` accepts either
 * a function (resolved with a `HeaderContext`) or a plain string. The
 * `ColumnDefTemplate` type is `Any?`, so lambdas and strings can be assigned
 * directly.
 */
private val columns: List<ColumnDef<Any?, Any?>> = listOf(
    columnHelper.accessor(
        "firstName",
        ColumnDef<Any?, Any?>().apply {
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    columnHelper.accessor(
        { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] },
        ColumnDef<Any?, Any?>().apply {
            id = "lastName"
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            header = { _: Any? -> "Last Name" }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    columnHelper.accessor(
        "age",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Age" }
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).renderValue() }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    columnHelper.accessor(
        "visits",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Visits" }
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    columnHelper.accessor(
        "status",
        ColumnDef<Any?, Any?>().apply {
            header = "Status"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
    columnHelper.accessor(
        "progress",
        ColumnDef<Any?, Any?>().apply {
            header = "Profile Progress"
            footer = { info: Any? -> (info as HeaderContext<Any?, Any?>).column.id }
        },
    ),
)

/**
 * Renders the table: `thead` from `getHeaderGroups()`, `tbody` from
 * `getRowModel().rows` × `getVisibleCells()`, `tfoot` from `getFooterGroups()`,
 * each cell resolved by `flexRender`.
 */
@Composable
fun BasicExample() {
    val data = remember { defaultData.toList() }

    // Force-rerender handle. Compose recomposes automatically on any state
    // change, so this is purely for the demo "Rerender" button below: reading
    // `rerender` subscribes the composable, and the button bumps it.
    var rerender by remember { mutableStateOf(0) }

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
        // The table is wrapped in a horizontal scroll — six columns can be
        // wider than a phone. TableGrid content-sizes every column by
        // default, the Compose equivalent of an HTML `<table>`'s `table-
        // layout: auto`.
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

// `TableCellText` are shared across the example screens; see TableGrid.kt.
