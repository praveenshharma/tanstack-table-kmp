package io.github.tanstacktable.core

/*
 * Cell construction. The `Cell` class and `CellContext` live in Cell.kt; this
 * file holds `createCell`, which assembles a cell, wires its accessors and
 * runs each installed feature's `createCell` hook.
 */

/**
 * Constructs a [Cell] for the given [row]/[column] pair, then runs every
 * registered feature's `createCell` hook so features can attach their own
 * members (`getIsGrouped`, `getIsAggregated`, ...).
 */
@Suppress("UNCHECKED_CAST")
fun <TData, TValue> createCell(
    table: Table<TData>,
    row: Row<TData>,
    column: Column<TData, TValue>,
    columnId: String,
): Cell<TData, TValue> {
    // Declared first so `getRenderValue` and the memoised `getContext` below
    // can capture it by closure.
    val cell = Cell<TData, TValue>()

    // Falls back to `renderFallbackValue` when the cell value is null.
    val getRenderValue: () -> TValue? = {
        cell.getValue() ?: (table.options.renderFallbackValue as TValue?)
    }

    cell.id = "${row.id}_${column.id}"
    cell.row = row
    cell.column = column
    // The unchecked cast bridges the erased `TValue` carried on `Row.getValue`,
    // which is typed `(String) -> Any?`.
    cell.getValue = { row.getValue(columnId) as TValue }
    cell.renderValue = getRenderValue
    cell.getContext = memo(
        getDeps = { listOf(table, column, row, cell) },
        fn = { deps ->
            val depTable = deps[0] as Table<TData>
            val depColumn = deps[1] as Column<TData, TValue>
            val depRow = deps[2] as Row<TData>
            val depCell = deps[3] as Cell<TData, TValue>
            CellContext(
                table = depTable,
                column = depColumn,
                row = depRow,
                cell = depCell,
                getValue = depCell.getValue,
                renderValue = depCell.renderValue,
            )
        },
        opts = getMemoOptions(table.options, "debugCells", "cell.getContext"),
    )

    table._features.forEach { feature ->
        feature.createCell?.invoke(
            cell as Cell<Any?, Any?>,
            column as Column<Any?, Any?>,
            row as Row<Any?>,
            table as Table<Any?>,
        )
    }

    return cell
}
