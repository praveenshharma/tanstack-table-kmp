package io.github.tanstacktable.core

import kotlin.math.round
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/*
 * Internal utility functions shared across the table-core implementation:
 * the `Updater` resolver, the dependency-tracking memoiser, a JS-style
 * truthiness helper and a few small predicates.
 */

/**
 * Resolves an [Updater] against an input value.
 *
 * An updater is either a new value of type [T] or a function `(T) -> T` that
 * produces one. When [updater] is callable it is invoked with [input];
 * otherwise it is returned as the new value. The runtime check uses
 * `updater is Function<*>` to match any function value.
 */
@Suppress("UNCHECKED_CAST")
fun <T> functionalUpdate(updater: Any?, input: T): T {
    return if (updater is Function<*>) {
        (updater as (T) -> T)(input)
    } else {
        updater as T
    }
}

/**
 * An intentionally empty function used where a callable no-op is required.
 */
fun noop() {
    //
}

/**
 * Returns `true` when [d] is a function value (any arity).
 */
fun isFunction(d: Any?): Boolean = d is Function<*>

/**
 * Returns `true` when [d] is a non-null list whose every element is a
 * [Number].
 */
fun isNumberArray(d: Any?): Boolean =
    d is List<*> && d.all { it is Number }

/**
 * Walks [arr] depth-first and returns every node together with each node's
 * descendants. [getChildren] returns `null` (or an empty list) for leaf
 * nodes.
 */
fun <TNode> flattenBy(
    arr: List<TNode>,
    getChildren: (item: TNode) -> List<TNode>?,
): List<TNode> {
    val flat = mutableListOf<TNode>()

    fun recurse(subArr: List<TNode>) {
        for (item in subArr) {
            flat.add(item)
            val children = getChildren(item)
            if (children != null && children.isNotEmpty()) {
                recurse(children)
            }
        }
    }

    recurse(arr)

    return flat
}

/**
 * Options bag for [memo] / [memoWithArg].
 *
 * [key] identifies the memoised computation in debug output. [debug] gates
 * the timing log on each call. [onChange] is invoked whenever the cached
 * result is recomputed.
 */
class MemoOptions<TResult>(
    val key: Any?,
    val debug: (() -> Any?)? = null,
    val onChange: ((TResult) -> Unit)? = null,
)

/**
 * Dependency-tracking memoiser with a thread-through argument.
 *
 * Re-runs [fn] only when one of the dependencies returned by [getDeps] has
 * changed by reference (`!==`) compared with the previous call. Dependencies
 * are carried as `List<Any?>` and indexed inside [fn]; this trades the JS
 * tuple-spread typing for a single list parameter that fits Kotlin's type
 * system.
 *
 * Returns a function of `(TArg) -> TResult`; pair with [memo] when no
 * argument is needed.
 */
fun <TArg, TResult> memoWithArg(
    getDeps: (depArgs: TArg) -> List<Any?>,
    fn: (deps: List<Any?>) -> TResult,
    opts: MemoOptions<TResult>,
): (depArgs: TArg) -> TResult {
    var deps: List<Any?> = emptyList()
    var result: TResult? = null

    return { depArgs ->
        var depTime: TimeMark? = null
        if (isTruthy(opts.key) && opts.debug != null) depTime = TimeSource.Monotonic.markNow()

        val newDeps = getDeps(depArgs)

        val depsChanged =
            newDeps.size != deps.size ||
                newDeps.withIndex().any { (index, dep) -> deps[index] !== dep }

        if (!depsChanged) {
            @Suppress("UNCHECKED_CAST")
            result as TResult
        } else {
            deps = newDeps

            var resultTime: TimeMark? = null
            if (isTruthy(opts.key) && opts.debug != null) resultTime = TimeSource.Monotonic.markNow()

            result = fn(newDeps)
            @Suppress("UNCHECKED_CAST")
            opts.onChange?.invoke(result as TResult)

            if (isTruthy(opts.key) && opts.debug != null) {
                if (isTruthy(opts.debug.invoke())) {
                    val depEndTime =
                        round(depTime!!.elapsedNow().inWholeMilliseconds.toDouble() * 100) / 100
                    val resultEndTime =
                        round(resultTime!!.elapsedNow().inWholeMilliseconds.toDouble() * 100) / 100

                    @Suppress("UNUSED_VARIABLE")
                    val resultFpsPercentage = resultEndTime / 16

                    fun pad(str: Any?, num: Int): String {
                        var s = str.toString()
                        while (s.length < num) {
                            s = " $s"
                        }
                        return s
                    }

                    println("⏱ ${pad(resultEndTime, 5)} /${pad(depEndTime, 5)} ms ${opts.key}")
                }
            }

            @Suppress("UNCHECKED_CAST")
            result as TResult
        }
    }
}

