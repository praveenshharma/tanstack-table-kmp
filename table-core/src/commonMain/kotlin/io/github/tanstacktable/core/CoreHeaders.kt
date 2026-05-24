package io.github.tanstacktable.core

import kotlin.math.max
import kotlin.math.min

private const val debug = "debugHeaders"

@Suppress("UNCHECKED_CAST")
private fun <TData, TValue> createHeader(
    table: Table<TData>,
    column: Column<TData, TValue>,
    id: String?,
    isPlaceholder: Boolean?,
    placeholderId: String?,
    index: Int,
    depth: Int,
): Header<TData, TValue> {
    val headerId = id ?: column.id

    val header = Header<TData, TValue>()

    header.id = headerId
    header.column = column
    header.index = index
    header.isPlaceholder = isPlaceholder == true
    header.placeholderId = placeholderId
    header.depth = depth
    header.subHeaders = mutableListOf()
    header.colSpan = 0
    header.rowSpan = 0

    header.getLeafHeaders = {
        val leafHeaders = mutableListOf<Header<TData, *>>()

        fun recurseHeader(h: Header<TData, *>) {
            if (h.subHeaders.isNotEmpty()) {
                h.subHeaders.forEach { sub -> recurseHeader(sub) }
            }
            leafHeaders.add(h)
        }

        recurseHeader(header)

        leafHeaders
    }

    header.getContext = {
        HeaderContext(
            table = table,
            header = header,
            column = column,
        )
    }

    table._features.forEach { feature ->
        feature.createHeader?.invoke(header as Header<Any?, Any?>, table as Table<Any?>)
    }

    return header
}

