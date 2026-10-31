package org.srcvoting.admin

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.srcvoting.core.StudentImportEntry

@Composable
fun AdminElectionDetailScreen(electionId: String, state: AdminUiState, viewModel: AdminViewModel) {
    val election = state.currentElection

    AdminScaffold(
        title = election?.name ?: "Election",
        subtitle = "Status: ${election?.status ?: ""}",
        state = state,
        viewModel = viewModel,
    ) {
        AdminSecondaryButton("← All elections") { viewModel.navigate(AdminScreen.Elections) }

        AdminCard {
            Text("Turnout & results", style = MaterialTheme.typography.titleMedium)
            val turnout = state.turnout
            if (turnout != null) {
                Text("Eligible: ${turnout.eligible_students}  Votes cast: ${turnout.votes_cast}  Turnout: ${turnout.turnout_percent}%")
            } else {
                Text("Turnout is not currently available for this election.")
            }

            val results = state.results
            if (results != null) {
                if (results.published) {
                    AssistChip(onClick = {}, label = { Text("Published ${results.published_at ?: ""}") })
                } else {
                    AssistChip(onClick = {}, label = { Text("Staff-only preview — not published to students") })
                    AdminPrimaryButton("Publish results") { viewModel.publishResults(electionId) }
                }
                results.totals.orEmpty().groupBy { it.position }.forEach { (positionName, rows) ->
                    Text(positionName, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                    rows.sortedByDescending { it.votes }.forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(row.candidate, modifier = Modifier.weight(1f))
                            Text("${row.votes}")
                        }
                    }
                }
                Text("Integrity hash: ${results.integrity_hash}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Results are not sealed yet — the election must close first.")
            }
        }

        var regNumbers by remember { mutableStateOf("") }
        AdminCard {
            Text("Voter eligibility", style = MaterialTheme.typography.titleMedium)
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

        Text("Positions & candidates", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        state.positions.forEach { position ->
            AdminCard {
                Text(position.name, style = MaterialTheme.typography.titleMedium)
                val people = state.candidates.filter { it.position_id == position.id }
                if (people.isEmpty()) Text("No candidates yet.")
                people.forEach { c ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(c.name, modifier = Modifier.weight(1f))
                        Text(c.approval_status ?: "")
                    }
                    Row {
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
                Text("Add candidate", style = MaterialTheme.typography.labelLarge)
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
            Text("Add a position", style = MaterialTheme.typography.titleMedium)
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
                Text("${entry.action} — ${entry.resource}", style = MaterialTheme.typography.titleSmall)
                Text("${entry.actor} · ${entry.created_at}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
