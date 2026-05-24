package io.github.tanstacktable.core

/*
 * ============================================================================
 * Row pinning behavior — exercises `getTopRows()`, `getBottomRows()`, and
 * `getCenterRows()` across pagination and `keepPinnedRows` settings.
 *
 * `pinGenerateColumns` is a file-local copy of the column-helper boilerplate
 * shared with other test files (each test file keeps its own copy to stay
 * self-contained without exposing a public helper).
 * ============================================================================
 */

import kotlin.test.Test
import kotlin.test.assertEquals

private fun pinGenerateColumns(people: List<Person>): List<ColumnDef<Person, Any?>> {
    val columnHelper = createColumnHelper<Person>()
    val person = people[0]
    return person.keys.map { key ->
        columnHelper.accessor(key, ColumnDef<Person, Any?>().also { it.id = key })
    }
}

@Suppress("UNCHECKED_CAST")
class RowPinningTest {

    class GetTopRows {

        @Test
        fun shouldReturnPinnedRowsWhenKeepPinnedRowsIsTrueRowsAreVisible() {
            val data = makeData(10)
            val columns = pinGenerateColumns(data)

            val table = createTable(
                TableOptionsResolved(
                    enableRowPinning = true,
                    keepPinnedRows = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    state = TableState(
                        pagination = PaginationState(pageSize = 5, pageIndex = 0),
                        rowPinning = RowPinningState(top = listOf("0", "1")),
                    ),
                    columns = columns,
                    getPaginationRowModel = getPaginationRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val result = table.getTopRows()

            assertEquals(2, result.size)
            assertEquals("0", result[0].id)
            assertEquals("1", result[1].id)
        }

        @Test
        fun shouldReturnPinnedRowsWhenKeepPinnedRowsIsTrueRowsAreNotVisible() {
            val data = makeData(10)
            val columns = pinGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowPinning = true,
                    keepPinnedRows = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    state = TableState(
                        pagination = PaginationState(pageSize = 5, pageIndex = 1),
                        rowPinning = RowPinningState(top = listOf("0", "1")),
                    ),
                    columns = columns,
                    getPaginationRowModel = getPaginationRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val result = table.getTopRows()

            assertEquals(2, result.size)
            assertEquals("0", result[0].id)
            assertEquals("1", result[1].id)
        }

        @Test
        fun shouldReturnPinnedRowsWhenKeepPinnedRowsIsFalseRowsAreVisible() {
            val data = makeData(10)
            val columns = pinGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowPinning = true,
                    keepPinnedRows = false,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    state = TableState(
                        pagination = PaginationState(pageSize = 5, pageIndex = 0),
                        rowPinning = RowPinningState(top = listOf("0", "1")),
                    ),
                    columns = columns,
                    getPaginationRowModel = getPaginationRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val result = table.getTopRows()

            assertEquals(2, result.size)
            assertEquals("0", result[0].id)
            assertEquals("1", result[1].id)
        }

        @Test
        fun shouldNotReturnPinnedRowsWhenKeepPinnedRowsIsFalseAndRowsAreNotVisible() {
            val data = makeData(10)
            val columns = pinGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowPinning = true,
                    keepPinnedRows = false,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    state = TableState(
                        pagination = PaginationState(pageSize = 5, pageIndex = 1),
                        rowPinning = RowPinningState(top = listOf("0", "1")),
                    ),
                    columns = columns,
                    getPaginationRowModel = getPaginationRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val result = table.getTopRows()

            assertEquals(0, result.size)
        }

        @Test
        fun shouldReturnCorrectTopRows() {
            val data = makeData(10)
            val columns = pinGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowPinning = true,
                    keepPinnedRows = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    state = TableState(
                        pagination = PaginationState(pageSize = 5, pageIndex = 0),
                        rowPinning = RowPinningState(top = listOf("1", "3")),
                    ),
                    columns = columns,
                    getPaginationRowModel = getPaginationRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val result = table.getTopRows()

            assertEquals(2, result.size)
            assertEquals("1", result[0].id)
            assertEquals("3", result[1].id)
        }
    }

    class GetBottomRows {

        @Test
        fun shouldReturnCorrectBottomRows() {
            val data = makeData(10)
            val columns = pinGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowPinning = true,
                    keepPinnedRows = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    state = TableState(
                        pagination = PaginationState(pageSize = 5, pageIndex = 0),
                        rowPinning = RowPinningState(bottom = listOf("1", "3")),
                    ),
                    columns = columns,
                    getPaginationRowModel = getPaginationRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val result = table.getBottomRows()

            assertEquals(2, result.size)
            assertEquals("1", result[0].id)
            assertEquals("3", result[1].id)
        }
    }

    class GetCenterRows {

        @Test
        fun shouldReturnAllRowsExceptAnyPinnedRows() {
            val data = makeData(6)
            val columns = pinGenerateColumns(data)

            val table = createTable<Person>(
                TableOptionsResolved<Person>(
                    enableRowPinning = true,
                    keepPinnedRows = true,
                    onStateChange = {},
                    renderFallbackValue = "",
                    data = data,
                    state = TableState(
                        pagination = PaginationState(pageSize = 10, pageIndex = 0),
                        rowPinning = RowPinningState(top = listOf("1", "3"), bottom = listOf("2", "4")),
                    ),
                    columns = columns,
                    getPaginationRowModel = getPaginationRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                    getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                ),
            )

            val result = table.getCenterRows()

            assertEquals(2, result.size)
            // 0 and 5 are the only rows not pinned
            assertEquals("0", result[0].id)
            assertEquals("5", result[1].id)
        }
    }
}
