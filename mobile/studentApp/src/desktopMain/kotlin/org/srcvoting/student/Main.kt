package org.srcvoting.student

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.PlatformContext
import org.srcvoting.core.initImageLoader

fun main() = application {
    initImageLoader(PlatformContext.INSTANCE)
    val windowState = rememberWindowState(size = DpSize(960.dp, 720.dp))
    Window(onCloseRequest = ::exitApplication, title = "SRC Student Voting", state = windowState) {
        StudentApp()
    }
}
