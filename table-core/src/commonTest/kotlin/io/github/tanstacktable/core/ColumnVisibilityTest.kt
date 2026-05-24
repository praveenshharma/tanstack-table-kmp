package io.github.tanstacktable.core

/*
 * ============================================================================
 * Column visibility behavior for the nested "Name" / "Info" layout.
 *
 * Drives one table through four toggle states and asserts the resulting
 * `{ headers, rows, footers }` structure after each toggle. The test calls
 * `toggleAllColumnsVisible` / `column.toggleVisibility` directly rather than
 * going through the DOM-event-style handlers (those expect a browser event,
 * see ColumnVisibility.kt).
 *
 * State propagation: the engine routes `onColumnVisibilityChange` through
 * `makeStateUpdater("columnVisibility", table)` -> `table.setState(updater)`
 * -> `options.onStateChange(updater)`. The harness wires `onStateChange` to
 * apply the updater in place. `table` is `lateinit` so that closure can
 * capture it before `createTable` returns.
 *
 * Fixtures: `nestedDefaultData` / `nestedDefaultColumns()` from
 * NestedColumnsTestData.kt. `resolveTemplate` (same file) stands in for
 * `flexRender`, which lives in `:table-compose`.
 * ============================================================================
 */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
class ColumnVisibilityTest {

    // `lateinit` so the `onStateChange` closure can capture the table and
    // read `table.options.state` when applying an update.
    private lateinit var table: Table<Person>

    /**
     * Toggles column visibility across four states (all off, all on,
     * firstName off, "More Info" off) and asserts the headers, rows, and
     * footers after each transition.
     */
    @Test
    fun canToggleColumnVisibility() {
        table = createTable(
            TableOptionsResolved(
                // Apply the updater in place so subsequent reads of
                // `table.options.state` see the new value.
                onStateChange = { updater ->
                    table.options.state = functionalUpdate(updater, table.options.state)
                },
                renderFallbackValue = "",
                data = nestedDefaultData,
                columns = nestedDefaultColumns(),
                // columnVisibility starts empty.
                state = TableState(),
                getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
            ),
        )

        // --- state 0: every leaf column hidden ------------------------------
        table.toggleAllColumnsVisible(false)
        // With every leaf hidden, a single empty header/footer group remains
        // and the three body rows are empty.
        assertState(
            headers = listOf(emptyList()),
            rows = listOf(emptyList(), emptyList(), emptyList()),
            footers = listOf(emptyList()),
            label = "state 0 (all off)",
        )

        // --- state 1: every column visible ----------------------------------
        table.toggleAllColumnsVisible(true)
        // The full nested layout.
        assertState(
            headers = listOf(
                listOf(HCell("Name", 2), HCell("Info", 4)),
                listOf(
                    HCell.placeholder(),
                    HCell.placeholder(),
                    HCell.placeholder(),
                    HCell("More Info", 3),
                ),
                listOf(
                    HCell("firstName", 1),
                    HCell("Last Name", 1),
                    HCell("Age", 1),
                    HCell("Visits", 1),
                    HCell("Status", 1),
                    HCell("Profile Progress", 1),
                ),
            ),
            rows = listOf(
                listOf("tanner", "linsley", "29", "100", "In Relationship", "50"),
                listOf("derek", "perkins", "40", "40", "Single", "80"),
                listOf("joe", "bergevin", "45", "20", "Complicated", "10"),
            ),
            footers = listOf(
                listOf(
                    HCell("firstName", 1),
                    HCell("lastName", 1),
                    HCell("age", 1),
                    HCell("visits", 1),
                    HCell("status", 1),
                    HCell("progress", 1),
                ),
                listOf(
                    HCell.placeholder(),
                    HCell.placeholder(),
                    HCell.placeholder(),
                    HCell("", 3),
                ),
                listOf(HCell("Name", 2), HCell("Info", 4)),
            ),
            label = "state 1 (all on)",
        )

        // --- state 2: firstName hidden --------------------------------------
        firstNameColumn().toggleVisibility(false)
        // "Name" group now spans 1, the depth-1 placeholder count drops to 2,
        // and firstName's header/footer cells are gone.
        assertState(
            headers = listOf(
                listOf(HCell("Name", 1), HCell("Info", 4)),
                listOf(
                    HCell.placeholder(),
                    HCell.placeholder(),
                    HCell("More Info", 3),
                ),
                listOf(
                    HCell("Last Name", 1),
                    HCell("Age", 1),
                    HCell("Visits", 1),
                    HCell("Status", 1),
                    HCell("Profile Progress", 1),
                ),
            ),
            rows = listOf(
                listOf("linsley", "29", "100", "In Relationship", "50"),
                listOf("perkins", "40", "40", "Single", "80"),
                listOf("bergevin", "45", "20", "Complicated", "10"),
            ),
            footers = listOf(
                listOf(
                    HCell("lastName", 1),
                    HCell("age", 1),
                    HCell("visits", 1),
                    HCell("status", 1),
                    HCell("progress", 1),
                ),
                listOf(
                    HCell.placeholder(),
                    HCell.placeholder(),
                    HCell("", 3),
                ),
                listOf(HCell("Name", 1), HCell("Info", 4)),
            ),
            label = "state 2 (firstName off)",
        )

        // --- state 3: "More Info" group hidden, firstName restored ----------
        // Restore firstName, then hide every leaf under "More Info".
        firstNameColumn().toggleVisibility(true)
        leafColumn("visits").toggleVisibility(false)
        leafColumn("status").toggleVisibility(false)
        leafColumn("progress").toggleVisibility(false)
        // With "More Info" fully hidden only 2 header rows remain (the depth
        // collapses), and "Info" spans 1.
        assertState(
            headers = listOf(
                listOf(HCell("Name", 2), HCell("Info", 1)),
                listOf(
                    HCell("firstName", 1),
                    HCell("Last Name", 1),
                    HCell("Age", 1),
                ),
            ),
            rows = listOf(
                listOf("tanner", "linsley", "29"),
                listOf("derek", "perkins", "40"),
                listOf("joe", "bergevin", "45"),
            ),
            footers = listOf(
                listOf(
                    HCell("firstName", 1),
                    HCell("lastName", 1),
                    HCell("age", 1),
                ),
                listOf(HCell("Name", 2), HCell("Info", 1)),
            ),
            label = "state 3 (More Info off)",
        )
    }

