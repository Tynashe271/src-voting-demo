@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.srcvoting.student

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Four tabs, matching the student-site's own nav exactly (Dashboard/Candidates/Results/
 * Notifications) -- the web and native apps should feel like the same product, not two different
 * ones built by different teams.
 */
private enum class BottomTab(val label: String) { Dashboard("Dashboard"), Candidates("Candidates"), Results("Results"), Notifications("Alerts") }

@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    state: StudentUiState,
    viewModel: StudentViewModel,
    showNav: Boolean = true,
    content: @Composable () -> Unit,
) {
    val electionId = state.dashboard?.election?.id
    val currentTab = when (state.screen) {
        is Screen.Candidates -> BottomTab.Candidates
        is Screen.Results -> BottomTab.Results
        Screen.Notifications -> BottomTab.Notifications
        else -> BottomTab.Dashboard
    }

    val canGoBack = showNav && currentTab != BottomTab.Dashboard
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SRC Student Voting", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { viewModel.navigate(Screen.Dashboard) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to dashboard", tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (showNav) {
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SrcGreen,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            if (showNav) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == BottomTab.Dashboard,
                        onClick = { viewModel.navigate(Screen.Dashboard) },
                        icon = { Icon(Icons.Filled.HowToVote, contentDescription = null) },
                        label = { Text(BottomTab.Dashboard.label) },
                    )
                    NavigationBarItem(
                        selected = currentTab == BottomTab.Candidates,
                        onClick = { if (electionId != null) viewModel.navigate(Screen.Candidates(electionId)) },
                        icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                        label = { Text(BottomTab.Candidates.label) },
                    )
                    NavigationBarItem(
                        selected = currentTab == BottomTab.Results,
                        onClick = { if (electionId != null) viewModel.navigate(Screen.Results(electionId)) },
                        icon = { Icon(Icons.Filled.QueryStats, contentDescription = null) },
                        label = { Text(BottomTab.Results.label) },
                    )
                    NavigationBarItem(
                        selected = currentTab == BottomTab.Notifications,
                        onClick = { viewModel.navigate(Screen.Notifications) },
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                        label = { Text(BottomTab.Notifications.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("UNIVERSITY ELECTIONS", style = MaterialTheme.typography.labelMedium, color = SrcGold, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SrcSerif), fontWeight = FontWeight.Medium, color = SrcGreen)
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFF647268), modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
            }
            MessageBanner(state)
            content()
        }
    }
}

@Composable
fun MessageBanner(state: StudentUiState) {
    state.error?.let {
        Card(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFBEBEA)),
        ) { Text(it, color = Color(0xFF772B26), modifier = Modifier.padding(14.dp)) }
    }
    state.info?.let {
        Card(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F1E9)),
        ) { Text(it, color = Color(0xFF17482F), modifier = Modifier.padding(14.dp)) }
    }
}

@Composable
fun InfoCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(top = 8.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SrcGreen)) { Text(text) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(text) }
}

/** Mirrors the web app's .pill status badges -- same colour meaning, so a staff member or student who's seen one has already seen the other. */
@Composable
fun StatusPill(status: String) {
    val (bg, fg) = when (status) {
        "OPEN" -> Color(0xFFE8F0EA) to Color(0xFF21513D)
        "UPCOMING" -> Color(0xFFFFF6DF) to Color(0xFF6D4B10)
        "RESULTS_VISIBLE" -> Color(0xFFE3EEFD) to Color(0xFF1E4C8A)
        "CLOSED", "ARCHIVED" -> Color(0xFFEFEFEF) to Color(0xFF55606A)
        else -> Color(0xFFE8F0EA) to Color(0xFF21513D)
    }
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(999.dp)) {
        Text(status, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}
