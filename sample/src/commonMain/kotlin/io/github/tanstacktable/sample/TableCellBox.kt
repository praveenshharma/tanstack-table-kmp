package io.github.tanstacktable.sample

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Sample-local fixed-width bordered cell. Used by the filter screens, which
 * lay their rows out with plain `Row { ... }` (one [TableCellBox] per column)
 * instead of `TableGrid` so that each `OutlinedTextField` filter input is
 * placed in its own composition root — keeping IME `InputConnection` callbacks
 * unaffected by sibling layout work.
 */
@Composable
internal fun TableCellBox(width: Int = 120, content: @Composable () -> Unit) {
    Box(
        Modifier
            .width(width.dp)
            .border(1.dp, Color(0xFFBDBDBD))
            .padding(8.dp),
    ) {
        content()
    }
}
