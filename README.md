# tanstack-table-kmp

[![Maven Central](https://img.shields.io/maven-central/v/io.github.praveenshharma/table-core.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.praveenshharma/table-core)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.x-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/compose--multiplatform-1.7.x-4285F4.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Targets](https://img.shields.io/badge/targets-Android%20%7C%20iOS%20%7C%20JVM-brightgreen.svg)](#supported-targets)
[![API docs](https://img.shields.io/badge/api%20docs-praveenshharma.github.io-blue.svg)](https://praveenshharma.github.io/tanstack-table-kmp/)
[![CI](https://github.com/praveenshharma/tanstack-table-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/praveenshharma/tanstack-table-kmp/actions/workflows/ci.yml)

A headless table engine for Kotlin Multiplatform with a Compose Multiplatform
adapter — the data, sorting, filtering, grouping, pagination, and selection
logic of TanStack Table v8, rendered through your Compose UI on Android and
iOS from a single shared source set.

## Demo

<p align="center">
  <img src="docs/media/sample-android.gif" alt="tanstack-table-kmp sample running on Android: sorting, filtering, pagination, grouping, row selection" width="270">
</p>

<p align="center"><sub>The <code>:sample</code> app on Android, 2× playback speed. <a href="docs/media/sample-android.mp4">Full-quality MP4 →</a> &nbsp;·&nbsp; iOS recording coming soon (same Compose Multiplatform code).</sub></p>

```kotlin
val table = rememberTable(
    TableOptions(
        data = people,
        columns = columns,
        getCoreRowModel = getCoreRowModel(),
        getSortedRowModel = getSortedRowModel(),
    ),
)

TableGrid(Modifier.horizontalScroll(rememberScrollState())) {
    table.getHeaderGroups().forEach { hg ->
        row {
            hg.headers.forEach { h ->
                cell { TableCellText(flexRender(h.column.columnDef.header, h.getContext()), bold = true) }
            }
        }
    }
    table.getRowModel().rows.forEach { r ->
        row {
            r.getVisibleCells().forEach { c ->
                cell { TableCellText(flexRender(c.column.columnDef.cell, c.getContext())) }
            }
        }
    }
}
```

The engine is design-free — what you render is up to you. `TableGrid` is the
adapter's drop-in `SubcomposeLayout` for the common case (content-sized
columns, optional column-span cells, IME-safe nested text fields); skip it and
draw cells yourself if you need a different layout.

## Why

TanStack Table is the de-facto headless table engine on the web. There was no
equivalent for Kotlin Multiplatform / Compose Multiplatform: existing Compose
table libraries bake their own opinion into the engine and rendering, leaving
you to either accept their choices or rebuild from scratch. This project
ports the engine 1:1 from TanStack's TypeScript source, layers a Compose
Multiplatform adapter on top, and ships them as separate Gradle modules so
you take only what you need.

## Supported targets

| Module          | Targets                                                |
| --------------- | ------------------------------------------------------ |
| `:table-core`   | `commonMain` (JVM 11, Android, `iosX64`, `iosArm64`, `iosSimulatorArm64`) |
| `:table-compose`| `commonMain` (JVM, Android, all three iOS targets)     |

Compose Multiplatform 1.7.x. Kotlin 2.x. Android `minSdk` 24, `compileSdk` 35.

### Version compatibility

0.1.0 is built with **Kotlin 2.1.0** and **Compose Multiplatform 1.7.3**.

- **Android / JVM** consumers: tolerant of differing Kotlin versions (JVM
  bytecode is stable), so most setups just work.
- **iOS / Kotlin Native** consumers: the published `.klib`s are tied to the
  Kotlin compiler version. For the iOS targets, keep your project's Kotlin
  version aligned with **2.1.x** — a significantly different Kotlin version can
  fail to link against the native artifacts. This is a general Kotlin
  Multiplatform constraint, not specific to this library.

## Modules

- **`:table-core`** — the engine. No UI dependencies. Mirrors the TanStack
  Table v8 API: `Table`, `ColumnDef`, `createColumnHelper`, `getCoreRowModel`,
  `getSortedRowModel`, `getFilteredRowModel`, `getGroupedRowModel`,
  `getExpandedRowModel`, `getPaginationRowModel`, `getFacetedRowModel`, and
  the column / row / cell feature mixins (visibility, ordering, pinning,
  sizing, filtering, sorting, grouping, expansion, pagination, selection).
- **`:table-compose`** — the Compose Multiplatform adapter. Provides
  `rememberTable` (subscribes the composition to the engine's state),
  `flexRender` (resolves `cell` / `header` / `footer` templates), and
  `TableGrid` (a theme-neutral `SubcomposeLayout` grid).
- **`:sample`** — a Compose Multiplatform demo app covering 13 feature
  screens. Runs on Android and iOS from one common source set.

## Install

Available on Maven Central. Add `mavenCentral()` to your repositories, then the
dependencies to your shared module:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// build.gradle.kts (your KMP module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            // The Compose Multiplatform adapter. Pulls in table-core transitively
            // (it api-depends on the engine), so this one line is enough for most apps.
            implementation("io.github.praveenshharma:table-compose:0.1.0")

            // Or depend on just the headless engine — no Compose, no UI:
            // implementation("io.github.praveenshharma:table-core:0.1.0")
        }
    }
}
```

## Quick start

The API mirrors TanStack Table v8 — the [TanStack Table docs](https://tanstack.com/table/v8)
remain the canonical reference for engine concepts (row models, column
definitions, state). The differences live in Kotlin's syntax and a handful of
type-system adaptations documented in [`docs/INTERNALS.md`](docs/INTERNALS.md).

Kotlin API reference (generated by Dokka, regenerated on every push to `main`):
**<https://praveenshharma.github.io/tanstack-table-kmp/>**.

A complete walkthrough for each feature lives in the `:sample` app — open the
example screen for the feature you want and follow the column definitions
through the `rememberTable` call into the `TableGrid` builder.

## Compose adapter pattern

Cell composables that take a single stable parameter (a `Row`, `Column`,
`TableColumn`, or `Header`) are skipped by Compose's stability-inference
restart logic when the surrounding table state changes but the parameter
reference does not. Annotate your cell composables `@NonRestartableComposable`
to opt out of the skip and re-run them on every parent recomposition. The
adapter's own cell composables (`TableGrid` and friends) already do this;
your own cell composables need the annotation if they read engine state.

```kotlin
@Composable
@NonRestartableComposable
fun StatusCell(row: Row<Person>) {
    val isSelected = row.getIsSelected()
    // ...
}
```

Background on why this is necessary lives in
[`docs/INTERNALS.md`](docs/INTERNALS.md) under "Compose adapter".

## Examples

Each of the 13 example screens in `:sample` is self-contained: open the file,
follow the column definitions, the `rememberTable` call, and the `TableGrid`
builder. The sample app runs on Android (`./gradlew :sample:installDebug`)
and iOS (see [`docs/INTERNALS.md`](docs/INTERNALS.md) for the simulator
setup).

| Screen                          | Demonstrates                                                  |
| ------------------------------- | ------------------------------------------------------------- |
| Basic                           | Core column rendering, footers                                |
| Sorting                         | Single + multi-column sort, custom sort fns                   |
| Filters                         | Per-column text filtering                                     |
| Filters (Faceted)               | Faceted unique-value / min-max filtering                      |
| Pagination                      | Page size, page navigation, manual vs auto                    |
| Grouping                        | Group rows by column, aggregation, leaf-row counts            |
| Expanding                       | Sub-row expansion, row-level expand state                     |
| Row Selection                   | Single / multi-row selection, indeterminate header state      |
| Row Pinning                     | Pin rows to the top or bottom                                 |
| Column Pinning                  | Pin columns to left / right                                   |
| Column Ordering                 | Reorder columns at runtime                                    |
| Column Visibility               | Show / hide individual columns                                |
| Column Groups                   | Nested header groups (multi-row headers)                      |

## Project layout

```
table-core/         — headless engine (Kotlin Multiplatform)
table-compose/      — Compose Multiplatform adapter
sample/             — demo app (Android + iOS)
iosApp/             — minimal Swift host for the iOS simulator demo
docs/INTERNALS.md   — design notes and implementation invariants
```

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). PRs are welcome; bug reports and
reproduction projects in particular. For substantial changes please open an
issue first to discuss direction.

## License

[MIT](LICENSE). This project is a community-maintained Kotlin Multiplatform
port of [TanStack Table](https://github.com/TanStack/table) (also MIT,
© Tanner Linsley); see [`NOTICE`](NOTICE) for attribution. It is not
affiliated with, endorsed by, or sponsored by TanStack.
