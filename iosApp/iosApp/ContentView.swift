import SwiftUI
import UIKit
import SampleApp

// Bridges the Kotlin/Compose UI into SwiftUI. `MainViewControllerKt.MainViewController()`
// is the `fun MainViewController(): UIViewController` exported by the SampleApp
// framework (sample/src/iosMain/.../MainViewController.kt).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose Multiplatform manages its own window insets (status bar via
        // TopAppBar / statusBarsPadding), so let it draw edge-to-edge.
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
