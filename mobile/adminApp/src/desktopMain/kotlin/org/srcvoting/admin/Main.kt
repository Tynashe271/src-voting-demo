package org.srcvoting.admin

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.PlatformContext
import org.srcvoting.core.initImageLoader

fun main() = application {
    initImageLoader(PlatformContext.INSTANCE)
    val windowState = rememberWindowState(size = DpSize(1100.dp, 780.dp))
    Window(onCloseRequest = ::exitApplication, title = "SRC Elections Admin", state = windowState) {
        AdminApp()
    }
}
