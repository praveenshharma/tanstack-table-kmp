# Contributing

Thanks for your interest in improving `tanstack-table-kmp`. This document
covers how to build, test, and submit changes.

## Ground rules

- **Be kind.** This project follows the [Contributor Covenant Code of
  Conduct](CODE_OF_CONDUCT.md). Be respectful in issues, PRs, and
  discussions.
- **The engine mirrors TanStack Table v8.** When changing `:table-core`,
  keep the public API in line with the upstream TanStack Table v8 surface
  unless there's a documented type-system reason to diverge. Divergences
  belong in [`docs/INTERNALS.md`](docs/INTERNALS.md) under "Implementation
  invariants" with a brief rationale.
- **The Compose adapter (`:table-compose`) is ours.** TanStack Table ships no
  Compose adapter; this module is original work. API changes here are
  weighed on their own merits.
- **Theme-neutrality.** `:table-compose` depends only on Compose
  `runtime` / `foundation` / `ui`. No Material, no Cupertino, no opinion on
  typography or color. New code in `:table-compose` must keep this property.

## Setup

Prerequisites:

- JDK 17+
- Android SDK (compileSdk 34, minSdk 24)
- Xcode 15+ (only if you want to run the iOS sample)

Clone and build:

```bash
git clone https://github.com/praveenshharma/tanstack-table-kmp.git
cd tanstack-table-kmp
./gradlew build
```

## Running tests

```bash
# Unit tests for the engine (JVM + iOS sim runtime)
./gradlew :table-core:allTests

# Compile-check every target (no test runtime)
./gradlew \
  :table-core:compileKotlinIosSimulatorArm64 \
  :table-core:compileDebugKotlinAndroid \
  :table-compose:compileKotlinIosSimulatorArm64 \
  :table-compose:compileDebugKotlinAndroid
```

## Running the sample app

**Android:**
```bash
./gradlew :sample:installDebug
adb shell am start -n io.github.tanstacktable.sample/.MainActivity
```

**iOS (simulator):** see the iOS section of
[`docs/INTERNALS.md`](docs/INTERNALS.md#how-to-verify-changes).

## Pull-request checklist

Before opening a PR:

- [ ] `./gradlew build` succeeds locally.
- [ ] If you changed `:table-core` engine behaviour, you added or updated a
      test in `table-core/src/commonTest/`.
- [ ] If your change is user-visible, you added a `CHANGELOG.md` entry under
      `## [Unreleased]`.
- [ ] For `:table-core` changes that diverge from TanStack Table v8, you
      either flagged this in [`docs/INTERNALS.md`](docs/INTERNALS.md) or in
      your PR description so we can discuss before merging.

## Reporting bugs

Use the bug-report issue template. The most helpful reports include:

- The smallest example that reproduces the issue (a Gradle module or a
  short Compose snippet).
- Expected vs actual behaviour.
- Target platform (Android, iOS, JVM) and Compose Multiplatform version.
- Stack trace, if any.

## Releasing

Maintainer-facing — see the "Maintenance" section of
[`docs/INTERNALS.md`](docs/INTERNALS.md).
