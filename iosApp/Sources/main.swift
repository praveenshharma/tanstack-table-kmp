import UIKit
import SampleApp

// Minimal iOS host for the Compose Multiplatform UI. Does what an Xcode
// project's `@UIApplicationMain` AppDelegate would do, but as a single Swift
// file compiled with `swiftc` so this sample can run on a simulator without
// an Xcode project.
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        // `MainViewControllerKt.MainViewController()` is the Kotlin
        // `fun MainViewController(): UIViewController` exposed by the
        // SampleApp framework (sample/src/iosMain/.../MainViewController.kt).
        window.rootViewController = MainViewControllerKt.MainViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}

UIApplicationMain(
    CommandLine.argc,
    CommandLine.unsafeArgv,
    nil,
    NSStringFromClass(AppDelegate.self)
)
