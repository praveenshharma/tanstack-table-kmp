package io.github.tanstacktable.core

/*
 * ============================================================================
 * Performance tests for `getGroupedRowModel`.
 *
 * The row count (50000) and the wall-clock budget (5000 ms) are intentional:
 * lowering either would weaken the regression guard around grouping
 * performance.
 * ============================================================================
 */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Builds one accessor column per key of the first row in [people], using the
 * key as both the accessor and the column id.
 */
private fun generateColumns(people: List<Person>): List<ColumnDef<Person, Any?>> {
    val columnHelper = createColumnHelper<Person>()
    val person = people[0]
    return person.keys.map { key ->
        columnHelper.accessor(key, ColumnDef<Person, Any?>().also { it.id = key })
    }
}

class GetGroupedRowModelTest {

    @Test
    fun groups50kRowsAnd3GroupedColumnsWithClusteredDataInLessThan5Seconds() {
        val data: List<Person> = makeData(50000)
        val columns = generateColumns(data)
        val grouping: List<String> = listOf("firstName", "lastName", "age")

        // `TimeSource.Monotonic` is the KMP equivalent of a wall-clock delta
        // suitable for a "less than N ms" budget.
        val start = TimeSource.Monotonic.markNow()

        // Force every row's grouping fields to the same value so all 50000
        // rows collapse into a single grouped bucket at every level.
        data.forEach { p -> (p as MutableMap<String, Any?>)["firstName"] = "Fixed" }
        data.forEach { p -> (p as MutableMap<String, Any?>)["lastName"] = "Name" }
        data.forEach { p -> (p as MutableMap<String, Any?>)["age"] = 123 }

        val table = createTable<Person>(
            TableOptionsResolved<Person>(
                onStateChange = {},
                renderFallbackValue = "",
                data = data,
                state = TableState(grouping = grouping),
                columns = columns,
                getCoreRowModel = getCoreRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
                getGroupedRowModel = getGroupedRowModel<Person>() as (Table<Any?>) -> () -> RowModel<Any?>,
            ),
        )
        val groupedById = table.getGroupedRowModel().rowsById
        val end = start.elapsedNow()

        // `leafRows` is the runtime property attached by `getGroupedRowModel`
        // (see Row.kt).
        assertEquals(50000, assertNotNull(groupedById["firstName:Fixed"]).leafRows?.size)
        assertEquals(
            50000,
            assertNotNull(groupedById["firstName:Fixed>lastName:Name"]).leafRows?.size,
        )
        assertEquals(
            50000,
            assertNotNull(groupedById["firstName:Fixed>lastName:Name>age:123"]).leafRows?.size,
        )
        assertTrue(end.inWholeMilliseconds < 5000, "grouping took ${end.inWholeMilliseconds}ms")
    }
}
