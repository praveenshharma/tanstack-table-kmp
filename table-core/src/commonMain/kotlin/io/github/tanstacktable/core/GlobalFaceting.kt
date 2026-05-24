package io.github.tanstacktable.core

/*
 * Global-faceting feature. Builds row-model/uniqueValues/min-max facets
 * keyed by the synthetic column id `"__global__"`, used by global filtering.
 *
 * The `Table` extension members it installs (`_getGlobalFacetedRowModel`,
 * `getGlobalFacetedRowModel`, ...) are declared on the unified `Table` type.
 */

/**
 * The `GlobalFaceting` feature. Wires the global facet accessors onto each
 * [Table] when installed.
 */
object GlobalFaceting : TableFeature {

    override val createTable: ((table: Table<Any?>) -> Unit)?
        get() = { table ->
            // When the user provides a `getFacetedRowModel` factory, invoke it
            // with the global column id; otherwise leave the cached accessor
            // null and fall back to the pre-filtered row model below.
            table._getGlobalFacetedRowModel =
                if (table.options.getFacetedRowModel != null) {
                    table.options.getFacetedRowModel!!(table, "__global__")
                } else {
                    null
                }

            table.getGlobalFacetedRowModel = {
                if (isTruthy(table.options.manualFiltering) || table._getGlobalFacetedRowModel == null) {
                    table.getPreFilteredRowModel()
                } else {
                    table._getGlobalFacetedRowModel!!()
                }
            }

            table._getGlobalFacetedUniqueValues =
                if (table.options.getFacetedUniqueValues != null) {
                    table.options.getFacetedUniqueValues!!(table, "__global__")
                } else {
                    null
                }
            table.getGlobalFacetedUniqueValues = {
                if (table._getGlobalFacetedUniqueValues == null) {
                    emptyMap()
                } else {
                    table._getGlobalFacetedUniqueValues!!()
                }
            }

            table._getGlobalFacetedMinMaxValues =
                if (table.options.getFacetedMinMaxValues != null) {
                    table.options.getFacetedMinMaxValues!!(table, "__global__")
                } else {
                    null
                }
            table.getGlobalFacetedMinMaxValues = {
                if (table._getGlobalFacetedMinMaxValues == null) {
                    null
                } else {
                    table._getGlobalFacetedMinMaxValues!!()
                }
            }
        }
}
