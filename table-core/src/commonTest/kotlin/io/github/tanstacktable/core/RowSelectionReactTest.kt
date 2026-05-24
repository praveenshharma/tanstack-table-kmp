package io.github.tanstacktable.core

/*
 * ============================================================================
 * Row-selection behavior driven through the table's selection getters.
 *
 * Tests build the table directly through the engine and assert on the
 * selection API rather than going through DOM-event-style toggle handlers
 * (those expect a browser event — see RowSelection.kt). Calls map as:
 *  - `table.toggleAllRowsSelected(null)`  toggles the global selection.
 *  - `row.toggleSelected(null, null)`     toggles a single row.
 *
 * Passing `null` for the value uses the engine's fallback: `null` resolves
 * to `!getIsAllRowsSelected()` / `!isSelected`, i.e. flips the current state
 * — the same effect a checkbox `onChange` would produce.
 *
 * Selection getters used in assertions:
 *  - `getIsAllRowsSelected()`        — every selectable row across the table.
 *  - `getIsSomeRowsSelected()`       — partial (indeterminate) selection.
 *  - `getIsAllPageRowsSelected()`    — every selectable row on the current page.
 *  - `getIsSomePageRowsSelected()`   — partial selection on the current page.
 *  - `row.getIsSelected()`           — selection state of a single row.
 *  - `row.getCanSelect()`            — whether a row is selectable.
 *
 * State propagation: `onRowSelectionChange` flows through
 * `makeStateUpdater("rowSelection", table)` -> `table.setState(updater)` ->
 * `options.onStateChange(updater)`. The harness applies the updater in
 * place; `tableRef` captures the table so the `onStateChange` closure can
 * read `t.options.state` (a `lateinit` is awkward inside the per-test
 * builder).
 *
 * Fixtures: a 2-row dataset (tanner age 29, joe age 45) plus a `select` +
 * `First Name` column layout, kept self-contained in this file.
 * ============================================================================
 */

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
class RowSelectionReactTest {

    /** Two-row dataset used by every test in this file. */
    private fun selectionData(): List<Person> = listOf(
        mapOf(
            "firstName" to "tanner",
            "lastName" to "linsley",
            "age" to 29,
            "visits" to 100,
            "status" to "In Relationship",
            "progress" to 50,
        ),
        mapOf(
            "firstName" to "joe",
            "lastName" to "bergevin",
            "age" to 45,
            "visits" to 20,
            "status" to "Complicated",
            "progress" to 10,
        ),
    )

    /**
     * Two columns: a display column `select` (id only — assertions read the
     * selection getters directly, so no functional header/cell is needed)
     * and an accessor column for `firstName`.
     */
    private fun selectionColumns(): List<ColumnDef<Person, Any?>> = listOf(
        ColumnDef<Person, Any?>().apply {
            id = "select"
        },
        ColumnDef<Person, Any?>().apply {
            header = "First Name"
            accessorKey = "firstName"
        },
    )

    /**
     * Builds a table whose `onStateChange` applies the updater in place.
     * `enableRowSelection` is passed through unchanged (the engine accepts
     * either a `Boolean` or a `(Row<Any?>) -> Boolean`).
     */
    private fun makeTable(
        data: List<Person> = selectionData(),
        enableRowSelection: Any? = null,
    ): Table<Person> {
        // A 1-element array so the `onStateChange` closure can capture a
        // mutable reference to the table before `createTable` returns it.
        val tableRef = arrayOfNulls<Table<Person>>(1)
        val table = createTable(
            TableOptionsResolved(
                onStateChange = { updater ->
                    val t = tableRef[0]!!
                    t.options.state = functionalUpdate(updater, t.options.state)
                },
                renderFallbackValue = "",
                data = data,
                columns = selectionColumns(),
                state = TableState(),
                enableRowSelection = enableRowSelection,
                getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
            ),
        )
        tableRef[0] = table
        return table
    }

    /**
     * Select-all skips rows the `enableRowSelection` predicate excludes:
     * with `age > 40`, only joe is selectable. After toggling all, joe is
     * selected and tanner is not. The select-all then reports a partial
     * selection.
     *
     * Note: `getIsAllRowsSelected()` is intentionally not asserted here. With
     * every SELECTABLE row selected (joe is the only selectable row, and joe
     * is selected), `getIsAllRowsSelected()` returns `true` — by design, per
     * the "all selectable rows" rule.
     */
    @Test
    fun selectAllDoesNotSelectRowsNotAvailableForSelection() {
        val table = makeTable(
            enableRowSelection = { row: Row<Any?> ->
                ((row.original as Person)["age"] as Int) > 40
            },
        )

        val rows = table.getRowModel().rows
        val notSelected = rows[0] // tanner — age 29, not selectable
        val selected = rows[1] // joe — age 45, selectable

        table.toggleAllRowsSelected(null)

        assertTrue(table.getIsSomeRowsSelected(), "select-all should be partially checked")
        assertFalse(notSelected.getIsSelected(), "tanner (unselectable) should not be checked")
        assertTrue(selected.getIsSelected(), "joe (selectable) should be checked")

        // Toggling again clears the selection.
        table.toggleAllRowsSelected(null)

        assertFalse(table.getIsSomeRowsSelected(), "select-all should not be partially checked")
        assertFalse(notSelected.getIsSelected(), "tanner should not be checked")
        assertFalse(selected.getIsSelected(), "joe should not be checked")
    }

