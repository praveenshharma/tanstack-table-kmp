package io.github.tanstacktable.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Navigation host for the `:sample` app. Each entry is a self-contained
 * example screen built on top of `:table-core` and `:table-compose`. A minimal
 * state-driven switcher — no navigation library — keeps the focus on the table
 * engine itself.
 */

private enum class SampleScreen(val title: String) {
    BASIC("Basic"),
    SORTING("Sorting"),
    FILTERS("Filters"),
    FILTERS_FACETED("Filters — Faceted"),
    PAGINATION("Pagination"),
    GROUPING("Grouping"),
    EXPANDING("Expanding"),
    ROW_SELECTION("Row Selection"),
    ROW_PINNING("Row Pinning"),
    COLUMN_PINNING("Column Pinning"),
    COLUMN_VISIBILITY("Column Visibility"),
    COLUMN_ORDERING("Column Ordering"),
    COLUMN_GROUPS("Column Groups"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleApp() {
    var current by remember { mutableStateOf<SampleScreen?>(null) }
    val screen = current

    if (screen == null) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("TanStack Table — Kotlin Multiplatform", fontWeight = FontWeight.Bold)
            Text("Sample screens demonstrating the engine and Compose adapter.")
            Spacer(Modifier.height(12.dp))
            for (s in SampleScreen.entries) {
                TextButton(onClick = { current = s }) { Text(s.title) }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            // Material3 TopAppBar handles its own status-bar inset
            // (`TopAppBarDefaults.windowInsets` includes the top inset), so the
            // example branch needs no explicit `Modifier.statusBarsPadding()`.
            TopAppBar(
                title = { Text(screen.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { current = null }) {
                        // Plain text arrow as the back affordance; this module
                        // does not depend on `material-icons-core`.
                        Text("←")
                    }
                },
            )
            when (screen) {
                SampleScreen.BASIC -> BasicExample()
                SampleScreen.SORTING -> SortingExample()
                SampleScreen.FILTERS -> FiltersExample()
                SampleScreen.FILTERS_FACETED -> FiltersFacetedExample()
                SampleScreen.PAGINATION -> PaginationExample()
                SampleScreen.GROUPING -> GroupingExample()
                SampleScreen.EXPANDING -> ExpandingExample()
                SampleScreen.ROW_SELECTION -> RowSelectionExample()
                SampleScreen.ROW_PINNING -> RowPinningExample()
                SampleScreen.COLUMN_PINNING -> ColumnPinningExample()
                SampleScreen.COLUMN_VISIBILITY -> ColumnVisibilityExample()
                SampleScreen.COLUMN_ORDERING -> ColumnOrderingExample()
                SampleScreen.COLUMN_GROUPS -> ColumnGroupsExample()
            }
        }
    }
}
