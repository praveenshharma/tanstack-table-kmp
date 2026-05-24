package io.github.tanstacktable.core

/*
 * ============================================================================
 * Core engine smoke tests for the nested "Name" / "Info" column layout.
 *
 * Tests build the table directly through the engine and assert on
 * `getHeaderGroups()` / `getRowModel()` / `getFooterGroups()`. The
 * `resolveTemplate` helper (NestedColumnsTestData.kt) stands in for
 * `flexRender`, which lives in `:table-compose`.
 *
 * Fixtures: `nestedDefaultData` / `nestedDefaultColumns()` from
 * NestedColumnsTestData.kt.
 * ============================================================================
 */

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
class CoreEngineTest {

    /**
     * Builds a static table. Tests in this class do not mutate state, so
     * `onStateChange` is a no-op and the initial `state` is empty.
     */
    private fun makeTable(): Table<Person> {
        return createTable(
            TableOptionsResolved(
                onStateChange = {},
                renderFallbackValue = "",
                data = nestedDefaultData,
                columns = nestedDefaultColumns(),
                state = TableState(),
                getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
            ),
        )
    }

    /**
     * Renders the full nested layout and asserts every header, body cell,
     * and footer value + colSpan, plus the total header/cell counts.
     */
    @Test
    fun rendersATableWithMarkup() {
        val table = makeTable()

        // --- headers --------------------------------------------------------
        val headerGroups = table.getHeaderGroups()
        assertEquals(3, headerGroups.size, "expected 3 header groups")

        // Group row 0: the two top-level group headers.
        assertHeaderRow(
            headerGroups[0],
            listOf("Name" to 2, "Info" to 4),
            "header group 0",
        )
        // Group row 1: three placeholders for firstName/lastName/age (which
        // have no group parent at this depth) plus the "More Info" group.
        assertHeaderRow(
            headerGroups[1],
            listOf("" to 1, "" to 1, "" to 1, "More Info" to 3),
            "header group 1",
            placeholders = listOf(true, true, true, false),
        )
        // Group row 2: the leaf headers. firstName has no header template
        // and resolves through the default to its column id.
        assertHeaderRow(
            headerGroups[2],
            listOf(
                "firstName" to 1,
                "Last Name" to 1,
                "Age" to 1,
                "Visits" to 1,
                "Status" to 1,
                "Profile Progress" to 1,
            ),
            "header group 2",
        )

        // Total header cells across the three groups: 2 + 4 + 6.
        val totalThead = headerGroups.sumOf { it.headers.size }
        assertEquals(12, totalThead, "expected 12 header cells")

        // --- rows -----------------------------------------------------------
        val rows = table.getRowModel().rows
        assertEquals(3, rows.size, "expected 3 body rows")

        val expectedRows = listOf(
            listOf("tanner", "linsley", "29", "100", "In Relationship", "50"),
            listOf("derek", "perkins", "40", "40", "Single", "80"),
            listOf("joe", "bergevin", "45", "20", "Complicated", "10"),
        )
        rows.forEachIndexed { rowIndex, row ->
            val cells = row.getVisibleCells()
            assertEquals(6, cells.size, "row $rowIndex expected 6 cells")
            cells.forEachIndexed { cellIndex, cell ->
                val resolved = resolveTemplate(cell.column.columnDef.cell, cell.getContext())
                assertEquals(
                    expectedRows[rowIndex][cellIndex],
                    resolved,
                    "row $rowIndex cell $cellIndex",
                )
            }
        }
        assertEquals(18, rows.sumOf { it.getVisibleCells().size }, "expected 18 body cells")

        // --- footers --------------------------------------------------------
        // Footer groups are the reverse of header groups. Each footer
        // resolves `columnDef.footer = { ctx -> ctx.column.id }`.
        val footerGroups = table.getFooterGroups()
        assertEquals(3, footerGroups.size, "expected 3 footer groups")

        // Footer row 0 (leaf footers): the column ids.
        assertFooterRow(
            footerGroups[0],
            listOf(
                "firstName" to 1,
                "lastName" to 1,
                "age" to 1,
                "visits" to 1,
                "status" to 1,
                "progress" to 1,
            ),
            "footer group 0",
        )
        // Footer row 1: three placeholders + the "More Info" group footer.
        // "More Info" has no footer template, so it resolves to "".
        assertFooterRow(
            footerGroups[1],
            listOf("" to 1, "" to 1, "" to 1, "" to 3),
            "footer group 1",
            placeholders = listOf(true, true, true, false),
        )
        // Footer row 2: the "Name" / "Info" group footers (column ids).
        assertFooterRow(
            footerGroups[2],
            listOf("Name" to 2, "Info" to 4),
            "footer group 2",
        )
    }

