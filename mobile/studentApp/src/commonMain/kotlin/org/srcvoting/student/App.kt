package org.srcvoting.student

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// The web app's exact palette (student-site/styles.css custom properties) -- same green/gold
// identity on every surface a student sees, web or native.
val SrcGreen = Color(0xFF173C32)
val SrcGreen2 = Color(0xFF285746)
val SrcGold = Color(0xFFD6A952)
val SrcPaper = Color(0xFFF5F6F2)

// The web app sets headings in Georgia/serif; Compose can't fetch a system "Georgia" by name
// across platforms, but the generic serif family gives the same "ceremonial/official document"
// feel without bundling a font asset.
val SrcSerif = FontFamily.Serif

private val SrcColorScheme = lightColorScheme(
    primary = SrcGreen,
    onPrimary = Color.White,
    secondary = SrcGold,
    tertiary = SrcGreen2,
    background = SrcPaper,
    surface = Color.White,
)

private val SrcTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontFamily = SrcSerif, fontWeight = FontWeight.Medium),
        headlineLarge = base.headlineLarge.copy(fontFamily = SrcSerif, fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontFamily = SrcSerif),
    )
}

@Composable
fun StudentApp() {
    val viewModel = remember { StudentViewModel() }
    val state by viewModel.state.collectAsState()

    MaterialTheme(colorScheme = SrcColorScheme, typography = SrcTypography) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (val screen = state.screen) {
                    Screen.Login -> LoginScreen(state, viewModel)
                    Screen.ChangePassword -> ChangePasswordScreen(state, viewModel)
                    Screen.Dashboard -> DashboardScreen(state, viewModel)
                    is Screen.Candidates -> CandidatesScreen(screen.electionId, state, viewModel)
                    is Screen.Ballot -> BallotScreen(screen.electionId, state, viewModel)
                    is Screen.Review -> ReviewScreen(screen.electionId, screen.selections, state, viewModel)
                    is Screen.Receipt -> ReceiptScreen(state, viewModel)
                    is Screen.Results -> ResultsScreen(state, viewModel)
                    Screen.Notifications -> NotificationsScreen(state, viewModel)
                }
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SrcGreen)
                    }
                }
            }
        }
    }
}