    /**
     * Page-level select-all stays unchecked when no row on the page is
     * selectable, and reflects partial / full selection correctly once a
     * selectable row exists. Regression coverage for the case where every
     * row on the current page is filtered out by `enableRowSelection`.
     *
     * With no pagination model configured, `getPaginationRowModel()` falls
     * back to the core rows, so the whole dataset is the "current page".
     */
    @Test
    fun selectAllIsUncheckedForCurrentPageWhenNoRowsSelectable() {
        // --- condition 1: `age > 50` — nobody selectable --------------------
        val tableNoneSelectable = makeTable(
            enableRowSelection = { row: Row<Any?> ->
                ((row.original as Person)["age"] as Int) > 50
            },
        )

        assertTrue(
            tableNoneSelectable.getRowModel().rows.none { it.getCanSelect() },
            "no row should be selectable",
        )
        assertFalse(
            tableNoneSelectable.getIsAllPageRowsSelected(),
            "select-all-page should not be checked when no rows selectable",
        )
        assertFalse(
            tableNoneSelectable.getIsSomePageRowsSelected(),
            "select-all-page should not be partially checked when no rows selectable",
        )

        // --- condition 2: `age > 40` — joe(45) selectable -------------------
        val tableOneSelectable = makeTable(
            enableRowSelection = { row: Row<Any?> ->
                ((row.original as Person)["age"] as Int) > 40
            },
        )

        assertTrue(
            tableOneSelectable.getRowModel().rows.any { it.getCanSelect() },
            "at least one row should be selectable",
        )
        // Nothing is selected yet.
        assertFalse(
            tableOneSelectable.getIsAllPageRowsSelected(),
            "select-all-page should not be checked before toggling",
        )
        assertFalse(
            tableOneSelectable.getIsSomePageRowsSelected(),
            "select-all-page should not be partially checked before toggling",
        )

        // Select the one selectable row.
        val selectableRow = tableOneSelectable.getRowModel().rows.first { it.getCanSelect() }
        selectableRow.toggleSelected(null, null)

        // With the only selectable row selected, the page is fully selected.
        assertTrue(
            tableOneSelectable.getIsAllPageRowsSelected(),
            "select-all-page should be checked after selecting the only selectable row",
        )
    }

    /**
     * With the default `enableRowSelection`, every row is selectable: a
     * single select-all toggle selects every row, and a second toggle clears
     * the selection.
     */
    @Test
    fun selectAllWhenAllRowsAreAvailableForSelection() {
        val table = makeTable()

        val rows = table.getRowModel().rows
        val rowOne = rows[0]
        val rowTwo = rows[1]

        table.toggleAllRowsSelected(null)

        assertTrue(table.getIsAllRowsSelected(), "select-all should be fully checked")
        assertTrue(rowOne.getIsSelected(), "rowOne should be checked")
        assertTrue(rowTwo.getIsSelected(), "rowTwo should be checked")

        // Toggling again clears the selection.
        table.toggleAllRowsSelected(null)

        assertFalse(table.getIsAllRowsSelected(), "select-all should not be checked")
        assertFalse(rowOne.getIsSelected(), "rowOne should not be checked")
        assertFalse(rowTwo.getIsSelected(), "rowTwo should not be checked")
    }

    /**
     * Toggling a single row produces a partial (indeterminate) select-all
     * state; toggling the same row again clears the selection.
     */
    @Test
    fun selectASingleRow() {
        val table = makeTable()

        val rows = table.getRowModel().rows
        val rowOne = rows[0]
        val rowTwo = rows[1]

        rowOne.toggleSelected(null, null)

        // One of two rows selected -> indeterminate select-all.
        assertTrue(table.getIsSomeRowsSelected(), "select-all should be partially checked")
        assertTrue(rowOne.getIsSelected(), "rowOne should be checked")
        assertFalse(rowTwo.getIsSelected(), "rowTwo should not be checked")

        // Toggling the same row again clears the selection.
        rowOne.toggleSelected(null, null)

        assertFalse(table.getIsAllRowsSelected(), "select-all should not be checked")
        assertFalse(rowOne.getIsSelected(), "rowOne should not be checked")
        assertFalse(rowTwo.getIsSelected(), "rowTwo should not be checked")
    }
}
