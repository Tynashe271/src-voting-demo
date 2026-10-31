package org.srcvoting.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PublishedWithChanges
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import org.srcvoting.core.StudentImportEntry

@Composable
private fun CandidateAvatar(name: String, photoUrl: String?) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(AdminSlate100),
        )
    } else {
        Box(Modifier.size(40.dp).background(AdminSlate100, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.firstOrNull()?.uppercase() ?: "?", color = AdminSlate700)
        }
    }
}

@Composable
fun AdminElectionDetailScreen(electionId: String, state: AdminUiState, viewModel: AdminViewModel) {
    val election = state.currentElection

    AdminScaffold(
        title = election?.name ?: "Election",
        subtitle = "Status: ${election?.status ?: ""}",
        state = state,
        viewModel = viewModel,
    ) {
        AdminCard {
            Text("Turnout & results", style = MaterialTheme.typography.titleMedium, color = AdminSlate900)
            val turnout = state.turnout
            if (turnout != null) {
                Row(Modifier.padding(top = 8.dp)) {
                    Text("Eligible: ${turnout.eligible_students}   ", style = MaterialTheme.typography.bodyMedium)
                    Text("Votes cast: ${turnout.votes_cast}   ", style = MaterialTheme.typography.bodyMedium)
                    Text("Turnout: ${turnout.turnout_percent}%", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("Turnout is not currently available for this election.", modifier = Modifier.padding(top = 8.dp))
            }

            val results = state.results
            if (results != null) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                if (results.published) {
                    ApprovalChip("APPROVED")
                    Text("Published ${results.published_at ?: ""}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                } else {
                    ApprovalChip("PENDING")
                    Text("Staff-only preview — not published to students", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    AdminPrimaryButton("Publish results") { viewModel.publishResults(electionId) }
                }
                results.totals.orEmpty().groupBy { it.position }.forEach { (positionName, rows) ->
                    Text(positionName, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
                    rows.sortedByDescending { it.votes }.forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(row.candidate, modifier = Modifier.weight(1f))
                            Text("${row.votes}")
                        }
                    }
                }
                Text("Integrity hash: ${results.integrity_hash}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("Results are not sealed yet — the election must close first.", modifier = Modifier.padding(top = 10.dp))
            }
        }

        var regNumbers by remember { mutableStateOf("") }
        AdminCard {
            Text("Voter eligibility", style = MaterialTheme.typography.titleMedium, color = AdminSlate900)
            OutlinedTextField(
                value = regNumbers,
                onValueChange = { regNumbers = it },
                label = { Text("Registration numbers (one per line, or comma-separated)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row {
                AdminPrimaryButton("Mark eligible") {
                    viewModel.setEligibility(electionId, regNumbers.split(Regex("[,\n\r]+")).map { it.trim() }.filter { it.isNotEmpty() }, true)
                }
                AdminSecondaryButton("Mark not eligible") {
                    viewModel.setEligibility(electionId, regNumbers.split(Regex("[,\n\r]+")).map { it.trim() }.filter { it.isNotEmpty() }, false)
                }
            }
            Text("Students must already exist in the register (see Students) and eligibility can only be changed before voting opens.", style = MaterialTheme.typography.bodySmall)
        }

        Text("Positions & candidates", style = MaterialTheme.typography.titleLarge, color = AdminSlate900, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        state.positions.forEach { position ->
            AdminCard {
                Text(position.name, style = MaterialTheme.typography.titleMedium, color = AdminSlate900)
                val people = state.candidates.filter { it.position_id == position.id }
                if (people.isEmpty()) Text("No candidates yet.", modifier = Modifier.padding(top = 6.dp))
                people.forEach { c ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CandidateAvatar(c.name, c.photo_url)
                        Text(c.name, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        ApprovalChip(c.approval_status)
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        if (c.approval_status !in listOf("APPROVED", "DISQUALIFIED")) {
                            AdminSecondaryButton("Approve") { viewModel.approveCandidate(electionId, c.id) }
                        }
                        if (c.approval_status !in listOf("REJECTED", "DISQUALIFIED")) {
                            AdminSecondaryButton("Reject") { viewModel.rejectCandidate(electionId, c.id) }
                        }
                        if (c.approval_status == "APPROVED") {
                            AdminSecondaryButton("Disqualify") { viewModel.disqualifyCandidate(electionId, c.id) }
                        }
                    }
                }

                var candidateName by remember { mutableStateOf("") }
                var photoUrl by remember { mutableStateOf("") }
                var biography by remember { mutableStateOf("") }
                var manifesto by remember { mutableStateOf("") }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Text("Add candidate", style = MaterialTheme.typography.labelLarge, color = AdminSlate700)
                OutlinedTextField(candidateName, { candidateName = it }, label = { Text("Candidate name") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(photoUrl, { photoUrl = it }, label = { Text("Photo URL (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(biography, { biography = it }, label = { Text("Biography (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(manifesto, { manifesto = it }, label = { Text("Manifesto (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                AdminPrimaryButton("Add candidate") {
                    viewModel.createCandidate(electionId, position.id, candidateName, photoUrl, biography, manifesto)
                }
            }
        }

        var positionName by remember { mutableStateOf("") }
        var positionOrder by remember { mutableStateOf("1") }
        var maxSelections by remember { mutableStateOf("1") }
        AdminCard {
            Text("Add a position", style = MaterialTheme.typography.titleMedium, color = AdminSlate900)
            OutlinedTextField(positionName, { positionName = it }, label = { Text("Position name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(positionOrder, { positionOrder = it }, label = { Text("Display order") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(maxSelections, { maxSelections = it }, label = { Text("Max selections") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            AdminPrimaryButton("Add position") {
                viewModel.createPosition(electionId, positionName, "", positionOrder.toIntOrNull() ?: 1, maxSelections.toIntOrNull() ?: 1)
            }
        }
    }
}

@Composable
fun AdminStudentsScreen(state: AdminUiState, viewModel: AdminViewModel) {
    var csv by remember { mutableStateOf("") }

    AdminScaffold(
        title = "Import / refresh official student register",
        subtitle = "One student per line: registration_number,first_name,last_name,university_email,national_id,faculty,programme,level",
        state = state,
        viewModel = viewModel,
    ) {
        AdminCard {
            OutlinedTextField(
                value = csv,
                onValueChange = { csv = it },
                label = { Text("Students (CSV, one per line)") },
                modifier = Modifier.fillMaxWidth(),
            )
            AdminPrimaryButton("Import") {
                val entries = csv.lines().mapNotNull { line ->
                    val parts = line.split(",").map { it.trim() }
                    if (parts.size < 5) return@mapNotNull null
                    StudentImportEntry(
                        registration_number = parts[0],
                        first_name = parts[1],
                        last_name = parts[2],
                        university_email = parts[3],
                        national_id = parts[4],
                        faculty = parts.getOrElse(5) { "" },
                        programme = parts.getOrElse(6) { "" },
                        level = parts.getOrElse(7) { "" },
                    )
                }
                if (entries.isNotEmpty()) viewModel.importStudents(entries)
            }
        }
    }
}

@Composable
fun AdminAuditScreen(state: AdminUiState, viewModel: AdminViewModel) {
    AdminScaffold(
        title = "Audit trail",
        subtitle = "Ballot & result integrity log (Go, hash-chained).",
        state = state,
        viewModel = viewModel,
    ) {
        if (state.auditEntries.isEmpty()) {
            AdminCard { Text("No entries yet.") }
        }
        state.auditEntries.forEach { entry ->
            AdminCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PublishedWithChanges, contentDescription = null, tint = AdminSlate700, modifier = Modifier.size(18.dp))
                    Text("  ${entry.action} — ${entry.resource}", style = MaterialTheme.typography.titleSmall)
                }
                Text("${entry.actor} · ${entry.created_at}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
