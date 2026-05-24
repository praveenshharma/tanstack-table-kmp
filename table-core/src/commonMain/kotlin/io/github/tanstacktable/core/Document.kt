package io.github.tanstacktable.core

/*
 * Document/event helpers used by the resize handler. The browser DOM types
 * (`Document`, `Event`, `EventTarget`) do not exist in commonMain, so these
 * functions accept and return `Any?` and there is no platform global to read
 * from.
 */

/**
 * Returns [_document] when it is truthy, otherwise `null`. Mirrors the
 * JavaScript helper that would fall back to the global `document` binding;
 * commonMain has no such global, so the fallback always yields `null`.
 */
fun safelyAccessDocument(_document: Any?): Any? {
    // The global browser `document` is unreachable from commonMain, so the
    // fallback path collapses to `null`.
    return if (isTruthy(_document)) _document else null
}

/**
 * Returns the `ownerDocument` of the event's target, when reachable. The
 * required browser-DOM accessors are not available in commonMain, so this
 * always returns `null` — the same value the original guard chain returns
 * whenever the target or its `ownerDocument` is missing.
 */
fun safelyAccessDocumentEvent(event: Any?): Any? {
    // `event.target` / `.ownerDocument` are browser-DOM members absent from
    // commonMain, so there is no value to return here.
    return null
}
