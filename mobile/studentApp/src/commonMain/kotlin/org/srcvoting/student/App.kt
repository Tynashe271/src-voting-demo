package org.srcvoting.student

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val SrcGreen = Color(0xFF173C32)
private val SrcGreen2 = Color(0xFF285746)
private val SrcGold = Color(0xFFD6A952)

private val SrcColorScheme = lightColorScheme(
    primary = SrcGreen,
    secondary = SrcGold,
    tertiary = SrcGreen2,
)

@Composable
fun StudentApp() {
    val viewModel = remember { StudentViewModel() }
    val state by viewModel.state.collectAsState()

    MaterialTheme(colorScheme = SrcColorScheme) {
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
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
