# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] — 2026-05-24

Initial public release. Published to Maven Central on 2026-06-03 as
`io.github.praveenshharma:table-core:0.1.0` and
`io.github.praveenshharma:table-compose:0.1.0`.

### Added
- `:table-core` — Kotlin Multiplatform headless table engine. Mirrors the
  TanStack Table v8.21.3 API surface across `commonMain`, with Android (JVM 11)
  and Kotlin/Native iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
  Features: column visibility, ordering, pinning, sizing, filtering (column +
  global, faceted), sorting, grouping, expanding, pagination, row pinning,
  row selection.
- `:table-compose` — Compose Multiplatform adapter. Exposes `rememberTable`
  (state-driven `Table` factory), `flexRender` (template renderer), and
  `TableGrid` (a theme-neutral `SubcomposeLayout` grid that handles
  content-sized columns, column-span cells, and IME-safe text input inside
  cells).
- `:sample` — Compose Multiplatform demo app for Android + iOS covering 13
  feature screens (Basic, Sorting, Filtering, Faceted Filters, Pagination,
  Grouping, Expanding, Row Selection, Row Pinning, Column Pinning, Column
  Ordering, Column Visibility, Column Groups).
- `docs/INTERNALS.md` — maintainer-facing design notes (engine model,
  implementation invariants, the `@NonRestartableComposable` rationale for
  Compose cell composables).

[Unreleased]: https://github.com/praveenshharma/tanstack-table-kmp/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/praveenshharma/tanstack-table-kmp/releases/tag/v0.1.0
