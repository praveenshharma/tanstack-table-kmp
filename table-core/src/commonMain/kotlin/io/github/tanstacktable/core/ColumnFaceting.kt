package io.github.tanstacktable.core

/*
 * Column-faceting feature. Per-column row-model/uniqueValues/min-max accessors
 * are built from the table-level `getFacetedRowModel` / `getFacetedUniqueValues`
 * / `getFacetedMinMaxValues` factories when provided, with safe fallbacks when
 * they are absent.
 */

/**
 * The `ColumnFaceting` feature. Installs the per-column facet accessors on
 * each [Column] when registered with the table.
 */
object ColumnFaceting : TableFeature {

    override val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = { column, table ->
            // When `getFacetedRowModel` is configured, ask it for this column's
            // faceted row-model getter; otherwise leave the cached accessor null
            // and fall back to the pre-filtered row model below.
            column._getFacetedRowModel =
                if (table.options.getFacetedRowModel != null) {
                    table.options.getFacetedRowModel!!(table, column.id)
                } else {
                    null
                }
            column.getFacetedRowModel = {
                if (column._getFacetedRowModel == null) {
                    table.getPreFilteredRowModel()
                } else {
                    column._getFacetedRowModel!!()
                }
            }
            column._getFacetedUniqueValues =
                if (table.options.getFacetedUniqueValues != null) {
                    table.options.getFacetedUniqueValues!!(table, column.id)
                } else {
                    null
                }
            column.getFacetedUniqueValues = {
                if (column._getFacetedUniqueValues == null) {
                    emptyMap<Any?, Int>()
                } else {
                    column._getFacetedUniqueValues!!()
                }
            }
            column._getFacetedMinMaxValues =
                if (table.options.getFacetedMinMaxValues != null) {
                    table.options.getFacetedMinMaxValues!!(table, column.id)
                } else {
                    null
                }
            column.getFacetedMinMaxValues = {
                if (column._getFacetedMinMaxValues == null) {
                    null
                } else {
                    column._getFacetedMinMaxValues!!()
                }
            }
        }
}
