package org.srcvoting.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.srcvoting.core.Position

@Composable
private fun CandidateAvatar(name: String, photoUrl: String?, size: Int = 52) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFE6ECE7)),
        )
    } else {
        Box(
            Modifier.size(size.dp).background(Color(0xFFE6ECE7), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.firstOrNull()?.uppercase() ?: "?", color = SrcGreen, style = MaterialTheme.typography.titleMedium)
        }
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
                Text(position.name.uppercase(), style = MaterialTheme.typography.labelLarge, color = SrcGold)
                val people = state.candidates.filter { it.position_id == position.id }
                if (people.isEmpty()) {
                    Text("Candidate details are being prepared.", modifier = Modifier.padding(top = 8.dp))
                }
                people.forEach { c ->
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Row {
                        CandidateAvatar(c.name, c.photo_url)
                        Box(Modifier.padding(start = 14.dp)) {
                            Column {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                val bio = c.biography
                                if (!bio.isNullOrBlank()) Text(bio, style = MaterialTheme.typography.bodyMedium)
                                Text("MANIFESTO", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                                Text(c.manifesto?.ifBlank { "No manifesto supplied." } ?: "No manifesto supplied.", style = MaterialTheme.typography.bodyMedium)
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
                Text("${position.name} — choose up to ${position.max_selections}", style = MaterialTheme.typography.labelLarge, color = SrcGold)
                val people = state.candidates.filter { it.position_id == position.id }
                people.forEach { c ->
                    val key = position.id
                    val selected = selections.value[key]?.candidateId == c.id

                    fun toggle() {
                        val updated = selections.value.toMutableMap()
                        if (selected) updated.remove(key) else updated[key] = SelectedCandidate(position.id, position.name, c.id, c.name)
                        selections.value = updated
                    }

                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).selectable(selected = selected) { toggle() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFEFF6F1) else Color.White),
                        border = BorderStroke(1.5.dp, if (selected) SrcGreen else Color(0xFFDCE2DC)),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, onCheckedChange = { toggle() })
                            Spacer(Modifier.size(6.dp))
                            CandidateAvatar(c.name, c.photo_url, size = 44)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall)
                                val bio = c.biography
                                if (!bio.isNullOrBlank()) Text(bio, style = MaterialTheme.typography.bodySmall)
                            }
                            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = SrcGreen)
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
                Text(it.positionName.uppercase(), style = MaterialTheme.typography.labelMedium, color = SrcGold)
                Text(it.candidateName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
            }
            Row {
                SecondaryButton("Change selections") { viewModel.navigate(Screen.Ballot(electionId)) }
                Spacer(Modifier.size(12.dp))
                PrimaryButton("Confirm and submit") { viewModel.confirmBallot(electionId, selections) }
            }
            Text(
                "Submission is final. The receipt confirms participation and does not reveal your choices.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SrcGreen)
                Text("  Successfully recorded", color = SrcGreen, style = MaterialTheme.typography.labelLarge)
            }
            Text(state.receiptReference ?: "", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SrcSerif), modifier = Modifier.padding(vertical = 12.dp))
            Text("Keep this reference for your records. It does not prove which choices you selected.")
            PrimaryButton("Return to dashboard") { viewModel.navigate(Screen.Dashboard) }
        }
    }
}
