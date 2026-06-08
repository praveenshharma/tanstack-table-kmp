# Internals

Design notes and implementation rationale for the engine + Compose adapter.
Reading this is not required to use the library — it's for maintainers and
contributors who want to understand non-obvious decisions.

---

## Module layout

| Module | Role | Source set |
|---|---|---|
| `:table-core` | Headless table engine — pure logic, no UI, no Compose | 100% `commonMain` |
| `:table-compose` | Compose Multiplatform adapter — binds engine state to Compose | `commonMain` (CMP) |
| `:sample` | Demo app, exercises the adapter on a real device | Android + iOS |

**Targets** (`:table-core/build.gradle.kts`): `jvm`, `androidTarget`, `iosX64`,
`iosArm64`, `iosSimulatorArm64`. The engine lives entirely in `commonMain` and
uses zero platform APIs — keeping every target on at all times prevents
accidental JVM-only (`java.*`) usage that would silently break iOS.

The engine deliberately knows nothing about Compose. The adapter is the only
module that depends on `org.jetbrains.compose.*`. Anyone wanting a different
UI binding (XML views, SwiftUI, etc.) can sit on top of `:table-core` alone.

---

## Engine model

### Class-per-entity, members assigned by features

Each engine entity — `Table`, `Column`, `Row`, `Cell`, `Header` — is a single
mutable class declaring every member contributed by every feature. The core
constructor wires the universally-present members; each feature's
`createTable(table)` then assigns its own members onto the same instance.
Function-valued members are `lateinit var` (or nullable) because they close
over the surrounding `table` and must be installed after construction.

Consequence: these classes are authored complete, in one file each, and
**cannot be extended feature-by-feature** the way TypeScript declaration
merging allows. Adding a new feature means editing the entity class to declare
the new members, then writing the feature's `createTable` to assign them. See
*Maintenance → Adding a new column feature* below.

### Updater contract

`Updater` is represented as `Any?` and resolved at runtime by `functionalUpdate`:

- If the value `is Function<*>`, it's invoked with the previous state.
- Otherwise it's treated as the next state directly.

This avoids needing union types or a typed alias that would force every state
setter to know its `T`. Public adapter APIs may add typed overloads for
ergonomics, but the core plumbing stays `Any?`.

Edge case: any function arity satisfies `is Function<*>`. The engine only
passes 1-arg updaters; a misused 0-arg or multi-arg function would be cast at
the call site and fail there. Keep this in mind when adding new setters.

### Memoiser

The memo helper carries dependencies as `List<Any?>` and passes the list (not
a spread) to the compute function. Two flavours:

- `memo<TResult>` — no-arg, returns `() -> TResult`.
- `memoWithArg<TArg, TResult>` — returns `(TArg) -> TResult`, used where the
  computation legitimately takes a positional argument (e.g.
  `Column.getIndex(position)`, `Column.getStart(position)`).

`getMemoOptions<TResult>` is generic so call sites do not need `as` casts.
Dependency comparison is **reference inequality** (`!==`-equivalent): the
deps are the live state arrays/objects, not their contents.

When writing a new memoised getter, choose the right flavour and remember
that returning a different list of the *same* contents will invalidate the
memo. That's intentional — it tracks identity through the state-update graph.

### Default-merge for column definitions

`createColumn` merges the engine's `defaultColumn` with the caller's
`columnDef` using a **present-key-with-non-null-value-wins** rule, not a
naive shallow spread. A `columnDef` that simply does not specify `header` or
`cell` must inherit the engine default; a naive merge would overwrite the
default with the user's absent (`null`) field.

If you ever refactor this merge, exercise the "Basic" sample with a column
that specifies neither `header` nor `cell` — that's the regression case.

---

## Implementation invariants

These are properties the engine and call sites rely on. Breaking them produces
quiet, hard-to-find bugs, not compile errors.

### Value equality for primitives

Comparisons of `Int`, `String`, and other primitive-like values use Kotlin
`==` (value equality). `===` is reserved for reference identity, and is
appropriate only when the engine genuinely needs object identity (e.g.
`column === otherColumn`).

