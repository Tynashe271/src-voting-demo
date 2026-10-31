package org.srcvoting.student

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResultsScreen(state: StudentUiState, viewModel: StudentViewModel) {
    val results = state.results
    ScreenScaffold(
        title = "Official sealed results",
        subtitle = "These totals are read-only and are shared with the election administration portal.",
        state = state,
        viewModel = viewModel,
    ) {
        InfoCard {
            Text(results?.status ?: "", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            results?.totals.orEmpty().groupBy { it.position }.forEach { (positionName, rows) ->
                Text(positionName, style = MaterialTheme.typography.titleSmall)
                rows.sortedByDescending { it.votes }.forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(row.candidate, modifier = Modifier.weight(1f))
                        Text("${row.votes}")
                    }
                }
                Divider(Modifier.padding(vertical = 8.dp))
            }
            Text("Integrity hash: ${results?.integrity_hash ?: ""}", style = MaterialTheme.typography.bodySmall)
            Text("Public results close ${results?.visible_until ?: ""}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun NotificationsScreen(state: StudentUiState, viewModel: StudentViewModel) {
    ScreenScaffold(
        title = "Notifications",
        subtitle = "Election updates and confirmation messages.",
        state = state,
        viewModel = viewModel,
    ) {
        if (state.notifications.isEmpty()) {
            InfoCard { Text("You have no notifications.") }
        }
        state.notifications.forEach { n ->
            InfoCard {
                Text(n.type, style = MaterialTheme.typography.titleSmall)
                Text(n.message)
                Text(n.created_at, style = MaterialTheme.typography.bodySmall)
                if (n.read_at == null) {
                    SecondaryButton("Mark as read") { viewModel.markNotificationRead(n.id) }
                }
            }
        }
    }
}
