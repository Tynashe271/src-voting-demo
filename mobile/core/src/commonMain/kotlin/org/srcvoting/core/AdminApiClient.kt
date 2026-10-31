package org.srcvoting.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Thrown when the account exists and the password was right, but MFA enrollment is required first. */
class MfaSetupRequiredException(message: String) : Exception(message)

/** Thrown when the account has MFA enabled and the login call needs a 6-digit code to proceed. */
class MfaCodeRequiredException(message: String) : Exception(message)

/**
 * Talks to the Laravel admin-site's JSON API (routes/api.php), never to the Go backend's internal
 * admin endpoints directly -- that endpoint needs a service-level secret
 * (INTERNAL_ADMIN_API_KEY) that must only ever live on a server, never inside a binary this app
 * ships to a device. Laravel holds that secret and proxies every write through VotingApiClient,
 * exactly like its own Blade UI does; this client is just a second, JSON-speaking front door onto
 * the same authenticated `admin` session + mandatory-MFA guard (RequireAdminMfa).
 */
class AdminApiClient(
    private val baseUrl: String = "https://src-voting-admin-demo.onrender.com/api",
    private val client: HttpClient = createHttpClient(),
) {
    private suspend fun <T> decode(response: HttpResponse, bodyFn: suspend (HttpResponse) -> AdminEnvelope<T>): T {
        if (response.status.isSuccess()) {
            val envelope = bodyFn(response)
            return envelope.data as T
        }
        val message = runCatching { response.body<AdminEnvelope<Unit>>().message }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Request failed (${response.status.value})."
        if (response.status.value == 403 && message.contains("multi-factor", ignoreCase = true)) {
            throw MfaSetupRequiredException(message)
        }
        throw ApiException(message, response.status.value)
    }

    private suspend fun decodeUnit(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val message = runCatching { response.body<AdminEnvelope<Unit>>().message }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "Request failed (${response.status.value})."
        throw ApiException(message, response.status.value)
    }

    // --- Auth ---

    /** Pass [otp] only on the retry after an [MfaCodeRequiredException]. */
    suspend fun login(email: String, password: String, otp: String? = null): AdminAccount {
        val body = buildMap {
            put("email", email)
            put("password", password)
            if (otp != null) put("otp", otp)
        }
        val response = client.post("$baseUrl/admin/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            val message = runCatching { response.body<AdminEnvelope<Unit>>().message }.getOrNull()
                ?: runCatching { response.body<Map<String, String>>()["message"] }.getOrNull()
                ?: "Sign-in failed."
            if (response.status.value == 422 && message.contains("otp", ignoreCase = true)) {
                throw MfaCodeRequiredException("Enter the 6-digit code from your authenticator app.")
            }
            throw ApiException(message, response.status.value)
        }
        return response.body<AdminEnvelope<AdminAccountWrapper>>().data!!.admin
    }

    suspend fun logout() = decodeUnit(client.post("$baseUrl/admin/auth/logout"))

    suspend fun me(): AdminAccount =
        decode(client.get("$baseUrl/admin/auth/me")) { it.body<AdminEnvelope<AdminAccountWrapper>>().let { e -> AdminEnvelope(e.success, e.message, e.data?.admin) } }

    suspend fun setupMfa(): AdminMfaSetup {
        val response = client.post("$baseUrl/admin/auth/setup-mfa")
        return decode(response) { it.body() }
    }

    suspend fun enableMfa(otp: String) {
        val response = client.post("$baseUrl/admin/auth/enable-mfa") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("otp" to otp))
        }
        decodeUnit(response)
    }

    suspend fun disableMfa(password: String) {
        val response = client.post("$baseUrl/admin/auth/disable-mfa") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("password" to password))
        }
        decodeUnit(response)
    }

    // --- Elections ---

    suspend fun listElections(): List<Election> = decode(client.get("$baseUrl/admin/elections")) { it.body() }

    suspend fun createElection(name: String, electionDate: String): Election =
        decode(client.post("$baseUrl/admin/elections") {
            contentType(ContentType.Application.Json)
            setBody(CreateElectionRequest(name, electionDate))
        }) { it.body() }

    suspend fun updateElection(id: String, name: String, electionDate: String) {
        decodeUnit(client.patch("$baseUrl/admin/elections/$id") {
            contentType(ContentType.Application.Json)
            setBody(CreateElectionRequest(name, electionDate))
        })
    }

    suspend fun positions(electionId: String): List<Position> = decode(client.get("$baseUrl/admin/elections/$electionId/positions")) { it.body() }

    suspend fun candidates(electionId: String): List<Candidate> = decode(client.get("$baseUrl/admin/elections/$electionId/candidates")) { it.body() }

    suspend fun createPosition(electionId: String, req: CreatePositionRequest) {
        decodeUnit(client.post("$baseUrl/admin/elections/$electionId/positions") {
            contentType(ContentType.Application.Json)
            setBody(req)
        })
    }

    suspend fun deletePosition(positionId: String) {
        decodeUnit(client.delete("$baseUrl/admin/positions/$positionId"))
    }

    /** [photoBytes]/[photoFileName] are optional; pass them to upload a real photo instead of [photoUrl]. */
    suspend fun createCandidate(
        positionId: String,
        name: String,
        photoUrl: String? = null,
        biography: String? = null,
        manifesto: String? = null,
        photoBytes: ByteArray? = null,
        photoFileName: String? = null,
    ) {
        val response = if (photoBytes != null && photoFileName != null) {
            client.post("$baseUrl/admin/positions/$positionId/candidates") {
                setBody(MultiPartFormDataContent(formData {
                    append("name", name)
                    append("photo_url", photoUrl.orEmpty())
                    append("biography", biography.orEmpty())
                    append("manifesto", manifesto.orEmpty())
                    append("photo", photoBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$photoFileName\"")
                    })
                }))
            }
        } else {
            client.post("$baseUrl/admin/positions/$positionId/candidates") {
                contentType(ContentType.Application.Json)
                setBody(CreateCandidateRequest(name, photoUrl.orEmpty(), biography.orEmpty(), manifesto.orEmpty()))
            }
        }
        decodeUnit(response)
    }

    suspend fun approveCandidate(id: String) = decodeUnit(client.patch("$baseUrl/admin/candidates/$id/approve"))
    suspend fun rejectCandidate(id: String) = decodeUnit(client.patch("$baseUrl/admin/candidates/$id/reject"))
    suspend fun disqualifyCandidate(id: String) = decodeUnit(client.patch("$baseUrl/admin/candidates/$id/disqualify"))

    suspend fun setEligibility(electionId: String, entries: List<EligibilityEntry>): AdminEligibilityResult =
        decode(client.post("$baseUrl/admin/elections/$electionId/eligibility") {
            contentType(ContentType.Application.Json)
            setBody(SetEligibilityRequest(entries))
        }) { it.body() }

    suspend fun importStudents(students: List<StudentImportEntry>): AdminStudentImportResult =
        decode(client.post("$baseUrl/admin/students") {
            contentType(ContentType.Application.Json)
            setBody(StudentImportRequest(students))
        }) { it.body() }

    suspend fun turnout(electionId: String): AdminTurnout = decode(client.get("$baseUrl/admin/elections/$electionId/turnout")) { it.body() }

    suspend fun results(electionId: String): AdminResults? {
        val response = client.get("$baseUrl/admin/elections/$electionId/results")
        if (response.status.value == 404) return null
        return decode(response) { it.body() }
    }

    suspend fun publishResults(electionId: String): AdminPublishResult =
        decode(client.post("$baseUrl/admin/elections/$electionId/results/publish")) { it.body() }

    suspend fun audit(): List<AuditEntry> = decode(client.get("$baseUrl/admin/audit")) { it.body() }
}

@kotlinx.serialization.Serializable
private data class AdminAccountWrapper(val admin: AdminAccount)
