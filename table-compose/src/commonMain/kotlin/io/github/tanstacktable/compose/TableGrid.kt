package io.github.tanstacktable.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * A bordered Compose grid for rendering headless table output.
 *
 * `table-core` is design-free: it returns row/cell/header models but emits no
 * UI. In the browser an HTML `<table>` fills that gap for free, content-sizing
 * every column by default. Compose has no equivalent built-in, so consumers
 * either reinvent a `SubcomposeLayout`-based grid or pin every column to a
 * fixed width. [TableGrid] is the reusable substrate the adapter ships so the
 * common case — content-sized columns, optional column-span cells, IME-safe
 * text fields inside cells — works out of the box.
 *
 * Theme-neutral: this file depends only on `compose.foundation` (no Material).
 * Cell text is rendered with [BasicText]; wrap calls in your theme of choice
 * for typography and color.
 */

private val DefaultGridLineColor = Color(0xFFBDBDBD)

/**
 * Renders a [flexRender] result as plain text. Convenience wrapper around
 * [BasicText] that tolerates `null` and exposes a bold variant for headers.
 *
 * For themed typography wrap your own `Text` composable around the
 * [flexRender] output instead — this helper is intentionally minimal so the
 * adapter stays free of Material/Cupertino dependencies.
 */
@Composable
fun TableCellText(value: Any?, bold: Boolean = false) {
    BasicText(
        text = value?.toString() ?: "",
        style = if (bold) TextStyle(fontWeight = FontWeight.Bold) else TextStyle.Default,
    )
}

/**
 * Per-column width policy used by [TableGrid].
 *
 * - [Auto] — column sizes to its widest cell, the Compose equivalent of
 *   `table-layout: auto`. Default.
 * - [Fixed] — column is pinned to [Fixed.value].
 */
sealed interface ColumnWidth {
    data object Auto : ColumnWidth
    data class Fixed(val value: Dp) : ColumnWidth
}

/** Builder scope for [TableGrid]: collects rows of cells. */
class TableGridScope internal constructor() {
    internal class Cell(val colSpan: Int, val content: @Composable () -> Unit)

    internal val rows = mutableListOf<List<Cell>>()

    /** Adds one row; declare its cells inside [content] via [TableGridRowScope.cell]. */
    fun row(content: TableGridRowScope.() -> Unit) {
        val cells = mutableListOf<Cell>()
        TableGridRowScope(cells).content()
        rows.add(cells)
    }
}

/** Builder scope for one [TableGrid] row. */
class TableGridRowScope internal constructor(
    private val cells: MutableList<TableGridScope.Cell>,
) {
    /** Adds one cell, optionally spanning [colSpan] columns (grouped headers). */
    fun cell(colSpan: Int = 1, content: @Composable () -> Unit) {
        cells.add(TableGridScope.Cell(colSpan, content))
    }
}

/**
 * A bordered grid for rendering a headless table.
 *
 * Every column is content-sized by default (`ColumnWidth.Auto`, equivalent to
 * `table-layout: auto`); pin a column to a width by returning
 * [ColumnWidth.Fixed] from [columnWidth]. Rows and cells are declared through
 * [content]; a cell may span columns for grouped headers. The column count is
 * derived from the widest row.
 *
 * **Implementation note (intrinsics).** [TableGrid] measures cells through a
 * [SubcomposeLayout] and deliberately does **not** query intrinsic widths or
 * heights. Querying an intrinsic recursively re-composes any nested
 * [SubcomposeLayout] — every Material `OutlinedTextField` contains one — and
 * that recursive re-composition can jam the main thread long enough to time
 * out the IME's `InputConnection`, causing a filter text field inside the
 * grid to silently reject input. The per-cell border is drawn by a separate
 * empty bordered [Box] sized to the grid slot, so a column-spanning grouped
 * header still gets one full border.
 *
 * **Implementation note (recomposition).** Marked [NonRestartableComposable]
 * so [TableGrid] always re-runs when its caller recomposes (and the builder
 * rebuilds cells from current engine state). Without this, Compose's
 * parameter-equality skip can drop the recomposition when the [content]
 * lambda's captures look stable (for example, if it only captures the
 * long-lived `Table` reference), leaving the grid showing stale header / cell
 * order after `setColumnOrder` and similar state changes. Anything composed
 * **inside** [content] inherits the same re-run cadence; you do not need to
 * annotate your own cell composables.
 *
 * @param modifier applied to the outer [SubcomposeLayout].
 * @param columnWidth per-column width policy, indexed from 0.
 * @param gridLineColor color of the per-cell border. Defaults to a neutral grey.
 * @param cellPadding inner padding between the border and cell content.
 * @param content row/cell builder.
 */
