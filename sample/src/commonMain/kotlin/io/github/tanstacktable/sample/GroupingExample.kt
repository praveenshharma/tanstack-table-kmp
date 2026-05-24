package io.github.tanstacktable.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.tanstacktable.compose.flexRender
import io.github.tanstacktable.compose.rememberTable
import io.github.tanstacktable.compose.TableCellText
import io.github.tanstacktable.compose.TableGrid
import io.github.tanstacktable.core.CellContext
import io.github.tanstacktable.core.ColumnDef
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.createColumnHelper
import io.github.tanstacktable.core.getCoreRowModel
import io.github.tanstacktable.core.getExpandedRowModel
import io.github.tanstacktable.core.getFilteredRowModel
import io.github.tanstacktable.core.getGroupedRowModel
import io.github.tanstacktable.core.getPaginationRowModel
import kotlin.math.roundToInt

/**
 * Per-header "GROUP" toggles plus per-grouped-row expanders. Demonstrates
 * `column.toggleGrouping()`, the engine's grouped / aggregated / placeholder
 * cell branch (`cell.getIsGrouped()` / `getIsAggregated()` /
 * `getIsPlaceholder()`), and the built-in aggregation functions wired by name
 * (`"median"`, `"sum"`, `"mean"`). Grouped rows can be expanded inline via
 * `row.toggleExpanded`; the engine owns both `grouping` and `expanded`.
 * Pagination + filtering row models are wired so `getRowModel()` runs the full
 * pipeline, but their UI is omitted — this screen is about grouping.
 */

