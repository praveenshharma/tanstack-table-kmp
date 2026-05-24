package io.github.tanstacktable.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.tanstacktable.core.Table
import io.github.tanstacktable.core.TableOptions
import io.github.tanstacktable.core.TableOptionsResolved
import io.github.tanstacktable.core.copyTableOptions
import io.github.tanstacktable.core.createTable
import io.github.tanstacktable.core.functionalUpdate
import io.github.tanstacktable.core.mergeTableOptions

/*
 * Compose Multiplatform adapter for the headless table engine in `:table-core`.
 *
 * This module exposes [rememberTable] — the Compose entry point that creates a
 * [Table] and bridges its state into the composition so the table is reactive —
 * and [flexRender], the helper used to resolve a column / header / footer
 * template into its rendered value.
 *
 * `:table-compose` re-exports `:table-core` as an `api` dependency, so
 * consumers of this module also see [Table], `ColumnDef`, `createColumnHelper`,
 * the row-model factories, and the rest of the engine surface directly.
 */

/**
 * The value produced by a column / header / footer template.
 *
 * Templates may resolve to either already-rendered content (for example a
 * `String`) or to a callable that produces that content when given its props.
 * Both arms erase to `Any?` in Kotlin, so [Renderable] is exposed as `Any?`.
 */
typealias Renderable = Any?

/**
 * Resolves a column / header / footer template to its rendered value.
 *
 * If [comp] is `null`, returns `null`. If [comp] is a function (the typical
 * shape of an engine template such as `cell = { props -> props.renderValue() }`),
 * it is invoked with [props] and its result returned. Any other value is
 * returned unchanged.
 *
 * [flexRender] preserves value semantics only — it resolves the template to its
 * content. Emitting that content as Compose UI is the caller's responsibility;
 * for the engine's default templates the resolved value is a `String?`.
 *
 * Engine templates are arity-1 (`(props) -> ...`); a function value of a
 * different arity will fail at the call site.
 */
@Suppress("UNCHECKED_CAST")
fun flexRender(comp: Renderable, props: Any?): Any? {
    return when (comp) {
        null -> {
            null
        }
        is Function<*> -> {
            (comp as (Any?) -> Any?)(props)
        }

        else -> {
            comp
        }
    }
}

/**
 * Creates a [Table] from [options] and bridges its state into the composition
 * so the table is reactive.
 *
 * The [Table] instance itself is created once and remembered for the lifetime
 * of the enclosing composition — it is never replaced. The table's state is
 * held in a Compose [mutableStateOf]; reading the table's state inside the
 * composition subscribes the caller to recomposition whenever that state
 * changes (for example via sorting, filtering, pagination, or selection).
 *
 * Caller-controlled state (passing `options.state` to override fields managed
 * by the engine) is not currently supported by this Compose adapter; the
 * internally-managed state is always used. Reacting to state changes via
 * [TableOptions.onStateChange] is fully supported.
 */
@Composable
fun <TData> rememberTable(options: TableOptions<TData>): Table<TData> {
    // Create the [Table] once and reuse it across recompositions. Every field
    // in [TableOptionsResolved] already carries a sensible default; the only
    // additional default applied here is a noop [onStateChange] when the
    // caller did not provide one.
    val table = remember {
        val resolvedOptions = copyTableOptions(options)
        if (resolvedOptions.onStateChange == null) {
            resolvedOptions.onStateChange = {}
        }
        createTable(resolvedOptions)
    }

    // Compose-backed table state. Reading [state] inside this composable
    // subscribes [rememberTable] to recomposition when the table's state
    // changes.
    var state by remember { mutableStateOf(table.initialState) }

    // Runs on every recomposition: re-applies the latest [options] to the
    // table and wires [onStateChange] so engine-driven state updates flow back
    // into the Compose [mutableStateOf] above (and on to any caller-supplied
    // [TableOptions.onStateChange]).
    table.setOptions(
        fun(prev: TableOptionsResolved<TData>): TableOptionsResolved<TData> {
            val merged = mergeTableOptions(prev, options)
            // The internally-managed [state] is used; see the KDoc note on
            // caller-controlled state above.
            merged.state = state
            merged.onStateChange = { updater ->
                state = functionalUpdate(updater, state)
                options.onStateChange?.invoke(updater)
            }
            return merged
        },
    )

    return table
}
