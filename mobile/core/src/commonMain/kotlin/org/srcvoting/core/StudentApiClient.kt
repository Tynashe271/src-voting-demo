package org.srcvoting.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Talks directly to the live Go voting API over HTTPS, the same public/student surface the
 * official student-site JS already calls from any browser -- nothing here needs a secret, so a
 * shipped mobile/desktop binary embedding this base URL is exactly as safe as the public website.
 * Session auth works the same way too: a persistent cookie jar (see createHttpClient) carries the
 * src_session cookie, and every mutating request echoes the src_csrf cookie back as a header,
 * matching the API's CSRF double-submit check (cmd/api/main.go's csrf() middleware).
 */
class StudentApiClient(
    private val baseUrl: String = "https://src-voting-api-demo.onrender.com/api/v1",
    private val client: HttpClient = createHttpClient(),
) {
    private suspend fun csrfHeaderValue(): String? {
        val cookies = client.cookies(Url(baseUrl))
        return cookies.firstOrNull { it.name == "src_csrf" }?.value
    }

    private suspend fun <T> decode(response: HttpResponse, deserialize: suspend (HttpResponse) -> T): T {
        if (response.status.isSuccess()) return deserialize(response)
        val message = runCatching { response.body<ApiMessage>().message }.getOrNull()
            ?: "Request failed (${response.status.value})."
        throw ApiException(message, response.status.value)
    }

    // --- Auth ---

    suspend fun login(registrationNumber: String, password: String): LoginResponse {
        val response = client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("registration_number" to registrationNumber, "password" to password))
        }
        return decode(response) { it.body() }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val response = client.post("$baseUrl/auth/change-password") {
            contentType(ContentType.Application.Json)
            header("X-CSRF-Token", csrfHeaderValue())
            setBody(mapOf("current_password" to currentPassword, "new_password" to newPassword))
        }
        decode(response) {}
    }

    suspend fun logout() {
        val response = client.post("$baseUrl/auth/logout") {
            header("X-CSRF-Token", csrfHeaderValue())
        }
        decode(response) {}
    }

    suspend fun me(): StudentProfile {
        val response = client.get("$baseUrl/auth/me")
        return decode(response) { it.body() }
    }

    // --- Dashboard / profile ---

    suspend fun dashboard(): DashboardResponse {
        val response = client.get("$baseUrl/dashboard")
        return decode(response) { it.body() }
    }

    suspend fun myEligibility(): List<Election> {
        val response = client.get("$baseUrl/students/me/eligibility")
        return decode(response) { it.body() }
    }

    // --- Elections / candidates (public reads) ---

    suspend fun currentElection(): Election {
        val response = client.get("$baseUrl/elections/current")
        return decode(response) { it.body() }
    }

    suspend fun election(id: String): Election {
        val response = client.get("$baseUrl/elections/$id")
        return decode(response) { it.body() }
    }

    suspend fun positions(electionId: String): List<Position> {
        val response = client.get("$baseUrl/elections/$electionId/positions")
        return decode(response) { it.body() }
    }

    suspend fun candidates(electionId: String): List<Candidate> {
        val response = client.get("$baseUrl/elections/$electionId/candidates")
        return decode(response) { it.body() }
    }

    // --- Voting ---

    suspend fun eligibility(electionId: String): EligibilityResponse {
        val response = client.get("$baseUrl/elections/$electionId/eligibility")
        return decode(response) { it.body() }
    }

    suspend fun submitBallot(electionId: String, selections: List<BallotSelection>): BallotSubmitResponse {
        val response = client.post("$baseUrl/elections/$electionId/ballots") {
            contentType(ContentType.Application.Json)
            header("X-CSRF-Token", csrfHeaderValue())
            setBody(BallotRequest(selections))
        }
        return decode(response) { it.body() }
    }

    suspend fun receipt(electionId: String): ReceiptResponse {
        val response = client.get("$baseUrl/elections/$electionId/receipt")
        return decode(response) { it.body() }
    }

    suspend fun results(electionId: String): ResultsResponse {
        val response = client.get("$baseUrl/elections/$electionId/results")
        return decode(response) { it.body() }
    }

    // --- Notifications ---

    suspend fun notifications(): List<Notification> {
        val response = client.get("$baseUrl/notifications")
        return decode(response) { it.body() }
    }

    suspend fun markNotificationRead(id: String) {
        val response = client.patch("$baseUrl/notifications/$id/read") {
            header("X-CSRF-Token", csrfHeaderValue())
        }
        decode(response) {}
    }
}
