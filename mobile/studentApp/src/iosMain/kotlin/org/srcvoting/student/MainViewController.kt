package org.srcvoting.student

import androidx.compose.ui.window.ComposeUIViewController
import coil3.PlatformContext
import org.srcvoting.core.initImageLoader

fun MainViewController() = ComposeUIViewController {
    initImageLoader(PlatformContext.INSTANCE)
    StudentApp()
}