@Suppress("UNCHECKED_CAST")
object Headers : TableFeature {
    override val createTable: ((table: Table<Any?>) -> Unit)?
        get() = { table ->
        // Header Groups

        table.getHeaderGroups = memo(
            getDeps = {
                listOf(
                    table.getAllColumns(),
                    table.getVisibleLeafColumns(),
                    table.getState().columnPinning.left,
                    table.getState().columnPinning.right,
                )
            },
            fn = { deps ->
                val allColumns = deps[0] as List<Column<Any?, *>>
                val leafColumns = deps[1] as List<Column<Any?, *>>
                val left = deps[2] as List<String>?
                val right = deps[3] as List<String>?

                val leftColumns =
                    left?.map { columnId -> leafColumns.find { d -> d.id == columnId } }
                        ?.filter { isTruthy(it) }
                        ?: emptyList()

                val rightColumns =
                    right?.map { columnId -> leafColumns.find { d -> d.id == columnId } }
                        ?.filter { isTruthy(it) }
                        ?: emptyList()

                val centerColumns = leafColumns.filter { column ->
                    left?.contains(column.id) != true && right?.contains(column.id) != true
                }

                val headerGroups = buildHeaderGroups(
                    allColumns,
                    (leftColumns + centerColumns + rightColumns) as List<Column<Any?, *>>,
                    table,
                    null,
                )

                headerGroups
            },
            opts = getMemoOptions(table.options, debug, "getHeaderGroups"),
        )

        table.getCenterHeaderGroups = memo(
            getDeps = {
                listOf(
                    table.getAllColumns(),
                    table.getVisibleLeafColumns(),
                    table.getState().columnPinning.left,
                    table.getState().columnPinning.right,
                )
            },
            fn = { deps ->
                val allColumns = deps[0] as List<Column<Any?, *>>
                var leafColumns = deps[1] as List<Column<Any?, *>>
                val left = deps[2] as List<String>?
                val right = deps[3] as List<String>?

                leafColumns = leafColumns.filter { column ->
                    left?.contains(column.id) != true && right?.contains(column.id) != true
                }
                buildHeaderGroups(allColumns, leafColumns, table, "center")
            },
            opts = getMemoOptions(table.options, debug, "getCenterHeaderGroups"),
        )

        table.getLeftHeaderGroups = memo(
            getDeps = {
                listOf(
                    table.getAllColumns(),
                    table.getVisibleLeafColumns(),
                    table.getState().columnPinning.left,
                )
            },
            fn = { deps ->
                val allColumns = deps[0] as List<Column<Any?, *>>
                val leafColumns = deps[1] as List<Column<Any?, *>>
                val left = deps[2] as List<String>?

                val orderedLeafColumns =
                    left?.map { columnId -> leafColumns.find { d -> d.id == columnId } }
                        ?.filter { isTruthy(it) }
                        ?: emptyList()

                buildHeaderGroups(
                    allColumns,
                    orderedLeafColumns as List<Column<Any?, *>>,
                    table,
                    "left",
                )
            },
            opts = getMemoOptions(table.options, debug, "getLeftHeaderGroups"),
        )

        table.getRightHeaderGroups = memo(
            getDeps = {
                listOf(
                    table.getAllColumns(),
                    table.getVisibleLeafColumns(),
                    table.getState().columnPinning.right,
                )
            },
            fn = { deps ->
                val allColumns = deps[0] as List<Column<Any?, *>>
                val leafColumns = deps[1] as List<Column<Any?, *>>
                val right = deps[2] as List<String>?

                val orderedLeafColumns =
                    right?.map { columnId -> leafColumns.find { d -> d.id == columnId } }
                        ?.filter { isTruthy(it) }
                        ?: emptyList()

                buildHeaderGroups(
                    allColumns,
                    orderedLeafColumns as List<Column<Any?, *>>,
                    table,
                    "right",
                )
            },
            opts = getMemoOptions(table.options, debug, "getRightHeaderGroups"),
        )

        // Footer Groups

        table.getFooterGroups = memo(
            getDeps = { listOf(table.getHeaderGroups()) },
            fn = { deps ->
                val headerGroups = deps[0] as List<HeaderGroup<Any?>>
                headerGroups.reversed()
            },
            opts = getMemoOptions(table.options, debug, "getFooterGroups"),
        )

        table.getLeftFooterGroups = memo(
            getDeps = { listOf(table.getLeftHeaderGroups()) },
            fn = { deps ->
                val headerGroups = deps[0] as List<HeaderGroup<Any?>>
                headerGroups.reversed()
            },
            opts = getMemoOptions(table.options, debug, "getLeftFooterGroups"),
        )

        table.getCenterFooterGroups = memo(
            getDeps = { listOf(table.getCenterHeaderGroups()) },
            fn = { deps ->
                val headerGroups = deps[0] as List<HeaderGroup<Any?>>
                headerGroups.reversed()
            },
            opts = getMemoOptions(table.options, debug, "getCenterFooterGroups"),
        )

        table.getRightFooterGroups = memo(
            getDeps = { listOf(table.getRightHeaderGroups()) },
            fn = { deps ->
                val headerGroups = deps[0] as List<HeaderGroup<Any?>>
                headerGroups.reversed()
            },
            opts = getMemoOptions(table.options, debug, "getRightFooterGroups"),
        )

        // Flat Headers

        table.getFlatHeaders = memo(
            getDeps = { listOf(table.getHeaderGroups()) },
            fn = { deps ->
                val headerGroups = deps[0] as List<HeaderGroup<Any?>>
                headerGroups.flatMap { headerGroup -> headerGroup.headers }
            },
            opts = getMemoOptions(table.options, debug, "getFlatHeaders"),
        )

        table.getLeftFlatHeaders = memo(
            getDeps = { listOf(table.getLeftHeaderGroups()) },
            fn = { deps ->
                val left = deps[0] as List<HeaderGroup<Any?>>
                left.flatMap { headerGroup -> headerGroup.headers }
            },
            opts = getMemoOptions(table.options, debug, "getLeftFlatHeaders"),
        )

        table.getCenterFlatHeaders = memo(
            getDeps = { listOf(table.getCenterHeaderGroups()) },
            fn = { deps ->
                val left = deps[0] as List<HeaderGroup<Any?>>
                left.flatMap { headerGroup -> headerGroup.headers }
            },
            opts = getMemoOptions(table.options, debug, "getCenterFlatHeaders"),
        )

        table.getRightFlatHeaders = memo(
            getDeps = { listOf(table.getRightHeaderGroups()) },
            fn = { deps ->
                val left = deps[0] as List<HeaderGroup<Any?>>
                left.flatMap { headerGroup -> headerGroup.headers }
            },
            opts = getMemoOptions(table.options, debug, "getRightFlatHeaders"),
        )

        // Leaf Headers

        table.getCenterLeafHeaders = memo(
            getDeps = { listOf(table.getCenterFlatHeaders()) },
            fn = { deps ->
                val flatHeaders = deps[0] as List<Header<Any?, *>>
                flatHeaders.filter { header -> header.subHeaders.isEmpty() }
            },
            opts = getMemoOptions(table.options, debug, "getCenterLeafHeaders"),
        )

        table.getLeftLeafHeaders = memo(
            getDeps = { listOf(table.getLeftFlatHeaders()) },
            fn = { deps ->
                val flatHeaders = deps[0] as List<Header<Any?, *>>
                flatHeaders.filter { header -> header.subHeaders.isEmpty() }
            },
            opts = getMemoOptions(table.options, debug, "getLeftLeafHeaders"),
        )

        table.getRightLeafHeaders = memo(
            getDeps = { listOf(table.getRightFlatHeaders()) },
            fn = { deps ->
                val flatHeaders = deps[0] as List<Header<Any?, *>>
                flatHeaders.filter { header -> header.subHeaders.isEmpty() }
            },
            opts = getMemoOptions(table.options, debug, "getRightLeafHeaders"),
        )

        table.getLeafHeaders = memo(
            getDeps = {
                listOf(
                    table.getLeftHeaderGroups(),
                    table.getCenterHeaderGroups(),
                    table.getRightHeaderGroups(),
                )
            },
            fn = { deps ->
                val left = deps[0] as List<HeaderGroup<Any?>>
                val center = deps[1] as List<HeaderGroup<Any?>>
                val right = deps[2] as List<HeaderGroup<Any?>>

                (
                    (left.getOrNull(0)?.headers ?: emptyList()) +
                        (center.getOrNull(0)?.headers ?: emptyList()) +
                        (right.getOrNull(0)?.headers ?: emptyList())
                )
                    .flatMap { header -> header.getLeafHeaders() }
            },
            opts = getMemoOptions(table.options, debug, "getLeafHeaders"),
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun <TData> buildHeaderGroups(
    allColumns: List<Column<TData, *>>,
    columnsToGroup: List<Column<TData, *>>,
    table: Table<TData>,
    headerFamily: String?,
): List<HeaderGroup<TData>> {
    // Find the max depth of the columns:
    // build the leaf column row
    // build each buffer row going up
    //    placeholder for non-existent level
    //    real column for existing level

    var maxDepth = 0

    fun findMaxDepth(columns: List<Column<TData, *>>, depth: Int = 1) {
        maxDepth = max(maxDepth, depth)

        columns
            .filter { column -> column.getIsVisible() }
            .forEach { column ->
                if (column.columns.isNotEmpty()) {
                    findMaxDepth(column.columns, depth + 1)
                }
            }
    }

    findMaxDepth(allColumns)

    val headerGroups = mutableListOf<HeaderGroup<TData>>()

    fun createHeaderGroup(headersToGroup: List<Header<TData, *>>, depth: Int) {
        // The header group we are creating
        val headerGroup = HeaderGroup<TData>(
            depth = depth,
            id = listOf(headerFamily, "$depth").filter { isTruthy(it) }.joinToString("_"),
            headers = mutableListOf(),
        )

        // The parent columns we're going to scan next
        val pendingParentHeaders = mutableListOf<Header<TData, *>>()

        // Scan each column for parents
        headersToGroup.forEach { headerToGroup ->
            // What is the latest (last) parent column?

            val latestPendingParentHeader = pendingParentHeaders.lastOrNull()

            val isLeafHeader = headerToGroup.column.depth == headerGroup.depth

            val column: Column<TData, *>
            var isPlaceholder = false

            if (isLeafHeader && headerToGroup.column.parent != null) {
                // The parent header is new
                column = headerToGroup.column.parent!!
            } else {
                // The parent header is repeated
                column = headerToGroup.column
                isPlaceholder = true
            }

            if (latestPendingParentHeader != null &&
                latestPendingParentHeader.column === column
            ) {
                // This column is repeated. Add it as a sub header to the next batch
                (latestPendingParentHeader.subHeaders as MutableList<Header<TData, *>>)
                    .add(headerToGroup)
            } else {
                // This is a new header. Let's create it
                val header = createHeader(
                    table,
                    column,
                    id = listOf<Any?>(headerFamily, depth, column.id, headerToGroup.id)
                        .filter { isTruthy(it) }
                        .joinToString("_"),
                    isPlaceholder = isPlaceholder,
                    placeholderId = if (isPlaceholder) {
                        "${pendingParentHeaders.filter { d -> d.column === column }.size}"
                    } else {
                        null
                    },
                    depth = depth,
                    index = pendingParentHeaders.size,
                )

                // Add the headerToGroup as a subHeader of the new header
                (header.subHeaders as MutableList<Header<TData, *>>).add(headerToGroup)
                // Add the new header to the pendingParentHeaders to get grouped
                // in the next batch
                pendingParentHeaders.add(header)
            }

            headerGroup.headers.add(headerToGroup)
            headerToGroup.headerGroup = headerGroup
        }

        headerGroups.add(headerGroup)

        if (depth > 0) {
            createHeaderGroup(pendingParentHeaders, depth - 1)
        }
    }

    val bottomHeaders = columnsToGroup.mapIndexed { index, column ->
        createHeader(
            table,
            column,
            id = null,
            isPlaceholder = null,
            placeholderId = null,
            index = index,
            depth = maxDepth,
        )
    }

    createHeaderGroup(bottomHeaders, maxDepth - 1)

    headerGroups.reverse()

    // headerGroups = headerGroups.filter(headerGroup => {
    //   return !headerGroup.headers.every(header => header.isPlaceholder)
    // })

    fun recurseHeadersForSpans(headers: List<Header<TData, *>>): List<Pair<Int, Int>> {
        val filteredHeaders = headers.filter { header ->
            header.column.getIsVisible()
        }

        return filteredHeaders.map { header ->
            var colSpan = 0
            var rowSpan = 0
            var childRowSpans = mutableListOf(0)

            if (header.subHeaders.isNotEmpty()) {
                childRowSpans = mutableListOf()

                recurseHeadersForSpans(header.subHeaders).forEach { (childColSpan, childRowSpan) ->
                    colSpan += childColSpan
                    childRowSpans.add(childRowSpan)
                }
            } else {
                colSpan = 1
            }

            val minChildRowSpan = childRowSpans.min()
            rowSpan += minChildRowSpan

            header.colSpan = colSpan
            header.rowSpan = rowSpan

            colSpan to rowSpan
        }
    }

    recurseHeadersForSpans(headerGroups.getOrNull(0)?.headers ?: emptyList())

    return headerGroups
}
