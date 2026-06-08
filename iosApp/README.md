# iosApp

The iOS host for the `:sample` demo — a standard Compose Multiplatform iOS app.
It is a thin SwiftUI shell around the shared Compose UI:

- `iosApp/iOSApp.swift` — SwiftUI `@main` entry point.
- `iosApp/ContentView.swift` — wraps the Kotlin `MainViewController()` (exported
  by `SampleApp.framework`) in a `UIViewControllerRepresentable`.
- `iosApp/Info.plist` — generated from `project.yml`; includes the
  `CADisableMinimumFrameDurationOnPhone` key the Compose Multiplatform iOS
  runtime requires at launch.

A pre-build phase runs Gradle's `embedAndSignAppleFrameworkForXcode` task, which
builds, embeds, and signs `SampleApp.framework` for whichever SDK / architecture
Xcode is targeting. There is no CocoaPods or SPM step — just Gradle + Xcode.

## Run it

In Xcode: open `iosApp.xcodeproj`, select the `iosApp` scheme and a simulator,
and Run.

From the command line, onto a booted simulator:

```bash
# from the repository root
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'id=<BOOTED_SIM_UDID>' \
  -configuration Debug -derivedDataPath iosApp/build/dd build
xcrun simctl install booted iosApp/build/dd/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted io.github.tanstacktable.sample
```

(`xcrun simctl list devices booted` shows the booted simulator's UDID.)

## Project generation

`iosApp.xcodeproj` is generated from [`project.yml`](project.yml) with
[XcodeGen](https://github.com/yonaskolb/XcodeGen). Both files are committed, so
the project opens with no extra tooling. If you change `project.yml`, regenerate:

```bash
brew install xcodegen   # once
cd iosApp && xcodegen generate
```
