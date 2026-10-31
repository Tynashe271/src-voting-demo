@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.srcvoting.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
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

@Composable
fun AdminScaffold(
    title: String,
    subtitle: String? = null,
    state: AdminUiState,
    viewModel: AdminViewModel,
    showNav: Boolean = true,
    content: @Composable () -> Unit,
) {
    val canGoBack = showNav && state.screen is AdminScreen.ElectionDetail

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SRC Elections Admin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { viewModel.navigate(AdminScreen.Elections) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to elections", tint = Color.White)
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
                    containerColor = AdminSlate900,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            if (showNav) {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.screen == AdminScreen.Elections || state.screen is AdminScreen.ElectionDetail,
                        onClick = { viewModel.navigate(AdminScreen.Elections) },
                        icon = { Icon(Icons.Filled.HowToVote, contentDescription = null) },
                        label = { Text("Elections") },
                    )
                    NavigationBarItem(
                        selected = state.screen == AdminScreen.Students,
                        onClick = { viewModel.navigate(AdminScreen.Students) },
                        icon = { Icon(Icons.Filled.People, contentDescription = null) },
                        label = { Text("Students") },
                    )
                    NavigationBarItem(
                        selected = state.screen == AdminScreen.Audit,
                        onClick = { viewModel.navigate(AdminScreen.Audit) },
                        icon = { Icon(Icons.Filled.Fingerprint, contentDescription = null) },
                        label = { Text("Audit") },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium, color = AdminSlate900)
            if (subtitle != null) {
                Text(subtitle, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            }
            AdminMessageBanner(state)
            content()
        }
    }
}

@Composable
fun AdminMessageBanner(state: AdminUiState) {
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
fun AdminCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun AdminPrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(top = 8.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AdminSlate900)) { Text(text) }
}

@Composable
fun AdminSecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(text) }
}

/** Mirrors the Blade UI's exact approval_status pill colours (PENDING amber, APPROVED emerald, REJECTED red, DISQUALIFIED slate). */
@Composable
fun ApprovalChip(status: String?) {
    val (bg, fg) = when (status) {
        "APPROVED" -> Color(0xFFD1FAE5) to AdminEmerald
        "REJECTED" -> Color(0xFFFEE2E2) to AdminRed
        "DISQUALIFIED" -> AdminSlate900 to Color.White
        else -> Color(0xFFFEF3C7) to AdminAmber
    }
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(999.dp)) {
        Text(
            status ?: "PENDING",
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
