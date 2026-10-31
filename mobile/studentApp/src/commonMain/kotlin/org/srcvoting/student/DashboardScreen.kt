package org.srcvoting.student

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier.padding(end = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SrcPaper),
        border = androidx.compose.foundation.BorderStroke(0.dp, SrcPaper),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = SrcGold, modifier = Modifier.padding(bottom = 6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = SrcGreen)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DashboardScreen(state: StudentUiState, viewModel: StudentViewModel) {
    val profile = state.profile
    val election = state.dashboard?.election

    ScreenScaffold(
        title = "Hello, ${profile?.first_name ?: "student"}",
        subtitle = "Your election information and voting status.",
        state = state,
        viewModel = viewModel,
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            StatCard(Icons.Filled.Badge, profile?.registration_number ?: "", "Registration number", Modifier.weight(1f))
            StatCard(Icons.Filled.School, profile?.programme ?: "Student", "Programme", Modifier.weight(1f))
        }

        if (election == null) {
            InfoCard { Text("No election is scheduled at this time.") }
        } else {
            InfoCard {
                Text("CURRENT ELECTION", style = MaterialTheme.typography.labelMedium, color = SrcGold)
                Text(election.name, style = MaterialTheme.typography.titleLarge.copy(fontFamily = SrcSerif))
                Spacer(Modifier.height(6.dp))
                StatusPill(election.status ?: "")
                Spacer(Modifier.height(10.dp))
                Row {
                    Icon(if (election.has_voted) Icons.Filled.CheckCircle else Icons.Filled.HourglassEmpty, contentDescription = null, tint = if (election.has_voted) SrcGreen else SrcGold)
                    androidx.compose.foundation.layout.Column(Modifier.padding(start = 8.dp)) {
                        Text(if (election.has_voted) "Submitted" else "Not yet voted", style = MaterialTheme.typography.titleMedium)
                        Text("Your voting status", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Voting closes ${election.closes_at ?: ""}", modifier = Modifier.padding(top = 10.dp))

                if (election.status == "OPEN" && !election.has_voted) {
                    PrimaryButton("Vote now") { viewModel.navigate(Screen.Ballot(election.id)) }
                }
                if (election.has_voted) {
                    SecondaryButton("View receipt") { viewModel.navigate(Screen.Receipt(election.id)) }
                }
                if (state.dashboard?.results_available == true) {
                    SecondaryButton("View official results") { viewModel.navigate(Screen.Results(election.id)) }
                }
            }
        }

        InfoCard {
            Text("Before you vote", style = MaterialTheme.typography.titleMedium, color = SrcGreen)
            Text("Review candidates, choose once for each position, check your selections, then confirm submission. A submitted ballot cannot be changed.")
            if (election != null) {
                SecondaryButton("Browse candidates") { viewModel.navigate(Screen.Candidates(election.id)) }
            }
        }
    }
}
