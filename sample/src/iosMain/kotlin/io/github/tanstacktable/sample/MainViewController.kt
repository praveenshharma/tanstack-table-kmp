package io.github.tanstacktable.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS entry point, exposed to Swift as `MainViewControllerKt.MainViewController()`.
 * Wraps [SampleApp] in `MaterialTheme` / `Surface` and returns a
 * `UIViewController` the host app can mount, matching what [MainActivity] does
 * on Android.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    MaterialTheme {
        Surface {
            SampleApp()
        }
    }
}
