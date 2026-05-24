# v0.1.0 — Initial release

The first release of `tanstack-table-kmp`: a Kotlin Multiplatform port of
[TanStack Table v8.21.3](https://tanstack.com/table/v8) with a Compose
Multiplatform adapter.

## What's in the box

- **`:tanstack-table-core`** — headless engine. Targets `commonMain` /
  Android / JVM 11 / iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`). Mirrors
  the upstream TanStack Table v8 API: `Table`, `ColumnDef`,
  `createColumnHelper`, the row-model factories (`getCoreRowModel`,
  `getSortedRowModel`, `getFilteredRowModel`, `getGroupedRowModel`,
  `getExpandedRowModel`, `getPaginationRowModel`, `getFacetedRowModel`), and
  the full feature surface — column visibility, ordering, pinning, sizing;
  column + global filtering (faceted); sorting; grouping; expanding;
  pagination; row pinning; row selection.
- **`:tanstack-table-compose`** — Compose Multiplatform adapter. Provides
  `rememberTable` (subscribes the composition to engine state), `flexRender`
  (resolves cell / header / footer templates), and `TableGrid` — a
  theme-neutral `SubcomposeLayout` grid that handles content-sized columns,
  column-span cells, and IME-safe nested text fields out of the box.
- **`:sample`** — Compose Multiplatform demo app for Android and iOS,
  covering 13 feature screens.

## Targets

| Target          | Status |
| --------------- | :----: |
| Android (`minSdk 24`, `compileSdk 35`) | ✅ |
| JVM 11          | ✅ |
| `iosArm64`      | ✅ |
| `iosX64`        | ✅ |
| `iosSimulatorArm64` | ✅ |

Compose Multiplatform 1.7.x, Kotlin 2.1.x.

## Notes for early adopters

- API mirrors TanStack Table v8 — the
  [upstream docs](https://tanstack.com/table/v8) remain the canonical
  reference for concepts. Differences live in Kotlin syntax and a handful
  of type-system adaptations documented in
  [`docs/INTERNALS.md`](../docs/INTERNALS.md).
- The Compose adapter pattern that you'll likely hit first:
  `@NonRestartableComposable` on any cell composable that takes a single
  stable engine parameter (`Row`, `Column`, `Header`). The library's own
  composables (`TableGrid`) already do this. See the README's
  "Compose adapter pattern" section for the why.
- 0.x means the API may still evolve. The engine API is stable (it mirrors
  upstream); the Compose adapter API may grow as feedback comes in.

## Acknowledgement

This is a community port. TanStack Table is © Tanner Linsley and the
TanStack contributors, MIT-licensed; full attribution lives in
[`NOTICE`](../NOTICE). Not affiliated with TanStack.