@Composable
@NonRestartableComposable
fun TableGrid(
    modifier: Modifier = Modifier,
    columnWidth: (column: Int) -> ColumnWidth = { ColumnWidth.Auto },
    gridLineColor: Color = DefaultGridLineColor,
    cellPadding: Dp = 8.dp,
    content: TableGridScope.() -> Unit,
) {
    val rows = TableGridScope().apply(content).rows
    val columnCount = rows.maxOfOrNull { row -> row.sumOf { it.colSpan } } ?: 0

    SubcomposeLayout(modifier) { _ ->
        // 1) Compose + measure each cell's content ONCE at its natural size.
        val cellPlaceables = rows.mapIndexed { ri, row ->
            row.mapIndexed { ci, cell ->
                subcompose("c$ri-$ci") {
                    Box(Modifier.padding(cellPadding)) { cell.content() }
                }.first().measure(Constraints())
            }
        }

        // 2) Column widths — Auto = widest single-span cell (table-layout: auto).
        val colWidth = IntArray(columnCount)
        rows.forEachIndexed { ri, row ->
            var col = 0
            row.forEachIndexed { ci, cell ->
                if (cell.colSpan == 1 && col < columnCount && columnWidth(col) == ColumnWidth.Auto) {
                    colWidth[col] = maxOf(colWidth[col], cellPlaceables[ri][ci].width)
                }
                col += cell.colSpan
            }
        }
        for (c in 0 until columnCount) {
            (columnWidth(c) as? ColumnWidth.Fixed)?.let { colWidth[c] = it.value.roundToPx() }
        }
        // A spanning cell wider than the columns it covers widens the Auto ones.
        rows.forEachIndexed { ri, row ->
            var col = 0
            row.forEachIndexed { ci, cell ->
                if (cell.colSpan > 1) {
                    val span = (col until minOf(col + cell.colSpan, columnCount)).toList()
                    val have = span.sumOf { colWidth[it] }
                    val want = cellPlaceables[ri][ci].width
                    val auto = span.filter { columnWidth(it) == ColumnWidth.Auto }
                    if (want > have && auto.isNotEmpty()) {
                        val extra = (want - have) / auto.size
                        auto.forEach { colWidth[it] += extra }
                    }
                }
                col += cell.colSpan
            }
        }
        val colX = IntArray(columnCount + 1)
        for (c in 0 until columnCount) colX[c + 1] = colX[c] + colWidth[c]

        // 3) Row height = tallest cell content in the row.
        val rowHeight = IntArray(rows.size)
        rows.forEachIndexed { ri, row ->
            row.forEachIndexed { ci, _ ->
                rowHeight[ri] = maxOf(rowHeight[ri], cellPlaceables[ri][ci].height)
            }
        }
        val rowY = IntArray(rows.size + 1)
        for (r in rows.indices) rowY[r + 1] = rowY[r] + rowHeight[r]

        // 4) One empty bordered box per cell, sized to its (possibly spanned)
        //    grid slot — placed under the content so every cell, including a
        //    column-spanning grouped header, gets one full, aligned border.
        val borderPlaceables = rows.mapIndexed { ri, row ->
            var col = 0
            row.mapIndexed { ci, cell ->
                val end = minOf(col + cell.colSpan, columnCount)
                val w = (col until end).sumOf { colWidth[it] }
                col += cell.colSpan
                subcompose("b$ri-$ci") {
                    Box(Modifier.border(1.dp, gridLineColor))
                }.first().measure(Constraints.fixed(maxOf(w, 0), maxOf(rowHeight[ri], 0)))
            }
        }

        layout(colX[columnCount], rowY[rows.size]) {
            rows.forEachIndexed { ri, row ->
                var col = 0
                row.forEachIndexed { ci, cell ->
                    val x = colX[col]
                    val y = rowY[ri]
                    borderPlaceables[ri][ci].place(x, y)
                    cellPlaceables[ri][ci].place(x, y)
                    col += cell.colSpan
                }
            }
        }
    }
}
