package io.github.tanstacktable.core

/*
 * Grouped row-model builder. Walks the pre-grouped model, grouping rows
 * by each column id in [GroupingState] depth-first, building synthetic
 * parent rows whose `getValue` returns the column's aggregation result.
 */

/**
 * Builds the grouped row-model factory. The returned function is invoked
 * once per [Table] and produces a memoised getter for the grouped
 * [RowModel].
 *
 * When the pre-grouped model is empty, or no grouping is active, every
 * row is reset to `depth = 0` / `parentId = null` and the pre-grouped
 * model is returned unchanged.
 */
@Suppress("UNCHECKED_CAST")
fun <TData> getGroupedRowModel(): (table: Table<TData>) -> () -> RowModel<TData> {
    return factory@{ table ->
        val memoized: () -> RowModel<TData> = memo<RowModel<TData>>(
            getDeps = { listOf(table.getState().grouping, table.getPreGroupedRowModel()) },
            fn = { deps ->
                @Suppress("UNCHECKED_CAST")
                val grouping = deps[0] as GroupingState

                @Suppress("UNCHECKED_CAST")
                val rowModel = deps[1] as RowModel<TData>

                if (rowModel.rows.isEmpty() || grouping.isEmpty()) {
                    rowModel.rows.forEach { row ->
                        row.depth = 0
                        row.parentId = null
                    }
                    return@memo rowModel
                }

                // Filter the grouping list down to columns that exist.
                val existingGrouping = grouping.filter { columnId ->
                    table.getColumn(columnId) != null
                }

                val groupedFlatRows = mutableListOf<Row<TData>>()
                val groupedRowsById = mutableMapOf<String, Row<TData>>()
                // const onlyGroupedFlatRows: Row[] = [];
                // const onlyGroupedRowsById: Record<RowId, Row> = {};
                // const nonGroupedFlatRows: Row[] = [];
                // const nonGroupedRowsById: Record<RowId, Row> = {};

                // Recursively group the data.
                fun groupUpRecursively(
                    rows: List<Row<TData>>,
                    depth: Int = 0,
                    parentId: String? = null,
                ): List<Row<TData>> {
                    // Grouping depth has been met — stop grouping and simply
                    // rewrite the depth and row relationships.
                    if (depth >= existingGrouping.size) {
                        return rows.map { row ->
                            row.depth = depth

                            groupedFlatRows.add(row)
                            groupedRowsById[row.id] = row

                            row.subRows = groupUpRecursively(row.subRows, depth + 1, row.id)

                            row
                        }
                    }

                    val columnId: String = existingGrouping[depth]

                    // Group the rows together for this level.
                    val rowGroupsMap = groupBy(rows, columnId)

                    // Perform aggregations for each group.
                    val aggregatedGroupedRows = rowGroupsMap.entries.withIndex().map { (index, entry) ->
                        val groupingValue = entry.key
                        val groupedRows = entry.value

                        var id = "$columnId:$groupingValue"
                        id = if (isTruthy(parentId)) "$parentId>$id" else id

                        // First, recurse to group sub-rows before aggregation.
                        val subRows = groupUpRecursively(groupedRows, depth + 1, id)

                        subRows.forEach { subRow ->
                            subRow.parentId = id
                        }

                        // Flatten the leaf rows of the rows in this group. At
                        // depth 0 the group rows are already leaves.
                        val leafRows = if (depth != 0) {
                            flattenBy(groupedRows) { row -> row.subRows }
                        } else {
                            groupedRows
                        }

                        val row = createRow(
                            table,
                            id,
                            leafRows[0].original,
                            index,
                            depth,
                            null,
                            parentId,
                        )

                        // Attach grouping metadata to the synthetic parent row.
                        row.groupingColumnId = columnId
                        row.groupingValue = groupingValue
                        row.subRows = subRows
                        // `leafRows` is a runtime-only field exposed by the
                        // [Row] class for this builder to populate.
                        row.leafRows = leafRows
                        row.getValue = getValue@{ getValueColumnId ->
                            // Don't aggregate columns that are in the grouping.
                            if (existingGrouping.contains(getValueColumnId)) {
                                if (row._valuesCache.containsKey(getValueColumnId)) {
                                    return@getValue row._valuesCache[getValueColumnId]
                                }

                                val firstGroupedRow = groupedRows.getOrNull(0)
                                if (firstGroupedRow != null) {
                                    row._valuesCache[getValueColumnId] =
                                        firstGroupedRow.getValue(getValueColumnId)
                                }

                                return@getValue row._valuesCache[getValueColumnId]
                            }

                            if (row._groupingValuesCache.containsKey(getValueColumnId)) {
                                return@getValue row._groupingValuesCache[getValueColumnId]
                            }

                            // Aggregate the values.
                            val column = table.getColumn(getValueColumnId)
                            val aggregateFn = column?.getAggregationFn()

                            if (aggregateFn != null) {
                                row._groupingValuesCache[getValueColumnId] = aggregateFn(
                                    getValueColumnId,
                                    leafRows,
                                    groupedRows,
                                )

                                return@getValue row._groupingValuesCache[getValueColumnId]
                            }

                            return@getValue null
                        }

                        subRows.forEach { subRow ->
                            groupedFlatRows.add(subRow)
                            groupedRowsById[subRow.id] = subRow
                            // if (subRow.getIsGrouped?.()) {
                            //   onlyGroupedFlatRows.push(subRow);
                            //   onlyGroupedRowsById[subRow.id] = subRow;
                            // } else {
                            //   nonGroupedFlatRows.push(subRow);
                            //   nonGroupedRowsById[subRow.id] = subRow;
                            // }
                        }

                        row
                    }

                    return aggregatedGroupedRows
                }

                val groupedRows = groupUpRecursively(rowModel.rows, 0)

                groupedRows.forEach { subRow ->
                    groupedFlatRows.add(subRow)
                    groupedRowsById[subRow.id] = subRow
                    // if (subRow.getIsGrouped?.()) {
                    //   onlyGroupedFlatRows.push(subRow);
                    //   onlyGroupedRowsById[subRow.id] = subRow;
                    // } else {
                    //   nonGroupedFlatRows.push(subRow);
                    //   nonGroupedRowsById[subRow.id] = subRow;
                    // }
                }

                RowModel(
                    rows = groupedRows,
                    flatRows = groupedFlatRows,
                    rowsById = groupedRowsById,
                )
            },
            opts = getMemoOptions(
                table.options,
                "debugTable",
                "getGroupedRowModel",
            ) {
                table._queue {
                    table._autoResetExpanded()
                    table._autoResetPageIndex()
                }
            },
        )
                return@factory { memoized() }
    }
}

/**
 * Buckets [rows] by the stringified grouping value of [columnId] in
 * first-insertion order. The returned map preserves insertion order and
 * is backed by a [LinkedHashMap].
 */
private fun <TData> groupBy(rows: List<Row<TData>>, columnId: String): Map<Any?, MutableList<Row<TData>>> {
    val groupMap = LinkedHashMap<Any?, MutableList<Row<TData>>>()

    return rows.fold(groupMap) { map, row ->
        val resKey = "${row.getGroupingValue(columnId)}"
        val previous = map[resKey]
        if (previous == null) {
            map[resKey] = mutableListOf(row)
        } else {
            previous.add(row)
        }
        map
    }
}
