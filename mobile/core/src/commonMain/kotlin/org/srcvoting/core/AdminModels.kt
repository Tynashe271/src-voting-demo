package org.srcvoting.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Laravel's JSON controllers all respond with this envelope: {success, message?, data?}. */
@Serializable
data class AdminEnvelope<T>(
    val success: Boolean = false,
    val message: String = "",
    val data: T? = null,
)

@Serializable
data class AdminAccount(
    val id: String = "",
    val staff_identifier: String? = null,
    val email: String = "",
    val mfa_enabled: Boolean = false,
    val account_status: String? = null,
)

@Serializable
data class AdminMfaSetup(
    val secret: String = "",
    val qr_code_url: String = "",
)

@Serializable
data class AdminTurnout(
    val eligible_students: Long = 0,
    val votes_cast: Long = 0,
    val turnout_percent: Double = 0.0,
)

@Serializable
data class AdminResults(
    val status: String = "",
    val totals: List<ResultRow>? = null,
    val integrity_hash: String = "",
    val generated_at: String? = null,
    val published: Boolean = false,
    val published_at: String? = null,
)

@Serializable
data class AdminEligibilityResult(
    val updated: Int = 0,
    val failed: List<JsonElement> = emptyList(),
)

@Serializable
data class AdminStudentImportResult(
    val upserted: Int = 0,
    val failed: List<JsonElement> = emptyList(),
)

@Serializable
data class AdminPublishResult(
    val published: Boolean = false,
    val visible_until: String? = null,
)