    // ---------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------

    /** Looks up the leaf column whose id is `"firstName"`. */
    private fun firstNameColumn(): Column<Person, Any?> = leafColumn("firstName")

    private fun leafColumn(id: String): Column<Person, Any?> =
        table.getAllLeafColumns().first { it.id == id }

    /**
     * Expected header/footer cell: resolved `value`, `colSpan`, and whether
     * the cell is a placeholder (placeholder cells render no value).
     */
    private class HCell(
        val value: String,
        val colSpan: Int,
        val isPlaceholder: Boolean = false,
    ) {
        companion object {
            /** A placeholder cell renders as empty value with colSpan 1. */
            fun placeholder() = HCell("", 1, isPlaceholder = true)
        }
    }

    /**
     * Asserts the full `{ headers, rows, footers }` shape after a toggle.
     * `headers`/`footers` are lists of header rows; `rows` is a list of body
     * rows whose cells are the resolved cell strings.
     */
    private fun assertState(
        headers: List<List<HCell>>,
        rows: List<List<String>>,
        footers: List<List<HCell>>,
        label: String,
    ) {
        // Header rows resolve `columnDef.header`; footer rows resolve
        // `columnDef.footer`. The two getters can produce different values
        // for the same column, so each is asserted against its own template.
        assertHeaderGroups(table.getHeaderGroups(), headers, "$label headers", isFooter = false)
        assertHeaderGroups(table.getFooterGroups(), footers, "$label footers", isFooter = true)

        val rowModel = table.getRowModel().rows
        assertEquals(rows.size, rowModel.size, "$label row count")
        rowModel.forEachIndexed { rowIndex, row ->
            val cells = row.getVisibleCells()
            val expectedCells = rows[rowIndex]
            assertEquals(expectedCells.size, cells.size, "$label row $rowIndex cell count")
            cells.forEachIndexed { cellIndex, cell ->
                val resolved = resolveTemplate(cell.column.columnDef.cell, cell.getContext())
                assertEquals(
                    expectedCells[cellIndex],
                    resolved,
                    "$label row $rowIndex cell $cellIndex",
                )
            }
        }
    }

    private fun assertHeaderGroups(
        groups: List<HeaderGroup<Person>>,
        expected: List<List<HCell>>,
        label: String,
        isFooter: Boolean,
    ) {
        assertEquals(expected.size, groups.size, "$label group count")
        groups.forEachIndexed { groupIndex, group ->
            val expectedRow = expected[groupIndex]
            assertEquals(
                expectedRow.size,
                group.headers.size,
                "$label group $groupIndex header count",
            )
            group.headers.forEachIndexed { headerIndex, header ->
                val expectedCell = expectedRow[headerIndex]
                assertEquals(
                    expectedCell.colSpan,
                    header.colSpan,
                    "$label group $groupIndex header $headerIndex colSpan",
                )
                if (expectedCell.isPlaceholder) {
                    assertTrue(
                        header.isPlaceholder,
                        "$label group $groupIndex header $headerIndex expected placeholder",
                    )
                } else {
                    assertFalse(
                        header.isPlaceholder,
                        "$label group $groupIndex header $headerIndex expected non-placeholder",
                    )
                    // Pick the template matching the getter this group came
                    // from. "More Info" has no footer template, so its footer
                    // resolves to "" through the null branch in
                    // `resolveTemplate`.
                    val template = if (isFooter) {
                        header.column.columnDef.footer
                    } else {
                        header.column.columnDef.header
                    }
                    val resolved = resolveTemplate(template, header.getContext())
                    assertEquals(
                        expectedCell.value,
                        resolved,
                        "$label group $groupIndex header $headerIndex value",
                    )
                }
            }
        }
    }
}
