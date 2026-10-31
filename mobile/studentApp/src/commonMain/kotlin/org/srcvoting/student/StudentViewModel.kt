package org.srcvoting.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.srcvoting.core.ApiException
import org.srcvoting.core.BallotSelection
import org.srcvoting.core.Candidate
import org.srcvoting.core.DashboardResponse
import org.srcvoting.core.Notification
import org.srcvoting.core.Position
import org.srcvoting.core.ResultsResponse
import org.srcvoting.core.StudentApiClient
import org.srcvoting.core.StudentProfile

sealed interface Screen {
    data object Login : Screen
    data object ChangePassword : Screen
    data object Dashboard : Screen
    data class Candidates(val electionId: String) : Screen
    data class Ballot(val electionId: String) : Screen
    data class Review(val electionId: String, val selections: List<SelectedCandidate>) : Screen
    data class Receipt(val electionId: String) : Screen
    data class Results(val electionId: String) : Screen
    data object Notifications : Screen
}

data class SelectedCandidate(
    val positionId: String,
    val positionName: String,
    val candidateId: String,
    val candidateName: String,
)

data class StudentUiState(
    val screen: Screen = Screen.Login,
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val profile: StudentProfile? = null,
    val dashboard: DashboardResponse? = null,
    val positions: List<Position> = emptyList(),
    val candidates: List<Candidate> = emptyList(),
    val receiptReference: String? = null,
    val results: ResultsResponse? = null,
    val notifications: List<Notification> = emptyList(),
)

/**
 * One API client per app process: its Ktor HttpClient keeps a cookie jar alive for the session's
 * lifetime, exactly mirroring how the web student-site relies on the browser's own cookie jar for
 * src_session/src_csrf. There is no token to persist across app restarts by design -- a relaunch
 * starts a fresh (logged-out) session, same as closing and reopening a browser that doesn't
 * remember you.
 */
class StudentViewModel(private val api: StudentApiClient = StudentApiClient()) : ViewModel() {
    private val _state = MutableStateFlow(StudentUiState())
    val state: StateFlow<StudentUiState> = _state.asStateFlow()

    private fun setError(message: String) = _state.update { it.copy(error = message, loading = false) }
    private fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    fun navigate(screen: Screen) {
        clearMessages()
        _state.update { it.copy(screen = screen) }
        when (screen) {
            is Screen.Dashboard -> loadDashboard()
            is Screen.Candidates -> loadCandidates(screen.electionId)
            is Screen.Ballot -> loadBallot(screen.electionId)
            is Screen.Receipt -> loadReceipt(screen.electionId)
            is Screen.Results -> loadResults(screen.electionId)
            is Screen.Notifications -> loadNotifications()
            else -> Unit
        }
    }

    fun login(registrationNumber: String, password: String) {
        clearMessages()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val result = api.login(registrationNumber, password)
                _state.update { it.copy(loading = false) }
                if (result.must_change_password) {
                    navigate(Screen.ChangePassword)
                } else {
                    navigate(Screen.Dashboard)
                }
            } catch (e: ApiException) {
                setError(e.message ?: "Sign-in failed.")
            } catch (e: Exception) {
                setError("Could not reach the server. Check your connection and try again.")
            }
        }
    }

    fun changePassword(current: String, new: String, confirm: String) {
        clearMessages()
        if (new != confirm) return setError("New password and confirmation do not match.")
        if (new.length < 8) return setError("New password must be at least 8 characters.")
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                api.changePassword(current, new)
                _state.update { it.copy(loading = false, info = "Password updated.") }
                navigate(Screen.Dashboard)
            } catch (e: ApiException) {
                setError(e.message ?: "Password could not be updated.")
            } catch (e: Exception) {
                setError("Could not reach the server. Check your connection and try again.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { api.logout() }
            _state.value = StudentUiState()
        }
    }

    private fun loadDashboard() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val profile = api.me()
                if (profile.must_change_password) {
                    _state.update { it.copy(loading = false, profile = profile, screen = Screen.ChangePassword) }
                    return@launch
                }
                val dashboard = api.dashboard()
                _state.update { it.copy(loading = false, profile = profile, dashboard = dashboard) }
            } catch (e: ApiException) {
                _state.value = StudentUiState(error = e.message)
            } catch (e: Exception) {
                _state.value = StudentUiState(error = "Could not reach the server. Check your connection and try again.")
            }
        }
    }

    private fun loadCandidates(electionId: String) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val positions = api.positions(electionId)
                val candidates = api.candidates(electionId)
                _state.update { it.copy(loading = false, positions = positions, candidates = candidates) }
            } catch (e: ApiException) {
                setError(e.message ?: "Candidates are not available right now.")
            }
        }
    }

    private fun loadBallot(electionId: String) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val eligible = api.eligibility(electionId)
                if (!eligible.eligible) {
                    setError(eligible.reason)
                    navigate(Screen.Dashboard)
                    return@launch
                }
                val positions = api.positions(electionId)
                val candidates = api.candidates(electionId)
                _state.update { it.copy(loading = false, positions = positions, candidates = candidates) }
            } catch (e: ApiException) {
                setError(e.message ?: "The ballot is not available right now.")
                navigate(Screen.Dashboard)
            }
        }
    }

    fun reviewBallot(electionId: String, selections: List<SelectedCandidate>) {
        if (selections.isEmpty()) return setError("Choose at least one candidate before reviewing.")
        navigate(Screen.Review(electionId, selections))
    }

    fun confirmBallot(electionId: String, selections: List<SelectedCandidate>) {
        clearMessages()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val result = api.submitBallot(
                    electionId,
                    selections.map { BallotSelection(it.positionId, it.candidateId) },
                )
                _state.update { it.copy(loading = false, receiptReference = result.receipt_reference, screen = Screen.Receipt(electionId)) }
            } catch (e: ApiException) {
                setError(e.message ?: "Your ballot could not be accepted.")
            }
        }
    }

    private fun loadReceipt(electionId: String) {
        if (_state.value.receiptReference != null) return
        viewModelScope.launch {
            try {
                val receipt = api.receipt(electionId)
                _state.update { it.copy(receiptReference = receipt.reference) }
            } catch (e: ApiException) {
                setError(e.message ?: "No receipt found.")
            }
        }
    }

    private fun loadResults(electionId: String) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val results = api.results(electionId)
                _state.update { it.copy(loading = false, results = results) }
            } catch (e: ApiException) {
                setError(e.message ?: "Results are not currently available.")
                navigate(Screen.Dashboard)
            }
        }
    }

    private fun loadNotifications() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val list = api.notifications()
                _state.update { it.copy(loading = false, notifications = list) }
            } catch (e: ApiException) {
                setError(e.message ?: "Notifications are not available right now.")
                navigate(Screen.Dashboard)
            }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            runCatching { api.markNotificationRead(id) }
            loadNotifications()
        }
    }
}
