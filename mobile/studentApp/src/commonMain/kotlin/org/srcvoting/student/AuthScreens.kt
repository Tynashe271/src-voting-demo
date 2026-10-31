package org.srcvoting.student

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(state: StudentUiState, viewModel: StudentViewModel) {
    var registrationNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    ScreenScaffold(
        title = "Welcome back",
        subtitle = "Sign in with your registration number and national ID number.",
        state = state,
        viewModel = viewModel,
        showNav = false,
    ) {
        InfoCard {
            OutlinedTextField(
                value = registrationNumber,
                onValueChange = { registrationNumber = it },
                label = { Text("Registration number") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("National ID number") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            PrimaryButton("Sign in") { viewModel.login(registrationNumber, password) }
        }
    }
}

@Composable
fun ChangePasswordScreen(state: StudentUiState, viewModel: StudentViewModel) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    ScreenScaffold(
        title = "Set a new password",
        subtitle = "Your current password is the national ID number the university provided, which is not secret. Choose a new password before continuing.",
        state = state,
        viewModel = viewModel,
        showNav = false,
    ) {
        InfoCard {
            OutlinedTextField(
                value = current,
                onValueChange = { current = it },
                label = { Text("Current password (national ID number)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = new,
                onValueChange = { new = it },
                label = { Text("New password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Confirm new password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            PrimaryButton("Set new password") { viewModel.changePassword(current, new, confirm) }
        }
    }
}
