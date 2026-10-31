package org.srcvoting.student

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        InfoCard {
            Text(profile?.registration_number ?: "", style = MaterialTheme.typography.titleMedium)
            Text("Registration number", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Text(profile?.programme ?: "Student", style = MaterialTheme.typography.titleMedium)
            Text("Programme", style = MaterialTheme.typography.bodySmall)
        }

        if (election == null) {
            InfoCard { Text("No election is scheduled at this time.") }
        } else {
            InfoCard {
                Row(Modifier.fillMaxWidth()) {
                    Text("Current election", style = MaterialTheme.typography.labelMedium)
                }
                Text(election.name, style = MaterialTheme.typography.titleLarge)
                AssistChip(onClick = {}, label = { Text(election.status ?: "") })
                Spacer(Modifier.height(6.dp))
                Text(if (election.has_voted) "Submitted" else "Not yet voted", style = MaterialTheme.typography.titleMedium)
                Text("Your voting status", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Voting closes ${election.closes_at ?: ""}")

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
            Text("Before you vote", style = MaterialTheme.typography.titleMedium)
            Text("Review candidates, choose once for each position, check your selections, then confirm submission. A submitted ballot cannot be changed.")
            if (election != null) {
                SecondaryButton("Browse candidates") { viewModel.navigate(Screen.Candidates(election.id)) }
            }
        }
    }
}
