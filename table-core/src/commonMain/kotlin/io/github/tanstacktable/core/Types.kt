package io.github.tanstacktable.core

/*
 * Shared type declarations used across the table-core implementation.
 *
 * The composed objects — `Table`, `Column`, `Row`, `Cell`, `Header`,
 * `HeaderGroup`, `TableState`, `TableOptions`, `TableOptionsResolved`,
 * `InitialTableState`, `ColumnDef` — live in their own files because each
 * is a single mutable class consolidating members that several features
 * contribute to. Kotlin classes are closed, so they must be authored
 * complete in one place.
 */

/**
 * Extension point for table features. Each non-null member is invoked
 * during the corresponding construction step:
 *
 *  - [getDefaultOptions], [getInitialState], [getDefaultColumnDef] feed
 *    into options/state defaults and column-def defaults respectively.
 *  - [createTable] runs once per [Table] to install table-level members.
 *  - [createColumn] runs once per [Column].
 *  - [createHeader] runs once per [Header].
 *  - [createRow] runs once per [Row].
 *  - [createCell] runs once per [Cell].
 *
 * `Any?` (rather than star projections) is used throughout so feature code
 * can both read and assign object members.
 */
interface TableFeature {
    val createCell: ((cell: Cell<Any?, Any?>, column: Column<Any?, Any?>, row: Row<Any?>, table: Table<Any?>) -> Unit)?
        get() = null
    val createColumn: ((column: Column<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = null
    val createHeader: ((header: Header<Any?, Any?>, table: Table<Any?>) -> Unit)?
        get() = null
    val createRow: ((row: Row<Any?>, table: Table<Any?>) -> Unit)?
        get() = null
    val createTable: ((table: Table<Any?>) -> Unit)?
        get() = null
    val getDefaultColumnDef: (() -> ColumnDef<Any?, Any?>)?
        get() = null
    val getDefaultOptions: ((table: Table<Any?>) -> TableOptionsResolved<Any?>)?
        get() = null
    val getInitialState: ((initialState: InitialTableState?) -> TableState)?
        get() = null
}

/**
 * Marker interface for caller-supplied table-wide metadata. Empty by
 * design — implementors carry whatever payload their app needs.
 */
interface TableMeta<TData>

/**
 * Marker interface for caller-supplied per-column metadata.
 */
interface ColumnMeta<TData, TValue>

/**
 * Marker interface for caller-supplied filter-side metadata.
 */
interface FilterMeta

/**
 * Marker interface for a caller-registered set of filter functions.
 *
 * Renamed from `FilterFns` to avoid a case-only `.class` filename collision
 * with the [filterFns] object on case-insensitive filesystems (macOS/Windows).
 */
interface FilterFnsRegistry

/**
 * Marker interface for a caller-registered set of sort functions.
 *
 * Renamed from `SortingFns` to avoid a case-only `.class` filename collision
 * with the [sortingFns] object on case-insensitive filesystems.
 */
interface SortingFnsRegistry

/**
 * Marker interface for a caller-registered set of aggregation functions.
 *
 * Renamed from `AggregationFns` to avoid a case-only `.class` filename
 * collision with the [aggregationFns] object on case-insensitive filesystems.
 */
interface AggregationFnsRegistry

/**
 * Either a new value or a function `(old) -> new` that produces one. Resolved
 * by [functionalUpdate] at the call site. Typed as `Any?` because Kotlin
 * cannot express the underlying union.
 */
typealias Updater = Any?

/**
 * Setter signature: accepts either a new value or a function `(old) -> new`.
 */
typealias OnChangeFn = (updaterOrValue: Updater) -> Unit

/**
 * Documentation alias for the row-data type parameter — unbounded in the
 * Kotlin port, equivalent to `Any?`.
 */
typealias RowData = Any?

/**
 * Generic render signature accepting a component and its props. Provided
 * for adapter layers that wire table-core into a UI framework.
 */
typealias AnyRender = (comp: Any?, props: Any?) -> Any?

/**
 * Reads a value from an original row.
 */
typealias AccessorFn<TData, TValue> = (originalRow: TData, index: Int) -> TValue

/**
 * Cell/header template. At runtime either a `String` or a `(props) -> Any?`
 * renderer; consuming code (e.g. `flexRender` in the adapter layer)
 * discriminates by runtime type.
 */
typealias ColumnDefTemplate = Any?

/**
 * Header template — a `String` or a `(HeaderContext) -> Any?` renderer.
 */
typealias StringOrTemplateHeader = Any?

/**
 * Column-identifier shape: a string header with optional id.
 */
interface StringHeaderIdentifier {
    val header: String
    val id: String?
}

/**
 * Column-identifier shape: an explicit id with optional header template.
 */
interface IdIdentifier<TData, TValue> {
    val id: String
    val header: StringOrTemplateHeader?
}

/**
 * Identifier shape on a column def — either [IdIdentifier] or
 * [StringHeaderIdentifier]. Typed as `Any?` because Kotlin cannot express
 * the union directly.
 */
typealias ColumnIdentifiers = Any?

/**
 * A computed view of the table's rows.
 *
 * [rows] is the (possibly nested) row tree.
 * [flatRows] is every row in iteration order.
 * [rowsById] indexes every row by its id for `O(1)` lookup.
 */
class RowModel<TData>(
    val rows: List<Row<TData>>,
    val flatRows: List<Row<TData>>,
    val rowsById: Map<String, Row<TData>>,
)