/**
 * No-argument [memoWithArg]. Most table-core memoised computations have no
 * runtime input and use this form.
 */
fun <TResult> memo(
    getDeps: () -> List<Any?>,
    fn: (deps: List<Any?>) -> TResult,
    opts: MemoOptions<TResult>,
): () -> TResult {
    val memoized = memoWithArg<Unit, TResult>({ getDeps() }, fn, opts)
    return { memoized(Unit) }
}

/**
 * JavaScript-style truthiness for values typed as `Any?`. Falsy: `null`,
 * `false`, `0` (any numeric type), empty string, NaN. Everything else is
 * truthy. Used internally where the source applies an `if (value)` test to
 * an `any`-typed value.
 */
internal fun isTruthy(value: Any?): Boolean = when (value) {
    null -> false
    is Boolean -> value
    is String -> value.isNotEmpty()
    is Int -> value != 0
    is Long -> value != 0L
    is Short -> value != 0.toShort()
    is Byte -> value != 0.toByte()
    is Double -> value != 0.0 && !value.isNaN()
    is Float -> value != 0f && !value.isNaN()
    else -> true
}

/**
 * Returns a setter that updates one [TableState] field by [key], applying
 * [functionalUpdate] to merge new values. Used by feature `getDefaultOptions`
 * implementations to wire `onXxxChange` callbacks to `setState`.
 *
 * [key] must be a known [TableState] field name; [TableState.copyWithKey]
 * dispatches the assignment.
 */
@Suppress("UNCHECKED_CAST")
fun makeStateUpdater(key: String, instance: Any?): (updater: Updater) -> Unit {
    return { updater ->
        (instance as Table<Any?>).setState(fun(old: TableState): TableState {
            return old.copyWithKey(key, functionalUpdate(updater, old.getByKey(key)))
        })
    }
}

/**
 * Builds a [MemoOptions] from a debug level (`"debugAll"`, `"debugCells"`,
 * `"debugTable"`, `"debugColumns"`, `"debugRows"`, `"debugHeaders"`) and a
 * memo key. The `debug` callback reports whether the relevant debug flag is
 * enabled; `debugAll` takes priority over the per-area flag.
 *
 * The memo `key` is fixed to `false` here because commonMain has no portable
 * build-time "development" flag. This disables [memo]'s debug timing output
 * — set up an explicit feature flag if you need it.
 */
fun <TResult> getMemoOptions(
    tableOptions: TableOptionsResolved<*>,
    debugLevel: String,
    key: String,
    onChange: ((result: TResult) -> Unit)? = null,
): MemoOptions<TResult> {
    return MemoOptions(
        debug = {
            tableOptions.debugAll ?: when (debugLevel) {
                "debugAll" -> tableOptions.debugAll
                "debugCells" -> tableOptions.debugCells
                "debugTable" -> tableOptions.debugTable
                "debugColumns" -> tableOptions.debugColumns
                "debugRows" -> tableOptions.debugRows
                "debugHeaders" -> tableOptions.debugHeaders
                else -> null
            }
        },
        key = false,
        onChange = onChange,
    )
}
