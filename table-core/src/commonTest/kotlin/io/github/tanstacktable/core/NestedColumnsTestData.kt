package io.github.tanstacktable.core

/*
 * ============================================================================
 * Shared test fixtures for the nested "Name" / "Info" column layout used by
 * CoreEngineTest and ColumnVisibilityTest.
 *
 * Tests drive the engine directly (`createTable(TableOptionsResolved(...))`)
 * and assert against the resulting header / row / footer structure.
 *
 * Row representation — `Person` is `Map<String, Any?>` (see MakeTestData.kt):
 * the engine resolves a string `accessorKey` via `originalRow as? Map<String,
 * Any?>`.
 * ============================================================================
 */

/**
 * Three flat `Person` rows (tanner / derek / joe). `age`/`visits`/`progress`
 * are `Int`; `firstName`/`lastName`/`status` are `String`. No `subRows`.
 */
val nestedDefaultData: List<Person> = listOf(
    mapOf(
        "firstName" to "tanner",
        "lastName" to "linsley",
        "age" to 29,
        "visits" to 100,
        "status" to "In Relationship",
        "progress" to 50,
    ),
    mapOf(
        "firstName" to "derek",
        "lastName" to "perkins",
        "age" to 40,
        "visits" to 40,
        "status" to "Single",
        "progress" to 80,
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
 * Builds the nested column definitions used by CoreEngineTest and
 * ColumnVisibilityTest.
 *
 * Layout: two top-level group columns.
 *   - "Name"  { firstName (accessorKey, no header), lastName (accessorFn) }
 *   - "Info"  { age, "More Info" { visits, status, progress } }
 *
 * Header templates:
 *  - "Name" / "Info" / "More Info" / "Status" / "Profile Progress" are stored
 *    as plain `String` headers.
 *  - "Age" / "Last Name" / "Visits" are written as 1-arg lambdas that ignore
 *    the context and return the literal string.
 *  - `firstName` has no header; it resolves via the engine's default header
 *    template (CoreTable._getDefaultColumnDef), which returns the column id
 *    `"firstName"`. Tests resolve this through the engine rather than
 *    hardcoding the value.
 *
 * Footers: every column carries `footer = { ctx -> ctx.column.id }`. The
 * "More Info" group is the lone exception — it has no footer template (null).
 *
 * Cells: `firstName` / `lastName` carry `cell = info -> info.renderValue()`.
 * Every other column has no `cell`, resolving via the engine default
 * (`props.renderValue()?.toString()`). Either way, the asserted cell value is
 * the row value `.toString()`-ed.
 */
@Suppress("UNCHECKED_CAST")
fun nestedDefaultColumns(): List<ColumnDef<Person, Any?>> {
    // Footer template shared by every column: render the column id.
    val footerById: ColumnDefTemplate = { ctx: Any? ->
        (ctx as HeaderContext<Person, Any?>).column.id
    }

    return listOf(
        // "Name" group: firstName + lastName.
        ColumnDef<Person, Any?>().apply {
            header = "Name"
            footer = footerById
            columns = listOf(
                // firstName: no header, resolves to the column id
                // "firstName" via the engine default header template.
                ColumnDef<Person, Any?>().apply {
                    accessorKey = "firstName"
                    cell = { info: CellContext<Person, Any?> -> info.renderValue() }
                    footer = footerById
                },
                // lastName: accessor function + explicit id, plain string
                // header "Last Name".
                ColumnDef<Person, Any?>().apply {
                    accessorFn = { row: Person, _: Int -> row["lastName"] }
                    id = "lastName"
                    cell = { info: CellContext<Person, Any?> -> info.renderValue() }
                    header = { _: HeaderContext<Person, Any?> -> "Last Name" }
                    footer = footerById
                },
            )
        },
        // "Info" group: age + nested "More Info" group.
        ColumnDef<Person, Any?>().apply {
            header = "Info"
            footer = footerById
            columns = listOf(
                ColumnDef<Person, Any?>().apply {
                    accessorKey = "age"
                    header = { _: HeaderContext<Person, Any?> -> "Age" }
                    footer = footerById
                },
                // "More Info" group: no footer template (null).
                ColumnDef<Person, Any?>().apply {
                    header = "More Info"
                    columns = listOf(
                        ColumnDef<Person, Any?>().apply {
                            accessorKey = "visits"
                            header = { _: HeaderContext<Person, Any?> -> "Visits" }
                            footer = footerById
                        },
                        ColumnDef<Person, Any?>().apply {
                            accessorKey = "status"
                            header = "Status"
                            footer = footerById
                        },
                        ColumnDef<Person, Any?>().apply {
                            accessorKey = "progress"
                            header = "Profile Progress"
                            footer = footerById
                        },
                    )
                },
            )
        },
    )
}

/**
 * Lightweight stand-in for `flexRender(template, context)`. The real helper
 * lives in `:table-compose`, which is not available from `:table-core`'s
 * `commonTest`. This reproduces the slice the header/cell assertions need:
 *  - a `null` template -> `""`.
 *  - a `String` template -> the string itself.
 *  - a function template -> invoked with the supplied `context`, result
 *    `.toString()`-ed; a `null` result -> `""`.
 *
 * Placeholder headers are checked directly via `header.isPlaceholder`, so
 * this helper is only ever called for non-placeholder headers/cells.
 */
fun resolveTemplate(template: Any?, context: Any?): String {
    return when (template) {
        null -> ""
        is String -> template
        is Function<*> -> {
            @Suppress("UNCHECKED_CAST")
            val result = (template as (Any?) -> Any?)(context)
            result?.toString() ?: ""
        }
        else -> template.toString()
    }
}
