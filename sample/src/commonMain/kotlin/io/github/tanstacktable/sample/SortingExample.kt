package io.github.tanstacktable.sample

import androidx.compose.foundation.clickable
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
import io.github.tanstacktable.core.Row as TableRow
import io.github.tanstacktable.core.SortingFn
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.createColumnHelper
import io.github.tanstacktable.core.getCoreRowModel
import io.github.tanstacktable.core.getSortedRowModel

/**
 * Click-to-sort headers (multi-column on subsequent clicks), a custom per-column
 * sort function (`status` orders by a domain-specific list), and the
 * `sortUndefined`, `sortDescFirst`, and `invertSorting` per-column options.
 * `createdAt` is stored as a `Long` epoch ms so the built-in `datetime` sort
 * function applies. The engine owns `sorting`; click lambdas call
 * `column.toggleSorting(...)` and the readout at the bottom shows the engine's
 * current sort state.
 */

/**
 * Rows are `Map<String, Any?>`. `lastName` and `visits` are nullable, with
 * every tenth row set to `null` so `sortUndefined` is exercised.
 */
private fun sortingPerson(
    firstName: String,
    lastName: String?,
    age: Int,
    visits: Int?,
    progress: Int,
    status: String,
    rank: Int,
    createdAt: Long,
): Map<String, Any?> = mapOf(
    "firstName" to firstName,
    "lastName" to lastName,
    "age" to age,
    "visits" to visits,
    "progress" to progress,
    "status" to status,
    "rank" to rank,
    "createdAt" to createdAt,
)

private val sortingFirstNames = listOf(
    "Tanner", "Tandy", "Joe", "Maya", "Liam", "Noah", "Olivia", "Ava",
    "Ethan", "Sophia", "Lucas", "Mia", "Aiden", "Isla", "Caleb", "Zoe",
)
private val sortingLastNames = listOf(
    "Linsley", "Miller", "Dirte", "Stone", "Vance", "Quinn", "Reed", "Park",
    "Howe", "Cruz", "Bauer", "Frost", "Nash", "Wells", "Ortiz", "Day",
)
private val sortingStatuses = listOf("relationship", "complicated", "single")

/**
 * Builds 100 deterministic rows; every tenth row has a `null` `lastName` and
 * every tenth (offset by 5) has a `null` `visits` so the `sortUndefined`
 * column option visibly takes effect.
 */
private fun makeSortingData(count: Int, seed: Int = 0): List<Map<String, Any?>> {
    val baseEpoch = 1_577_836_800_000L // 2020-01-01T00:00:00Z
    val dayMs = 86_400_000L
    return (0 until count).map { i ->
        sortingPerson(
            firstName = sortingFirstNames[(i + seed) % sortingFirstNames.size],
            lastName = if (i % 10 == 0) null else sortingLastNames[(i * 7 + seed * 3) % sortingLastNames.size],
            age = (i * 13 + seed * 5) % 41,
            visits = if (i % 10 == 5) null else (i * 137 + seed * 11) % 1001,
            progress = (i * 29 + seed * 7) % 101,
            status = sortingStatuses[(i * 5 + seed) % sortingStatuses.size],
            rank = (i * 17 + seed * 9) % 101,
            createdAt = baseEpoch + ((i * 9_973 + seed * 131) % 2_000) * dayMs,
        )
    }
}

/**
 * Domain-specific sort for the `status` column: orders by the priority list
 * `[single, complicated, relationship]` rather than alphabetically.
 */
private val sortStatusFn: SortingFn<Any?> = { rowA: TableRow<Any?>, rowB: TableRow<Any?>, _columnId: String ->
    val statusA = (rowA.original as Map<*, *>)["status"]
    val statusB = (rowB.original as Map<*, *>)["status"]
    val statusOrder = listOf("single", "complicated", "relationship")
    statusOrder.indexOf(statusA) - statusOrder.indexOf(statusB)
}

private val sortingColumnHelper = createColumnHelper<Any?>()