/** Rows are `Map<String, Any?>` so the engine's `accessorKey` can index them. */
private fun groupingPerson(
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

// 30 deterministic rows is small enough to inspect on screen yet has enough
// repeated `firstName` / `status` values to form multi-row groups.
private val groupingFirstNamePool = listOf("Tanner", "Kevin", "Maria", "Joe", "Sandra")
private val groupingLastNamePool = listOf("Linsley", "Miller", "Dirte", "Vance", "Holt")
private val groupingStatusPool = listOf("relationship", "complicated", "single")

private fun makeGroupingPeople(count: Int, seed: Int = 0): List<Map<String, Any?>> =
    (0 until count).map { i ->
        groupingPerson(
            firstName = groupingFirstNamePool[(i + seed) % groupingFirstNamePool.size],
            lastName = groupingLastNamePool[(i / 5 + seed * 3) % groupingLastNamePool.size],
            age = (i * 13 + seed * 5) % 41,
            visits = (i * 137 + seed * 11) % 1001,
            progress = (i * 17 + seed * 7) % 101,
            status = groupingStatusPool[(i * 2 + seed) % groupingStatusPool.size],
        )
    }

private val groupingColumnHelper = createColumnHelper<Any?>()

/** Rounds a numeric value to two decimals; used by the aggregated cells below. */
private fun round2(value: Any?): Double {
    val n = (value as? Number)?.toDouble() ?: 0.0
    return (n * 100.0).roundToInt() / 100.0
}

/**
 * Two top-level group columns: `Name` (`firstName` + `lastName`) and `Info`
 * (`age` plus a nested `More Info` sub-group with `visits`, `status`,
 * `progress`). `firstName` overrides its grouping key with `getGroupingValue`
 * so rows group on the full `"first last"` string. The `age`, `visits`, and
 * `progress` columns set an `aggregationFn` (`"median"`, `"sum"`, `"mean"`),
 * one of the engine's built-in functions registered by name.
 */
private val groupingColumns: List<ColumnDef<Any?, Any?>> = listOf(
    groupingColumnHelper.group(
        ColumnDef<Any?, Any?>().apply {
            header = "Name"
            columns = listOf(
                groupingColumnHelper.accessor(
                    "firstName",
                    ColumnDef<Any?, Any?>().apply {
                        header = "First Name"
                        cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
                        // Override the grouping key: group on "first last".
                        getGroupingValue = { row: Any? ->
                            val m = row as Map<*, *>
                            "${m["firstName"]} ${m["lastName"]}"
                        }
                    },
                ),
                groupingColumnHelper.accessor(
                    { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] },
                    ColumnDef<Any?, Any?>().apply {
                        id = "lastName"
                        header = { _: Any? -> "Last Name" }
                        cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
                    },
                ),
            )
        },
    ),
    groupingColumnHelper.group(
        ColumnDef<Any?, Any?>().apply {
            header = "Info"
            columns = listOf(
                groupingColumnHelper.accessor(
                    "age",
                    ColumnDef<Any?, Any?>().apply {
                        header = { _: Any? -> "Age" }
                        aggregatedCell = { info: Any? ->
                            round2((info as CellContext<Any?, Any?>).getValue())
                        }
                        aggregationFn = "median"
                    },
                ),
                groupingColumnHelper.group(
                    ColumnDef<Any?, Any?>().apply {
                        header = "More Info"
                        columns = listOf(
                            groupingColumnHelper.accessor(
                                "visits",
                                ColumnDef<Any?, Any?>().apply {
                                    header = { _: Any? -> "Visits" }
                                    aggregationFn = "sum"
                                },
                            ),
                            groupingColumnHelper.accessor(
                                "status",
                                ColumnDef<Any?, Any?>().apply {
                                    header = "Status"
                                },
                            ),
                            groupingColumnHelper.accessor(
                                "progress",
                                ColumnDef<Any?, Any?>().apply {
                                    header = "Profile Progress"
                                    cell = { info: Any? ->
                                        round2((info as CellContext<Any?, Any?>).getValue()).toString() + "%"
                                    }
                                    aggregationFn = "mean"
                                    aggregatedCell = { info: Any? ->
                                        round2((info as CellContext<Any?, Any?>).getValue()).toString() + "%"
                                    }
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
 * Renders the multi-row header with per-column GROUP buttons, the body with
 * the grouped / aggregated / placeholder cell branch, and the demo controls.
 */
@Composable
fun GroupingExample() {
    // Bumping `refresh` rebuilds `data` with a new seed.
    var refresh by remember { mutableStateOf(0) }
    val data = remember(refresh) { makeGroupingPeople(30, seed = refresh) }

    // Force-rerender handle for the demo "Force Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    // The engine manages `grouping` and `expanded` internally; the header
    // GROUP buttons and per-row expanders call back directly.
    val table = rememberTable(
        TableOptions<Any?>(
            data = data,
            columns = groupingColumns,
            getCoreRowModel = getCoreRowModel(),
            getGroupedRowModel = getGroupedRowModel(),
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
                    for (header in headerGroup.headers) {
                        // A group header spans its leaf columns via `colSpan`.
                        cell(colSpan = header.colSpan) {
                            if (!header.isPlaceholder) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (header.column.getCanGroup()) {
                                        val toggleLabel = if (header.column.getIsGrouped()) {
                                            "STOP(${header.column.getGroupedIndex()})"
                                        } else {
                                            "GROUP"
                                        }
                                        Button(onClick = { header.column.toggleGrouping() }) {
                                            Text(toggleLabel)
                                        }
                                        Spacer(Modifier.width(4.dp))
                                    }
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
                        // Per-state tint: green for the grouped key cell,
                        // orange for an aggregated cell, faint red for a
                        // placeholder (repeated) cell.
                        val tint = when {
                            dataCell.getIsGrouped() -> Color(0x820AFF00)
                            dataCell.getIsAggregated() -> Color(0x78FFA500)
                            dataCell.getIsPlaceholder() -> Color(0x42FF0000)
                            else -> Color.White
                        }
                        Box(Modifier.background(tint)) {
                            when {
                                // Grouped cell: expander + value + sub-row count.
                                dataCell.getIsGrouped() -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (dataRow.getIsExpanded()) "v" else ">",
                                            modifier = Modifier
                                                .clickable(enabled = dataRow.getCanExpand()) {
                                                    dataRow.toggleExpanded(null)
                                                }
                                                .padding(end = 4.dp),
                                        )
                                        TableCellText(
                                            flexRender(
                                                dataCell.column.columnDef.cell,
                                                dataCell.getContext(),
                                            ),
                                        )
                                        TableCellText(" (${dataRow.subRows.size})")
                                    }
                                }
                                // Aggregated cell: prefer `aggregatedCell`,
                                // fall back to `cell`.
                                dataCell.getIsAggregated() -> {
                                    TableCellText(
                                        flexRender(
                                            dataCell.column.columnDef.aggregatedCell
                                                ?: dataCell.column.columnDef.cell,
                                            dataCell.getContext(),
                                        ),
                                    )
                                }
                                // Placeholder (repeated value): render nothing.
                                dataCell.getIsPlaceholder() -> Unit
                                // Regular cell.
                                else -> {
                                    TableCellText(
                                        flexRender(
                                            dataCell.column.columnDef.cell,
                                            dataCell.getContext(),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        TableCellText("${table.getRowModel().rows.size} Rows")

        // Small readout of the engine's current grouping state.
        TableCellText("grouping: ${table.getState().grouping}")

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