This matters in `CoreHeaders` (depth and id comparisons) and throughout the
filter / sort built-ins.

### Filter / sort / aggregation built-ins compare by VALUE, not REFERENCE

`filterFns.equals` and `filterFns.weakEquals` implement JS strict / loose
equality semantics over primitive operands via `jsStrictEquals` /
`jsLooseEquals` shims. Do not "simplify" them to plain Kotlin `==` / `===` —
both have been wrong here before.

Residual gap: JS `==` performs `ToPrimitive` coercion when one operand is an
object. That coercion is not portable to `commonMain` and is **not**
implemented. Object-on-object loose equality falls back to reference
identity. Filters in practice operate on primitive cell values, so this has
not been an issue, but it's a known cliff.

### ASCII-only digit scanning

`jsParseFloat` / `jsParseInt` and the alphanumeric sort scan digits using
the literal range `'0'..'9'`, **not** `Char.isDigit()`. `Char.isDigit()`
accepts non-ASCII Unicode digits (Arabic-Indic, Devanagari, full-width…)
which would silently change sort and filter behaviour for international
data. Match JS `parseFloat`/`parseInt` semantics by sticking to ASCII.

### Regex split preserves capturing groups

`SortingFns.compareAlphanumeric` uses `splitKeepingDelimiters` because
Kotlin's `Regex.split` drops capturing-group content, whereas JS
`"…".split(/([0-9]+)/)` interleaves the captures back into the result.
The alphanumeric sort depends on getting the numeric chunks back to compare
them numerically — without the shim, every numeric segment vanishes and
numbers sort as strings.

### Slicing tolerates negative indices

JS `array.slice(0, -1)` is well-defined (returns the array minus the last
element); Kotlin `List.take(-1)` throws. Anywhere the engine takes a slice
whose endpoint can legitimately be `-1` — notably `column.getStart` when a
column is hidden and `getIndex` returns `-1` — the code emulates JS `slice`
semantics rather than calling `take` directly.

### Stringification of null/undefined

In a couple of filter built-ins (`includesString`, `includesStringSensitive`)
a nullish argument must stringify to `"undefined"`, not `"null"` and not the
empty string. The engine uses an explicit nullish-coalesce-to-`"undefined"`
helper at those sites.

### Case-sensitive class file names

Kotlin compiles each top-level class into `<Name>.class`. On a
case-insensitive filesystem (macOS HFS+/APFS default, Windows NTFS default)
two classes whose names differ only by case collide — one silently clobbers
the other and the loser surfaces as `NoClassDefFoundError` at runtime, not
at build time.

The engine therefore avoids case-only-distinct top-level names. Specifically:
the empty registry marker interfaces are `FilterFnsRegistry`,
`SortingFnsRegistry`, `AggregationFnsRegistry` (not `FilterFns` / `filterFns`
pairs). When introducing new types, do not create a sibling that differs only
by case.

---

## Compose adapter

### `@NonRestartableComposable` on cell composables

Cell-level composables in the adapter are annotated `@NonRestartableComposable`
because they are called many times per frame inside a single LazyList /
LazyRow row and have no observable state of their own — their entire output
is a function of their parameters. Making them restartable would add a
recompose scope per cell and an indirection layer with no benefit, since
they would never independently recompose anyway.

Rule of thumb when adding new adapter composables:

- Composables that **render a leaf** (a cell, an icon, a divider) and do not
  read any external state: mark `@NonRestartableComposable`.
- Composables that **own state**, read a `State<T>`, or are the recomposition
  boundary for a subtree: leave restartable (the default).

If unsure, leave it restartable; the annotation is an optimisation, not a
correctness requirement.

### Engine state is read once per frame, not subscribed

The adapter pulls engine state from the table's getters and lets the
surrounding Compose state (the row model, the column visibility map, etc.)
drive recomposition. The engine itself is not Compose-aware — it does not
emit `State<T>`. Wrap engine output in `remember(...) { ... }` against the
right keys when you need to cache derived values across recompositions.

