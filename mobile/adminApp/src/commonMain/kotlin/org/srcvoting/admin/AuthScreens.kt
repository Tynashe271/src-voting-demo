package org.srcvoting.admin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AdminLoginScreen(state: AdminUiState, viewModel: AdminViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AdminScaffold(
        title = "SRC Elections Admin",
        subtitle = "Staff sign-in. This is a service-management portal — students vote on the separate student app.",
        state = state,
        viewModel = viewModel,
        showNav = false,
    ) {
        AdminCard {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            AdminPrimaryButton("Sign in") { viewModel.login(email, password) }
        }
    }
}

@Composable
fun AdminMfaCodeScreen(email: String, password: String, state: AdminUiState, viewModel: AdminViewModel) {
    var otp by remember { mutableStateOf("") }

    AdminScaffold(
        title = "Verification code",
        subtitle = "Enter the 6-digit code from your authenticator app.",
        state = state,
        viewModel = viewModel,
        showNav = false,
    ) {
        AdminCard {
            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it },
                label = { Text("Verification code") },
                modifier = Modifier.fillMaxWidth(),
            )
            AdminPrimaryButton("Verify") { viewModel.loginWithOtp(email, password, otp) }
        }
    }
}

@Composable
fun AdminMfaSetupScreen(state: AdminUiState, viewModel: AdminViewModel) {
    var otp by remember { mutableStateOf("") }
    val setup = state.mfaSetup

    AdminScaffold(
        title = "Set up multi-factor authentication",
        subtitle = "This account cannot be used without it. Scan the secret into an authenticator app, or enter it manually, then confirm a code.",
        state = state,
        viewModel = viewModel,
        showNav = false,
    ) {
        AdminCard {
            Text("Secret", style = MaterialTheme.typography.labelMedium)
            Text(setup?.secret ?: "", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it },
                label = { Text("Verification code") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            AdminPrimaryButton("Enable MFA") { viewModel.enableMfa(otp) }
        }
    }
}
