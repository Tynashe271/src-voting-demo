package org.srcvoting.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.srcvoting.core.AdminAccount
import org.srcvoting.core.AdminApiClient
import org.srcvoting.core.AdminMfaSetup
import org.srcvoting.core.AdminResults
import org.srcvoting.core.AdminTurnout
import org.srcvoting.core.ApiException
import org.srcvoting.core.AuditEntry
import org.srcvoting.core.Candidate
import org.srcvoting.core.CreatePositionRequest
import org.srcvoting.core.Election
import org.srcvoting.core.MfaCodeRequiredException
import org.srcvoting.core.MfaSetupRequiredException
import org.srcvoting.core.Position

sealed interface AdminScreen {
    data object Login : AdminScreen
    data class MfaCode(val email: String, val password: String) : AdminScreen
    data object MfaSetup : AdminScreen
    data object Elections : AdminScreen
    data class ElectionDetail(val electionId: String) : AdminScreen
    data object Students : AdminScreen
    data object Audit : AdminScreen
    data object Account : AdminScreen
}

data class AdminUiState(
    val screen: AdminScreen = AdminScreen.Login,
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val account: AdminAccount? = null,
    val mfaSetup: AdminMfaSetup? = null,
    val elections: List<Election> = emptyList(),
    val currentElection: Election? = null,
    val positions: List<Position> = emptyList(),
    val candidates: List<Candidate> = emptyList(),
    val turnout: AdminTurnout? = null,
    val results: AdminResults? = null,
    val auditEntries: List<AuditEntry> = emptyList(),
)

/**
 * Talks only to the Laravel admin-site's JSON API, never to the Go backend's internal admin
 * endpoints directly -- see AdminApiClient's own doc comment for why that split exists. One
 * instance per app process keeps the session cookie jar (and so the logged-in session) alive for
 * as long as the app is running.
 */
