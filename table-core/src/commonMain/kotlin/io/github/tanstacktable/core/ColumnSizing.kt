package io.github.tanstacktable.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/*
 * Column-sizing feature. Tracks per-column sizes plus a transient
 * resize-in-progress state, the column-level size/resize accessors and
 * the header-level resize handler.
 */

/**
 * Per-column rendered size in pixels, keyed by column id. Missing entries
 * fall back to the column def's `size`.
 */
typealias ColumnSizingState = Map<String, Double>

/**
 * Transient state captured during a column resize gesture. All fields are
 * null when no resize is in progress; [isResizingColumn] holds the
 * column id (or `false`) when one is.
 */
class ColumnSizingInfoState(
    val startOffset: Double?,
    val startSize: Double?,
    val deltaOffset: Double?,
    val deltaPercentage: Double?,
    val isResizingColumn: Any?,
    val columnSizingStart: List<Pair<String, Double>>,
)

/**
 * When the new column size is committed: `"onChange"` (live) or `"onEnd"`
 * (on gesture end).
 */
typealias ColumnResizeMode = String

/**
 * Resize-direction hint: `"ltr"` or `"rtl"`.
 */
typealias ColumnResizeDirection = String

/**
 * Defaults applied to columns that do not set their own sizing fields.
 */
class DefaultColumnSizing(
    val size: Double = 150.0,
    val minSize: Double = 20.0,
    val maxSize: Double = 9007199254740991.0,
)

/**
 * Shared [DefaultColumnSizing] instance used by [ColumnSizing] and
 * [ColumnDef.fromDefaultSizing].
 */
val defaultColumnSizing: DefaultColumnSizing = DefaultColumnSizing()

private fun getDefaultColumnSizingInfoState(): ColumnSizingInfoState =
    ColumnSizingInfoState(
        startOffset = null,
        startSize = null,
        deltaOffset = null,
        deltaPercentage = null,
        isResizingColumn = false,
        columnSizingStart = emptyList(),
    )

/**
 * The `ColumnSizing` feature. Adds `columnSizing` / `columnSizingInfo`
 * state, the column-level size/resize accessors, and the header-level
 * resize handler.
 */
object ColumnSizing : TableFeature {

    override val getDefaultColumnDef: (() -> ColumnDef<Any?, Any?>)?
        get() = {
            ColumnDef.fromDefaultSizing(defaultColumnSizing)
        }

    override val getInitialState: ((initialState: InitialTableState?) -> TableState)?
        get() = { state ->
            TableState.fromInitialState(
                state,
                columnSizing = state?.columnSizing ?: emptyMap(),
                columnSizingInfo = state?.columnSizingInfo ?: getDefaultColumnSizingInfoState(),
            )
        }

