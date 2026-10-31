package org.srcvoting.admin

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

// Matches the Blade admin UI's Tailwind slate theme exactly -- staff who use both surfaces should
// recognise this as the same product.
val AdminSlate900 = Color(0xFF0F172A)
val AdminSlate700 = Color(0xFF334155)
val AdminSlate100 = Color(0xFFF1F5F9)
val AdminEmerald = Color(0xFF047857)
val AdminAmber = Color(0xFFB45309)
val AdminRed = Color(0xFFB91C1C)

private val AdminColorScheme = lightColorScheme(
    primary = AdminSlate900,
    onPrimary = Color.White,
    secondary = AdminSlate700,
    background = Color.White,
    surface = Color.White,
)

@Composable
fun AdminApp() {
    val viewModel = remember { AdminViewModel() }
    val state by viewModel.state.collectAsState()

    MaterialTheme(colorScheme = AdminColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when (val screen = state.screen) {
                    AdminScreen.Login -> AdminLoginScreen(state, viewModel)
                    is AdminScreen.MfaCode -> AdminMfaCodeScreen(screen.email, screen.password, state, viewModel)
                    AdminScreen.MfaSetup -> AdminMfaSetupScreen(state, viewModel)
                    AdminScreen.Elections -> AdminElectionsScreen(state, viewModel)
                    is AdminScreen.ElectionDetail -> AdminElectionDetailScreen(screen.electionId, state, viewModel)
                    AdminScreen.Students -> AdminStudentsScreen(state, viewModel)
                    AdminScreen.Audit -> AdminAuditScreen(state, viewModel)
                }
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AdminSlate900)
                    }
                }
            }
        }
    }
}
