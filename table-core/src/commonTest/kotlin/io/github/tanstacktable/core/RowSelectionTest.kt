package io.github.tanstacktable.core

/*
 * ============================================================================
 * Row-selection behavior — exercises the public `selectRowsFn`,
 * `isRowSelected`, and `isSubRowSelected` functions in RowSelection.kt
 * across flat and nested row models, with and without an
 * `enableRowSelection` predicate.
 * ============================================================================
 */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun selGenerateColumns(people: List<Person>): List<ColumnDef<Person, Any?>> {
    val columnHelper = createColumnHelper<Person>()
    val person = people[0]
    return person.keys.map { key ->
        columnHelper.accessor(key, ColumnDef<Person, Any?>().also { it.id = key })
    }
}

/**
 * `getSubRows` for a `Person` map: reads the `"subRows"` key.
 */
@Suppress("UNCHECKED_CAST")
private val personGetSubRows: (Person, Int) -> List<Person>? = { row, _ ->
    row["subRows"] as List<Person>?
}

@Suppress("UNCHECKED_CAST")
class RowSelectionTest {

    class SelectRowsFn {

        @Test
        fun shouldOnlyReturnRowsThatAreSelected() {
            val data = makeData(5)
            val columns = selGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowSelection = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    getSubRows = personGetSubRows,
                    state = TableState(rowSelection = mapOf("0" to true, "2" to true)),
                    columns = columns,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )
            val rowModel = table.getCoreRowModel()

            val result = selectRowsFn(table, rowModel)

            assertEquals(2, result.rows.size)
            assertEquals(2, result.flatRows.size)
            assertTrue(result.rowsById.containsKey("0"))
            assertTrue(result.rowsById.containsKey("2"))
        }

        @Test
        fun shouldRecurseIntoSubRowsAndOnlyReturnSelectedSubRows() {
            // assuming 3 parent rows with 2 sub-rows each
            val data = makeData(3, 2)
            val columns = selGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowSelection = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    getSubRows = personGetSubRows,
                    state = TableState(rowSelection = mapOf("0" to true, "0.0" to true)),
                    columns = columns,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )
            val rowModel = table.getCoreRowModel()

            val result = selectRowsFn(table, rowModel)

            assertEquals(1, result.rows[0].subRows.size)
            assertEquals(2, result.flatRows.size)
            assertTrue(result.rowsById.containsKey("0"))
            assertTrue(result.rowsById.containsKey("0.0"))
        }

        @Test
        fun shouldReturnAnEmptyListIfNoRowsAreSelected() {
            val data = makeData(5)
            val columns = selGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowSelection = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    getSubRows = personGetSubRows,
                    state = TableState(rowSelection = emptyMap()),
                    columns = columns,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )
            val rowModel = table.getCoreRowModel()

            val result = selectRowsFn(table, rowModel)

            assertEquals(0, result.rows.size)
            assertEquals(0, result.flatRows.size)
            assertTrue(result.rowsById.isEmpty())
        }
    }

    class IsRowSelected {

        @Test
        fun shouldReturnTrueIfTheRowIdExistsInSelectionAndIsSetToTrue() {
            // `isRowSelected` reads only `row.id`, so a bare `Row` with an
            // id is sufficient.
            val row = Row<Any?>().also { it.id = "123" }
            val selection: Map<String, Boolean> = mapOf("123" to true, "456" to false)

            val result = isRowSelected(row, selection)
            assertEquals(true, result)
        }

        @Test
        fun shouldReturnFalseIfTheRowIdExistsInSelectionAndIsSetToFalse() {
            val row = Row<Any?>().also { it.id = "456" }
            val selection: Map<String, Boolean> = mapOf("123" to true, "456" to false)

            val result = isRowSelected(row, selection)
            assertEquals(false, result)
        }

        @Test
        fun shouldReturnFalseIfTheRowIdDoesNotExistInSelection() {
            val row = Row<Any?>().also { it.id = "789" }
            val selection: Map<String, Boolean> = mapOf("123" to true, "456" to false)

            val result = isRowSelected(row, selection)
            assertEquals(false, result)
        }

        @Test
        fun shouldReturnFalseIfSelectionIsAnEmptyObject() {
            val row = Row<Any?>().also { it.id = "789" }
            val selection: Map<String, Boolean> = emptyMap()

            val result = isRowSelected(row, selection)
            assertEquals(false, result)
        }
    }

    class IsSubRowSelected {

        @Test
        fun shouldReturnFalseIfThereAreNoSubRows() {
            val data = makeData(3)
            val columns = selGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowSelection = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    state = TableState(),
                    columns = columns,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val firstRow = table.getCoreRowModel().rows[0]

            val result = isSubRowSelected(firstRow, table.getState().rowSelection, table)

            assertEquals(false, result)
        }

        @Test
        fun shouldReturnFalseIfNoSubRowsAreSelected() {
            val data = makeData(3, 2)
            val columns = selGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowSelection = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    getSubRows = personGetSubRows,
                    state = TableState(rowSelection = emptyMap()),
                    columns = columns,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val firstRow = table.getCoreRowModel().rows[0]

            val result = isSubRowSelected(firstRow, table.getState().rowSelection, table)

            assertEquals(false, result)
        }

        @Test
        fun shouldReturnSomeIfSomeSubRowsAreSelected() {
            val data = makeData(3, 2)
            val columns = selGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowSelection = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    getSubRows = personGetSubRows,
                    state = TableState(rowSelection = mapOf("0.0" to true)),
                    columns = columns,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val firstRow = table.getCoreRowModel().rows[0]

            val result = isSubRowSelected(firstRow, table.getState().rowSelection, table)

            assertEquals("some", result)
        }

        @Test
        fun shouldReturnAllIfAllSubRowsAreSelected() {
            val data = makeData(3, 2)
            val columns = selGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowSelection = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    getSubRows = personGetSubRows,
                    state = TableState(rowSelection = mapOf("0.0" to true, "0.1" to true)),
                    columns = columns,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val firstRow = table.getCoreRowModel().rows[0]

            val result = isSubRowSelected(firstRow, table.getState().rowSelection, table)

            assertEquals("all", result)
        }

        @Test
        fun shouldReturnAllIfAllSelectableSubRowsAreSelected() {
            val data = makeData(3, 2)
            val columns = selGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    // only first row is selectable (of 2 sub-rows)
                    enableRowSelection = { row: Row<Any?> -> row.index == 0 },
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    getSubRows = personGetSubRows,
                    state = TableState(rowSelection = mapOf("0.0" to true)),
                    columns = columns,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val firstRow = table.getCoreRowModel().rows[0]

            val result = isSubRowSelected(firstRow, table.getState().rowSelection, table)

            assertEquals("all", result)
        }

        @Test
        fun shouldReturnSomeWhenSomeNestedSubRowsAreSelected() {
            val data = makeData(3, 2, 2)
            val columns = selGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowSelection = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    getSubRows = personGetSubRows,
                    state = TableState(rowSelection = mapOf("0.0.0" to true)),
                    columns = columns,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val firstRow = table.getCoreRowModel().rows[0]

            val result = isSubRowSelected(firstRow, table.getState().rowSelection, table)

            assertEquals("some", result)
        }
    }
}
