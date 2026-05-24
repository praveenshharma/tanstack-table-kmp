package io.github.tanstacktable.core

/*
 * ============================================================================
 * Deterministic test fixture builder for table-core tests.
 *
 * `makeData(vararg lens)` produces a nested row tree whose per-level row count
 * comes from `lens`: `lens[0]` rows at the top level, each with `lens[1]`
 * sub-rows, and so on. Field values are fixed placeholders — no test reads
 * them; the grouped test overwrites the grouping fields explicitly, and the
 * pinning/selection tests rely only on row ids and sub-row structure.
 *
 * Row representation — a `Person` row is a `Map<String, Any?>`. The core
 * engine's `createColumn` resolves a string `accessorKey` via
 * `originalRow as? Map<String, Any?>`, and a Kotlin data class is not
 * string-indexable. Keys: `firstName, lastName, age, visits, progress, status`,
 * plus `subRows` (`List<Map<String,Any?>>?`).
 * ============================================================================
 */

/** A row is a string-keyed map so accessor keys can index it directly. */
typealias Person = Map<String, Any?>

/** `[0, 1, ..., len-1]`. */
private fun range(len: Int): List<Int> {
    val arr = mutableListOf<Int>()
    for (i in 0 until len) {
        arr.add(i)
    }
    return arr
}

/**
 * Builds a row with the six fixed fields used across the test suite. Values
 * are deterministic placeholders; no test reads them as-is.
 */
private fun newPerson(): MutableMap<String, Any?> {
    return mutableMapOf(
        "firstName" to "firstName",
        "lastName" to "lastName",
        "age" to 0,
        "visits" to 0,
        "progress" to 0,
        "status" to "single",
    )
}

/**
 * Builds a nested list of rows. For each level `depth`, produces `lens[depth]`
 * rows; each row carries `subRows` built from `lens[depth + 1]` when that
 * level exists, otherwise `null`.
 */
fun makeData(vararg lens: Int): List<Person> {
    fun makeDataLevel(depth: Int = 0): List<Person> {
        val len = lens[depth]
        return range(len).map {
            val person = newPerson()
            // A sub-level exists when `lens` has an entry at `depth + 1` that
            // is present and non-zero.
            person["subRows"] =
                if (depth + 1 < lens.size && lens[depth + 1] != 0) {
                    makeDataLevel(depth + 1)
                } else {
                    null
                }
            person
        }
    }
    return makeDataLevel()
}
