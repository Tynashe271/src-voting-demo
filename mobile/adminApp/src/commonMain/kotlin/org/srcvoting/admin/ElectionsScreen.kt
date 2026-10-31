package org.srcvoting.admin

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import org.srcvoting.core.Election

/** The API returns election_date as a full ISO timestamp, but the edit form and the Go backend's
 *  create/update validation both want a bare YYYY-MM-DD. */
private fun String?.toDateOnly(): String = this?.substringBefore('T') ?: ""

@Composable
fun AdminElectionsScreen(state: AdminUiState, viewModel: AdminViewModel) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    AdminScaffold(
        title = "Elections",
        state = state,
        viewModel = viewModel,
    ) {
        if (state.elections.isEmpty()) {
            AdminCard { Text("No elections yet. Create one to get started.") }
        }
        state.elections.forEach { election ->
            AdminElectionCard(election, viewModel)
        }
        AdminCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, tint = AdminSlate700)
                Text("  New election", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Election date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            AdminPrimaryButton("Create election") { viewModel.createElection(name, date) }
        }
    }
}

@Composable
private fun AdminElectionCard(election: Election, viewModel: AdminViewModel) {
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(election.name) }
    var date by remember { mutableStateOf(election.election_date.toDateOnly()) }

    AdminCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = AdminSlate700)
            Text(election.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
            IconButton(onClick = {
                name = election.name
                date = election.election_date.toDateOnly()
                editing = !editing
            }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit election", tint = AdminSlate700)
            }
        }
        Text("${election.election_date ?: ""} — ${election.status ?: ""}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 32.dp))
        if (editing) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Election date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            AdminPrimaryButton("Save changes") {
                viewModel.updateElection(election.id, name, date)
                editing = false
            }
        }
        AdminSecondaryButton("Manage") { viewModel.navigate(AdminScreen.ElectionDetail(election.id)) }
    }
}