    override val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>)?
        get() = { table ->
            TableOptionsResolved<Any?>(
                columnResizeMode = "onEnd",
                columnResizeDirection = "ltr",
                onColumnSizingChange = makeStateUpdater("columnSizing", table),
                onColumnSizingInfoChange = makeStateUpdater("columnSizingInfo", table),
            )
        }

    override val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = { column, table ->
            column.getSize = {
                val columnSize = table.getState().columnSizing[column.id]

                min(
                    max(
                        column.columnDef.minSize ?: defaultColumnSizing.minSize,
                        columnSize ?: column.columnDef.size ?: defaultColumnSizing.size,
                    ),
                    column.columnDef.maxSize ?: defaultColumnSizing.maxSize,
                )
            }

            column.getStart = memoWithArg(
                { position: Any? ->
                    listOf(
                        position,
                        _getVisibleLeafColumns(table, position),
                        table.getState().columnSizing,
                    )
                },
                { deps ->
                    val position = deps[0]
                    @Suppress("UNCHECKED_CAST")
                    val columns = deps[1] as List<Column<Any?, Any?>>
                    val sliceEnd = column.getIndex(position).let { idx ->
                        if (idx < 0) maxOf(0, columns.size + idx) else idx
                    }
                    columns
                        .take(sliceEnd)
                        .fold(0.0) { sum, c -> sum + c.getSize() }
                },
                getMemoOptions(table.options, "debugColumns", "getStart"),
            )

            column.getAfter = memoWithArg(
                { position: Any? ->
                    listOf(
                        position,
                        _getVisibleLeafColumns(table, position),
                        table.getState().columnSizing,
                    )
                },
                { deps ->
                    val position = deps[0]
                    @Suppress("UNCHECKED_CAST")
                    val columns = deps[1] as List<Column<Any?, Any?>>
                    columns
                        .drop(column.getIndex(position) + 1)
                        .fold(0.0) { sum, c -> sum + c.getSize() }
                },
                getMemoOptions(table.options, "debugColumns", "getAfter"),
            )

            column.resetSize = {
                table.setColumnSizing { old: ColumnSizingState ->
                    old - column.id
                }
            }
            column.getCanResize = {
                (column.columnDef.enableResizing ?: true) &&
                    (table.options.enableColumnResizing ?: true)
            }
            column.getIsResizing = {
                table.getState().columnSizingInfo.isResizingColumn == column.id
            }
        }

    override val createHeader: ((header: Header<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = { header, table ->
            header.getSize = {
                var sum = 0.0

                fun recurse(h: Header<Any?, Any?>) {
                    if (h.subHeaders.isNotEmpty()) {
                        h.subHeaders.forEach { recurse(it) }
                    } else {
                        sum += h.column.getSize()
                    }
                }

                recurse(header)

                sum
            }
            header.getStart = {
                if (header.index > 0) {
                    val prevSiblingHeader = header.headerGroup.headers[header.index - 1]
                    prevSiblingHeader.getStart(null) + prevSiblingHeader.getSize()
                } else {
                    0.0
                }
            }
            header.getResizeHandler = { _contextDocument ->
                val column = table.getColumn(header.column.id)
                val canResize = column?.getCanResize()

                val resizeHandler: (Any?) -> Unit = handler@{ e: Any? ->
                    if (column == null || canResize != true) {
                        return@handler
                    }

                    if (isTouchStartEvent(e)) {
                        // lets not respond to multiple touches (e.g. 2 or 3 fingers)
                    }

                    val startSize = header.getSize()

                    val columnSizingStart: List<Pair<String, Double>> =
                        header.getLeafHeaders().map { d -> d.column.id to d.column.getSize() }

                    val clientX: Double? = null

                    val newColumnSizing: MutableMap<String, Double> = mutableMapOf()

                    fun updateOffset(eventType: String, clientXPos: Double?) {
                        if (clientXPos !is Number) {
                            return
                        }

                        table.setColumnSizingInfo { old: ColumnSizingInfoState ->
                            val oldInfo = old
                            val deltaDirection =
                                if (table.options.columnResizeDirection == "rtl") -1 else 1
                            val deltaOffset =
                                (clientXPos - (oldInfo.startOffset ?: 0.0)) * deltaDirection
                            val deltaPercentage = max(
                                deltaOffset / (oldInfo.startSize ?: 0.0),
                                -0.999999,
                            )

                            oldInfo.columnSizingStart.forEach { (columnId, headerSize) ->
                                newColumnSizing[columnId] =
                                    round(
                                        max(headerSize + headerSize * deltaPercentage, 0.0) * 100,
                                    ) / 100
                            }

                            ColumnSizingInfoState(
                                startOffset = oldInfo.startOffset,
                                startSize = oldInfo.startSize,
                                deltaOffset = deltaOffset,
                                deltaPercentage = deltaPercentage,
                                isResizingColumn = oldInfo.isResizingColumn,
                                columnSizingStart = oldInfo.columnSizingStart,
                            )
                        }

                        if (table.options.columnResizeMode == "onChange" ||
                            eventType == "end"
                        ) {
                            table.setColumnSizing { old: ColumnSizingState ->
                                val oldMap = old
                                oldMap + newColumnSizing
                            }
                        }
                    }

                    val onMove = { clientXPos: Double? -> updateOffset("move", clientXPos) }

                    val onEnd = { clientXPos: Double? ->
                        updateOffset("end", clientXPos)

                        table.setColumnSizingInfo { old: ColumnSizingInfoState ->
                            val oldInfo = old
                            ColumnSizingInfoState(
                                startOffset = null,
                                startSize = null,
                                deltaOffset = null,
                                deltaPercentage = null,
                                isResizingColumn = false,
                                columnSizingStart = emptyList(),
                            )
                        }
                    }

                    @Suppress("UNUSED_VARIABLE")
                    val contextDocument: Any? = null

                    table.setColumnSizingInfo { old: ColumnSizingInfoState ->
                        val oldInfo = old
                        ColumnSizingInfoState(
                            startOffset = clientX,
                            startSize = startSize,
                            deltaOffset = 0.0,
                            deltaPercentage = 0.0,
                            isResizingColumn = column.id,
                            columnSizingStart = columnSizingStart,
                        )
                    }
                }

                resizeHandler
            }
        }

    override val createTable: ((table: Table<Any?>) -> Unit)?
        get() = { table ->
            table.setColumnSizing = { updater ->
                table.options.onColumnSizingChange?.invoke(updater)
            }
            table.setColumnSizingInfo = { updater ->
                table.options.onColumnSizingInfoChange?.invoke(updater)
            }
            table.resetColumnSizing = { defaultState ->
                table.setColumnSizing(
                    if (isTruthy(defaultState)) {
                        emptyMap<String, Double>()
                    } else {
                        table.initialState.columnSizing ?: emptyMap()
                    },
                )
            }
            table.resetHeaderSizeInfo = { defaultState ->
                table.setColumnSizingInfo(
                    if (isTruthy(defaultState)) {
                        getDefaultColumnSizingInfoState()
                    } else {
                        table.initialState.columnSizingInfo
                            ?: getDefaultColumnSizingInfoState()
                    },
                )
            }
            table.getTotalSize = {
                table.getHeaderGroups().getOrNull(0)?.headers
                    ?.fold(0.0) { sum, header -> sum + header.getSize() }
                    ?: 0.0
            }
            table.getLeftTotalSize = {
                table.getLeftHeaderGroups().getOrNull(0)?.headers
                    ?.fold(0.0) { sum, header -> sum + header.getSize() }
                    ?: 0.0
            }
            table.getCenterTotalSize = {
                table.getCenterHeaderGroups().getOrNull(0)?.headers
                    ?.fold(0.0) { sum, header -> sum + header.getSize() }
                    ?: 0.0
            }
            table.getRightTotalSize = {
                table.getRightHeaderGroups().getOrNull(0)?.headers
                    ?.fold(0.0) { sum, header -> sum + header.getSize() }
                    ?: 0.0
            }
        }
}

private var passiveSupported: Boolean? = null

fun passiveEventSupported(): Boolean {
    val cached = passiveSupported
    if (cached is Boolean) return cached

    var supported = false
    try {
    } catch (err: Throwable) {
        supported = false
    }
    passiveSupported = supported
    return passiveSupported as Boolean
}

private fun isTouchStartEvent(e: Any?): Boolean {
    return false
}