---

## How to verify changes

The engine is covered by a `commonTest` suite (32 cases, 3 honestly skipped)
mirroring upstream `table-core` and the engine-relevant `react-table` cases.
Run it on every target you touch:

```bash
./gradlew :table-core:allTests
```

The skipped tests are:

- Two `utils/document` cases that require a real DOM — not portable to
  `commonTest`.
- One `react-table` `has a stable api` case that asserts React-hook
  referential stability of `useReactTable` — an adapter concern with no
  engine equivalent.

When changing filter, sort, aggregation, or grouping behaviour, also run the
`:sample` app and walk through Basic, Column Groups, Sorting, Filters, and
Expanding — these are the screens that have caught real engine bugs (the
case-only-collision and the default-merge clobber) before tests did.

---

## Maintenance

### Running tests

```bash
./gradlew :table-core:allTests           # engine, all targets
./gradlew :table-core:jvmTest            # fastest iteration
./gradlew :table-core:iosSimulatorArm64Test
./gradlew :table-compose:allTests        # adapter
```

### Verifying on Android

```bash
./gradlew :sample:installDebug
```

Walk the screens listed above. The Sorting and Expanding screens are
interactive — tap a sortable header, expand a sub-row — and exercise the
state-update path end-to-end.

### Verifying on iOS

The `iosApp/` host is a standard Compose Multiplatform iOS app: a SwiftUI
entry point (`iOSApp.swift`) wrapping the shared Compose UI through a
`UIViewControllerRepresentable` (`ContentView.swift`), with a pre-build phase
that runs the Kotlin Multiplatform `embedAndSignAppleFrameworkForXcode` task to
build, embed, and sign `SampleApp.framework`.

Open `iosApp/iosApp.xcodeproj` in Xcode, pick the `iosApp` scheme and a
simulator, and Run. Or from the command line, build + install + launch on a
booted simulator:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'id=<BOOTED_SIM_UDID>' \
  -configuration Debug -derivedDataPath iosApp/build/dd build
xcrun simctl install booted iosApp/build/dd/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted io.github.tanstacktable.sample
```

The Xcode project is generated from `iosApp/project.yml` via
[xcodegen](https://github.com/yonaskolb/XcodeGen) (`cd iosApp && xcodegen
generate`); both the spec and the generated `.xcodeproj` are committed, so the
project opens in Xcode with no extra tooling — regenerate only after editing
`project.yml`.

For a quick command-line check that just the iOS *targets compile* (no app):

```bash
./gradlew :table-core:compileKotlinIosSimulatorArm64
./gradlew :table-compose:compileKotlinIosSimulatorArm64
```

### Adding a new column feature

1. Declare the new members on `Column` (and `Table` / `Header` / `Row` /
   `Cell` as needed). Function-valued members are `lateinit var`; data
   members get sensible defaults.
2. Add the corresponding state slice to `TableState` and the option slice to
   `TableOptionsResolved`, with merge / empty handling.
3. Create `features/<Name>.kt` with a `createTable(table)` that assigns the
   feature's members onto the entity instances.
4. Wire `getInitialState` and the options defaults from your feature into
   the core `createTable` initialisation sequence.
5. Add a `commonTest` case that exercises the new behaviour and a `:sample`
   screen (or extend an existing one) that surfaces it visually.
6. Run the full test suite **and** the sample on Android and iOS — the
   sample has historically caught bugs the test suite did not.

### Adding a new built-in filter / sort / aggregation function

Add the function as a named `val` on the corresponding `object` (`filterFns`,
`sortingFns`, `aggregationFns`). The object exposes both named access and an
`operator fun get(key)` for string-keyed lookup — both must continue to work,
so do not switch any of these to a private constructor or a `Map`-only form.

Add a `commonTest` case covering the new function's primitive operands, any
null/undefined behaviour, and (for sorts) at least one tie-breaker case.