    /**
     * Verifies `getRowModel()` exposes the expected `rows`, `flatRows`, and
     * `rowsById` for a flat dataset.
     */
    @Test
    fun canReturnTheRowModel() {
        val table = makeTable()
        val rowModel = table.getRowModel()

        assertEquals(3, rowModel.rows.size, "rows.size")
        assertEquals(3, rowModel.flatRows.size, "flatRows.size")
        // For a flat dataset the row id is the row index string, and
        // `.original` is the source `Person`.
        assertEquals(
            nestedDefaultData[2],
            rowModel.rowsById["2"]?.original,
            "rowsById[\"2\"].original",
        )
    }

    /**
     * Referential stability of the table instance across re-renders is a
     * concern of the React adapter (`useReactTable`'s memoisation), not of
     * the engine. `:table-core` has no render loop and `createTable` returns
     * a fresh `Table` per call, so there is no engine-level behavior to
     * exercise; this test is intentionally inert and ignored.
     */
    @Test
    @Ignore
    fun hasAStableApi() {
        // Intentionally empty — see the KDoc above.
    }

    // ---------------------------------------------------------------------
    // Test helpers — assert a header / footer row against an expected list
    // of `(value, colSpan)` cells. Placeholder cells are checked directly
    // via `header.isPlaceholder`; otherwise `resolveTemplate` renders the
    // header or footer template.
    // ---------------------------------------------------------------------

    private fun assertHeaderRow(
        group: HeaderGroup<Person>,
        expected: List<Pair<String, Int>>,
        label: String,
        placeholders: List<Boolean> = List(expected.size) { false },
    ) {
        assertEquals(expected.size, group.headers.size, "$label header count")
        group.headers.forEachIndexed { index, header ->
            val (expectedValue, expectedColSpan) = expected[index]
            assertEquals(expectedColSpan, header.colSpan, "$label header $index colSpan")
            if (placeholders[index]) {
                assertTrue(header.isPlaceholder, "$label header $index expected placeholder")
                // A placeholder cell carries no value.
                assertEquals("", expectedValue, "$label header $index placeholder value")
            } else {
                assertFalse(header.isPlaceholder, "$label header $index expected non-placeholder")
                val resolved = resolveTemplate(
                    header.column.columnDef.header,
                    header.getContext(),
                )
                assertEquals(expectedValue, resolved, "$label header $index value")
            }
        }
    }

    private fun assertFooterRow(
        group: HeaderGroup<Person>,
        expected: List<Pair<String, Int>>,
        label: String,
        placeholders: List<Boolean> = List(expected.size) { false },
    ) {
        assertEquals(expected.size, group.headers.size, "$label footer count")
        group.headers.forEachIndexed { index, header ->
            val (expectedValue, expectedColSpan) = expected[index]
            assertEquals(expectedColSpan, header.colSpan, "$label footer $index colSpan")
            if (placeholders[index]) {
                assertTrue(header.isPlaceholder, "$label footer $index expected placeholder")
                assertEquals("", expectedValue, "$label footer $index placeholder value")
            } else {
                assertFalse(header.isPlaceholder, "$label footer $index expected non-placeholder")
                // "More Info" has no footer template, so it resolves to ""
                // through the null branch in `resolveTemplate`.
                val resolved = resolveTemplate(
                    header.column.columnDef.footer,
                    header.getContext(),
                )
                assertEquals(expectedValue, resolved, "$label footer $index value")
            }
        }
    }
}