class AdminViewModel(private val api: AdminApiClient = AdminApiClient()) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private fun setError(message: String) = _state.update { it.copy(error = message, loading = false) }
    private fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    fun navigate(screen: AdminScreen) {
        clearMessages()
        _state.update { it.copy(screen = screen) }
        when (screen) {
            is AdminScreen.Elections -> loadElections()
            is AdminScreen.ElectionDetail -> loadElectionDetail(screen.electionId)
            is AdminScreen.Audit -> loadAudit()
            is AdminScreen.Account -> loadAccount()
            else -> Unit
        }
    }

    fun login(email: String, password: String) {
        clearMessages()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val account = api.login(email, password)
                _state.update { it.copy(loading = false, account = account) }
                if (!account.mfa_enabled) {
                    beginMfaSetup()
                } else {
                    navigate(AdminScreen.Elections)
                }
            } catch (e: MfaCodeRequiredException) {
                _state.update { it.copy(loading = false, screen = AdminScreen.MfaCode(email, password)) }
            } catch (e: ApiException) {
                setError(e.message ?: "Sign-in failed.")
            } catch (e: Exception) {
                setError("Could not reach the server. Check your connection and try again.")
            }
        }
    }

    fun loginWithOtp(email: String, password: String, otp: String) {
        clearMessages()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val account = api.login(email, password, otp)
                _state.update { it.copy(loading = false, account = account) }
                navigate(AdminScreen.Elections)
            } catch (e: ApiException) {
                setError(e.message ?: "Invalid code.")
            }
        }
    }

    private fun beginMfaSetup() {
        _state.update { it.copy(loading = true, screen = AdminScreen.MfaSetup) }
        viewModelScope.launch {
            try {
                val setup = api.setupMfa()
                _state.update { it.copy(loading = false, mfaSetup = setup) }
            } catch (e: ApiException) {
                setError(e.message ?: "MFA setup could not be started.")
            }
        }
    }

    fun enableMfa(otp: String) {
        clearMessages()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                api.enableMfa(otp)
                _state.update { it.copy(loading = false, info = "MFA enabled.") }
                navigate(AdminScreen.Elections)
            } catch (e: ApiException) {
                setError(e.message ?: "Invalid code.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { api.logout() }
            _state.value = AdminUiState()
        }
    }

    private fun loadElections() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val elections = api.listElections()
                _state.update { it.copy(loading = false, elections = elections) }
            } catch (e: MfaSetupRequiredException) {
                beginMfaSetup()
            } catch (e: ApiException) {
                setError(e.message ?: "Elections could not be loaded.")
            }
        }
    }

    fun createElection(name: String, electionDate: String) {
        clearMessages()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                api.createElection(name, electionDate)
                loadElections()
            } catch (e: ApiException) {
                setError(e.message ?: "Election could not be created.")
            }
        }
    }

    fun updateElection(electionId: String, name: String, electionDate: String) {
        clearMessages()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                api.updateElection(electionId, name, electionDate)
                loadElections()
            } catch (e: ApiException) {
                setError(e.message ?: "Election could not be updated.")
            }
        }
    }

    private fun loadElectionDetail(electionId: String) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val election = api.listElections().firstOrNull { it.id == electionId }
                val positions = api.positions(electionId)
                val candidates = api.candidates(electionId)
                val turnout = runCatching { api.turnout(electionId) }.getOrNull()
                val results = runCatching { api.results(electionId) }.getOrNull()
                _state.update {
                    it.copy(
                        loading = false,
                        currentElection = election,
                        positions = positions,
                        candidates = candidates,
                        turnout = turnout,
                        results = results,
                    )
                }
            } catch (e: ApiException) {
                setError(e.message ?: "Election could not be loaded.")
            }
        }
    }

    fun createPosition(electionId: String, name: String, description: String, order: Int, maxSelections: Int) {
        clearMessages()
        viewModelScope.launch {
            try {
                api.createPosition(electionId, CreatePositionRequest(name, description, order, maxSelections))
                loadElectionDetail(electionId)
            } catch (e: ApiException) {
                setError(e.message ?: "Position could not be created.")
            }
        }
    }

    fun updatePosition(electionId: String, positionId: String, name: String, description: String, order: Int, maxSelections: Int) {
        clearMessages()
        viewModelScope.launch {
            try {
                api.updatePosition(positionId, CreatePositionRequest(name, description, order, maxSelections))
                loadElectionDetail(electionId)
            } catch (e: ApiException) {
                setError(e.message ?: "Position could not be updated.")
            }
        }
    }

    fun createCandidate(
        electionId: String,
        positionId: String,
        name: String,
        photoUrl: String,
        biography: String,
        manifesto: String,
        photoBytes: ByteArray? = null,
        photoFileName: String? = null,
    ) {
        clearMessages()
        viewModelScope.launch {
            try {
                api.createCandidate(positionId, name, photoUrl, biography, manifesto, photoBytes, photoFileName)
                loadElectionDetail(electionId)
            } catch (e: ApiException) {
                setError(e.message ?: "Candidate could not be created. The election may already be open.")
            }
        }
    }

    fun updateCandidate(
        electionId: String,
        candidateId: String,
        name: String,
        photoUrl: String,
        biography: String,
        manifesto: String,
        photoBytes: ByteArray? = null,
        photoFileName: String? = null,
    ) {
        clearMessages()
        viewModelScope.launch {
            try {
                api.updateCandidate(candidateId, name, photoUrl, biography, manifesto, photoBytes, photoFileName)
                loadElectionDetail(electionId)
            } catch (e: ApiException) {
                setError(e.message ?: "Candidate could not be updated.")
            }
        }
    }

    fun approveCandidate(electionId: String, candidateId: String) = actOnCandidate(electionId, candidateId) { api.approveCandidate(it) }
    fun rejectCandidate(electionId: String, candidateId: String) = actOnCandidate(electionId, candidateId) { api.rejectCandidate(it) }
    fun disqualifyCandidate(electionId: String, candidateId: String) = actOnCandidate(electionId, candidateId) { api.disqualifyCandidate(it) }

    private fun actOnCandidate(electionId: String, candidateId: String, action: suspend (String) -> Unit) {
        clearMessages()
        viewModelScope.launch {
            try {
                action(candidateId)
                loadElectionDetail(electionId)
            } catch (e: ApiException) {
                setError(e.message ?: "Candidate status could not be changed.")
            }
        }
    }

    fun setEligibility(electionId: String, registrationNumbers: List<String>, eligible: Boolean) {
        clearMessages()
        viewModelScope.launch {
            try {
                val result = api.setEligibility(electionId, registrationNumbers.map { org.srcvoting.core.EligibilityEntry(it, eligible) })
                _state.update { it.copy(info = "Updated ${result.updated} student(s).") }
                loadElectionDetail(electionId)
            } catch (e: ApiException) {
                setError(e.message ?: "Eligibility could not be updated.")
            }
        }
    }

    fun publishResults(electionId: String) {
        clearMessages()
        viewModelScope.launch {
            try {
                api.publishResults(electionId)
                loadElectionDetail(electionId)
            } catch (e: ApiException) {
                setError(e.message ?: "Results could not be published.")
            }
        }
    }

    fun importStudents(students: List<org.srcvoting.core.StudentImportEntry>) {
        clearMessages()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val result = api.importStudents(students)
                _state.update { it.copy(loading = false, info = "Imported ${result.upserted} student(s).") }
            } catch (e: ApiException) {
                setError(e.message ?: "Students could not be imported.")
            }
        }
    }

    private fun loadAudit() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val entries = api.audit()
                _state.update { it.copy(loading = false, auditEntries = entries) }
            } catch (e: ApiException) {
                setError(e.message ?: "Audit log could not be loaded.")
            }
        }
    }

    private fun loadAccount() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val account = api.me()
                _state.update { it.copy(loading = false, account = account) }
            } catch (e: ApiException) {
                setError(e.message ?: "Account details could not be loaded.")
            }
        }
    }

    /** Re-enrollment (a fresh QR code) after this completes is the same [beginMfaSetup] flow login uses. */
    fun disableMfa(password: String) {
        clearMessages()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                api.disableMfa(password)
                _state.update { it.copy(info = "MFA disabled.") }
                loadAccount()
            } catch (e: ApiException) {
                setError(e.message ?: "MFA could not be disabled.")
            }
        }
    }

    fun reenrollMfa() = beginMfaSetup()
}