/**
 * Eight columns demonstrating the sort options:
 *  - `firstName` / `age` / `progress` / `createdAt` use the engine defaults.
 *  - `lastName` sets `sortUndefined = "last"` and `sortDescFirst = false`.
 *  - `visits` sets `sortUndefined = "last"`.
 *  - `status` overrides `sortingFn` with [sortStatusFn].
 *  - `rank` sets `invertSorting = true` (smaller is better, golf-style).
 */
private val sortingColumns: List<ColumnDef<Any?, Any?>> = listOf(
    sortingColumnHelper.accessor(
        "firstName",
        ColumnDef<Any?, Any?>().apply {
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
        },
    ),
    sortingColumnHelper.accessor(
        { row: Any?, _: Int -> (row as Map<*, *>)["lastName"] },
        ColumnDef<Any?, Any?>().apply {
            id = "lastName"
            cell = { info: Any? -> (info as CellContext<Any?, Any?>).getValue() }
            header = { _: Any? -> "Last Name" }
            sortUndefined = "last"
            sortDescFirst = false
        },
    ),
    sortingColumnHelper.accessor(
        "age",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Age" }
        },
    ),
    sortingColumnHelper.accessor(
        "visits",
        ColumnDef<Any?, Any?>().apply {
            header = { _: Any? -> "Visits" }
            sortUndefined = "last"
        },
    ),
    sortingColumnHelper.accessor(
        "status",
        ColumnDef<Any?, Any?>().apply {
            header = "Status"
            sortingFn = sortStatusFn
        },
    ),
    sortingColumnHelper.accessor(
        "progress",
        ColumnDef<Any?, Any?>().apply {
            header = "Profile Progress"
        },
    ),
    sortingColumnHelper.accessor(
        "rank",
        ColumnDef<Any?, Any?>().apply {
            header = "Rank"
            invertSorting = true
        },
    ),
    sortingColumnHelper.accessor(
        "createdAt",
        ColumnDef<Any?, Any?>().apply {
            header = "Created At"
        },
    ),
)

@Composable
fun SortingExample() {
    // Force-rerender handle for the demo "Force Rerender" button below.
    var rerender by remember { mutableStateOf(0) }

    // Bumping `refreshSeed` re-seeds `data` so "Refresh Data" produces a
    // visibly different result.
    var refreshSeed by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf(makeSortingData(100)) }

    // The engine manages `sorting` internally; header click lambdas call
    // `column.toggleSorting(...)` directly.
    val table = rememberTable(
        TableOptions<Any?>(
            columns = sortingColumns,
            data = data,
            getCoreRowModel = getCoreRowModel(),
            getSortedRowModel = getSortedRowModel(), // client-side sorting
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

        // Wrapped in a horizontal scroll: eight columns are wider than a
        // phone. Every column is content-sized.
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            TableGrid {
                for (headerGroup in table.getHeaderGroups()) row {
                    for (header in headerGroup.headers) cell(colSpan = header.colSpan) {
                        if (!header.isPlaceholder) {
                            val column = header.column
                            // Header click toggles sorting when the column
                            // supports it.
                            val canSort = column.getCanSort()
                            val cellModifier = if (canSort) {
                                Modifier.clickable {
                                    column.toggleSorting(null, false)
                                }
                            } else {
                                Modifier
                            }
                            Column(cellModifier) {
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
                        }
                    }
                }
                // Cap the displayed rows to the first 10 so sorting changes
                // are obvious without scrolling the full 100-row dataset.
                for (dataRow in table.getRowModel().rows.take(10)) row {
                    for (dataCell in dataRow.getVisibleCells()) cell {
                        TableCellText(flexRender(dataCell.column.columnDef.cell, dataCell.getContext()))
                    }
                }
            }
        }

        Text("${table.getRowModel().rows.size} Rows")

        Button(onClick = { rerender++ }) {
            Text("Force Rerender")
        }

        Button(onClick = {
            refreshSeed++
            data = makeSortingData(100, seed = refreshSeed)
        }) {
            Text("Refresh Data")
        }

        // Small readout of the engine's current sorting state.
        Text("sorting: " + table.getState().sorting?.joinToString { "${it.id}:${if (it.desc) "desc" else "asc"}" }.orEmpty())
    }
}
