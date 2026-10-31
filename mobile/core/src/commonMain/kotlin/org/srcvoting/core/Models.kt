package org.srcvoting.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StudentProfile(
    val registration_number: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val university_email: String? = null,
    val faculty: String? = null,
    val programme: String? = null,
    val level: String? = null,
    val must_change_password: Boolean = false,
)

@Serializable
data class Election(
    val id: String,
    val name: String,
    val election_date: String? = null,
    val opens_at: String? = null,
    val closes_at: String? = null,
    val status: String? = null,
    val results_visible_from: String? = null,
    val results_visible_until: String? = null,
    val has_voted: Boolean = false,
)

@Serializable
data class Position(
    val id: String,
    val election_id: String? = null,
    val name: String,
    val description: String? = null,
    val display_order: Int = 0,
    val max_selections: Int = 1,
)

@Serializable
data class Candidate(
    val id: String,
    val position_id: String,
    val name: String,
    val photo_url: String? = null,
    val biography: String? = null,
    val manifesto: String? = null,
    val approval_status: String? = null,
)

@Serializable
data class DashboardResponse(
    val student: StudentProfile? = null,
    val election: Election? = null,
    val notifications: List<Notification> = emptyList(),
    val results_available: Boolean = false,
)

@Serializable
data class Notification(
    val id: String,
    val type: String,
    val message: String,
    val read_at: String? = null,
    val created_at: String,
)

@Serializable
data class EligibilityResponse(
    val eligible: Boolean,
    val reason: String = "",
)

@Serializable
data class LoginResponse(
    val success: Boolean = true,
    val csrf_token: String? = null,
    val must_change_password: Boolean = false,
)

@Serializable
data class ApiMessage(
    val success: Boolean = false,
    val message: String = "",
)

@Serializable
data class BallotSelection(
    val position_id: String,
    val candidate_id: String,
)

@Serializable
data class BallotRequest(
    val selections: List<BallotSelection>,
)

@Serializable
data class BallotSubmitResponse(
    val success: Boolean = true,
    val receipt_reference: String = "",
    val message: String = "",
)

@Serializable
data class ReceiptResponse(
    val status: String = "",
    val reference: String = "",
    val message: String = "",
)

@Serializable
data class ResultRow(
    @SerialName("PositionID") val positionId: String = "",
    @SerialName("CandidateID") val candidateId: String = "",
    @SerialName("Position") val position: String = "",
    @SerialName("Candidate") val candidate: String = "",
    @SerialName("Votes") val votes: Long = 0,
)

@Serializable
data class ResultsResponse(
    val status: String = "",
    val totals: List<ResultRow>? = null,
    val integrity_hash: String = "",
    val visible_until: String? = null,
    val generated_at: String? = null,
    val published: Boolean = false,
    val published_at: String? = null,
)

@Serializable
data class PublishResponse(
    val published: Boolean = false,
    val visible_until: String? = null,
)

@Serializable
data class TurnoutResponse(
    val eligible_students: Long = 0,
    val votes_cast: Long = 0,
    val turnout_percent: Double = 0.0,
)

@Serializable
data class AuditEntry(
    val id: Long,
    val actor: String,
    val action: String,
    val resource: String,
    val request_id: String? = null,
    val previous_hash: String? = null,
    val entry_hash: String,
    val created_at: String,
)

@Serializable
data class EligibilityEntry(
    val registration_number: String,
    val eligible: Boolean,
)

@Serializable
data class SetEligibilityRequest(
    val entries: List<EligibilityEntry>,
)

@Serializable
data class SetEligibilityResponse(
    val updated: Int = 0,
    val failed: List<JsonElement> = emptyList(),
)

@Serializable
data class StudentImportEntry(
    val registration_number: String,
    val first_name: String,
    val last_name: String,
    val university_email: String,
    val national_id: String,
    val faculty: String = "",
    val programme: String = "",
    val level: String = "",
)

@Serializable
data class StudentImportRequest(
    val students: List<StudentImportEntry>,
)

@Serializable
data class StudentImportResponse(
    val upserted: Int = 0,
    val failed: List<JsonElement> = emptyList(),
)

@Serializable
data class CreateElectionRequest(
    val name: String,
    val election_date: String,
)

@Serializable
data class CreatePositionRequest(
    val name: String,
    val description: String = "",
    val display_order: Int,
    val max_selections: Int,
)

@Serializable
data class CreateCandidateRequest(
    val name: String,
    val photo_url: String = "",
    val biography: String = "",
    val manifesto: String = "",
)

/** Thrown for any non-2xx API response; [message] is the server's own user-facing message. */
class ApiException(message: String, val statusCode: Int) : Exception(message)
