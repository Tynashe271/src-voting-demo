package org.srcvoting.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
        if (showNav) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SRC Elections Admin", fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.signOut() }) { Text("Sign out") }
            }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TextButton(onClick = { viewModel.navigate(AdminScreen.Elections) }) { Text("Elections") }
                TextButton(onClick = { viewModel.navigate(AdminScreen.Students) }) { Text("Students") }
                TextButton(onClick = { viewModel.navigate(AdminScreen.Audit) }) { Text("Audit") }
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        if (subtitle != null) {
            Text(subtitle, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        }
        AdminMessageBanner(state)
        content()
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
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun AdminPrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(text) }
}

@Composable
fun AdminSecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(text) }
}
