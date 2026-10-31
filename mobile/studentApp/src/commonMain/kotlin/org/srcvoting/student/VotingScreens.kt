package org.srcvoting.student

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.srcvoting.core.Position

@Composable
private fun InitialsBadge(name: String) {
    Box(
        Modifier.size(44.dp).background(Color(0xFFE6ECE7), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.firstOrNull()?.uppercase() ?: "?", color = Color(0xFF173C32))
    }
}

@Composable
fun CandidatesScreen(electionId: String, state: StudentUiState, viewModel: StudentViewModel) {
    ScreenScaffold(
        title = "Candidates",
        subtitle = "Learn about each candidate before voting.",
        state = state,
        viewModel = viewModel,
    ) {
        state.positions.forEach { position ->
            InfoCard {
                Text(position.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                val people = state.candidates.filter { it.position_id == position.id }
                if (people.isEmpty()) {
                    Text("Candidate details are being prepared.")
                }
                people.forEach { c ->
                    Divider(Modifier.padding(vertical = 10.dp))
                    Row {
                        InitialsBadge(c.name)
                        Box(Modifier.padding(start = 12.dp)) {
                            androidx.compose.foundation.layout.Column {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text(c.biography.orEmpty())
                                Text("Manifesto", style = MaterialTheme.typography.labelMedium)
                                Text(c.manifesto?.ifBlank { "No manifesto supplied." } ?: "No manifesto supplied.")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BallotScreen(electionId: String, state: StudentUiState, viewModel: StudentViewModel) {
    val selections = remember { mutableStateOf<Map<String, SelectedCandidate>>(emptyMap()) }

    ScreenScaffold(
        title = "Build your ballot",
        subtitle = "Choose candidates by position. Review your choices before final submission.",
        state = state,
        viewModel = viewModel,
    ) {
        state.positions.forEach { position: Position ->
            InfoCard {
                Text("${position.name} — choose up to ${position.max_selections}", style = MaterialTheme.typography.labelLarge)
                val people = state.candidates.filter { it.position_id == position.id }
                people.forEach { c ->
                    val key = position.id
                    val selected = selections.value[key]?.candidateId == c.id
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .selectable(selected = selected) {
                                val updated = selections.value.toMutableMap()
                                if (selected) updated.remove(key) else updated[key] = SelectedCandidate(position.id, position.name, c.id, c.name)
                                selections.value = updated
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = {
                            val updated = selections.value.toMutableMap()
                            if (it) updated[key] = SelectedCandidate(position.id, position.name, c.id, c.name) else updated.remove(key)
                            selections.value = updated
                        })
                        Spacer(Modifier.size(8.dp))
                        InitialsBadge(c.name)
                        Spacer(Modifier.size(10.dp))
                        androidx.compose.foundation.layout.Column {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            Text(c.biography.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        PrimaryButton("Review ballot") {
            viewModel.reviewBallot(electionId, selections.value.values.toList())
        }
    }
}

@Composable
fun ReviewScreen(electionId: String, selections: List<SelectedCandidate>, state: StudentUiState, viewModel: StudentViewModel) {
    ScreenScaffold(
        title = "Review your choices",
        subtitle = "Check each selection carefully. Nothing is recorded until you confirm.",
        state = state,
        viewModel = viewModel,
    ) {
        InfoCard {
            selections.forEach {
                Text(it.positionName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(it.candidateName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            Row {
                SecondaryButton("Change selections") { viewModel.navigate(Screen.Ballot(electionId)) }
                Spacer(Modifier.size(12.dp))
                PrimaryButton("Confirm and submit") { viewModel.confirmBallot(electionId, selections) }
            }
            Text(
                "Submission is final. The receipt confirms participation and does not reveal your choices.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun ReceiptScreen(state: StudentUiState, viewModel: StudentViewModel) {
    ScreenScaffold(
        title = "Your voting receipt",
        subtitle = "This reference confirms ballot acceptance only.",
        state = state,
        viewModel = viewModel,
    ) {
        InfoCard {
            Text("Successfully recorded", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(state.receiptReference ?: "", style = MaterialTheme.typography.headlineSmall)
            Text("Keep this reference for your records. It does not prove which choices you selected.")
            PrimaryButton("Return to dashboard") { viewModel.navigate(Screen.Dashboard) }
        }
    }
}
