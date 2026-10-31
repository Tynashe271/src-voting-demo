<?php

namespace App\Services;

use App\Exceptions\VotingApiException;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\Client\PendingRequest;
use Illuminate\Http\Client\Response;
use Illuminate\Support\Facades\Http;

/**
 * Thin typed client for the Go voting backend's internal admin API (/api/v1/admin/*). Every
 * election/position/candidate/eligibility/turnout/results/audit operation goes through here —
 * this app must never write to the voting domain any other way. The X-Internal-Admin-Key header
 * is a service-to-service secret; it is attached here and only here, and must never reach a
 * browser response or a log line.
 */
class VotingApiClient
{
    protected function client(): PendingRequest
    {
        return Http::baseUrl(rtrim(config('services.voting_api.base_url'), '/'))
            ->withHeaders(['X-Internal-Admin-Key' => config('services.voting_api.key')])
            ->acceptJson()
            ->timeout(20)
            // The Go backend runs on a free-tier host that spins down when idle; the first
            // request after a gap gets a 502 from the platform's proxy while it boots back up
            // (seen live: ~10s cold start). Retry transient/gateway failures with backoff instead
            // of surfacing that boot window to staff as "the voting backend rejected the request."
            ->retry(5, 2000, function (\Exception $exception) {
                return $exception instanceof ConnectionException
                    || in_array($exception->response?->status(), [502, 503, 504], true);
            }, throw: false);
    }

    protected function decode(Response $response): array
    {
        if ($response->failed()) {
            throw new VotingApiException($response->status(), $response->json('message') ?? 'The voting backend rejected the request.');
        }

        return $response->json() ?? [];
    }

    /**
     * Also provisions each student's login in the same call: registration_number is the username,
     * national_id becomes the password (there is no separate self-registration step).
     *
     * @param array<int,array{registration_number:string,first_name:string,last_name:string,university_email:string,national_id:string,faculty?:string,programme?:string,level?:string}> $students
     * @return array{upserted:int,failed:array<int,array{registration_number:string,reason:string}>}
     */
    public function importStudents(array $students): array
    {
        return $this->decode($this->client()->post('/students', ['students' => $students]));
    }

    /** @return array<int,array{id:string,name:string,election_date:string,opens_at:string,closes_at:string,status:string,results_visible_from:?string,results_visible_until:?string}> */
    public function listElections(): array
    {
        return $this->decode($this->client()->get('/elections'));
    }

    /** @return array{id:string,name:string,election_date:string,opens_at:string,closes_at:string} */
    public function createElection(string $name, string $electionDate): array
    {
        return $this->decode($this->client()->post('/elections', ['name' => $name, 'election_date' => $electionDate]));
    }

    /** @return array{id:string,name:string,election_date:string,opens_at:string,closes_at:string} */
    public function updateElection(string $electionId, string $name, string $electionDate): array
    {
        return $this->decode($this->client()->patch("/elections/{$electionId}", ['name' => $name, 'election_date' => $electionDate]));
    }

    /** @return array{id:string,election_id:string,name:string,description:string,display_order:int,max_selections:int} */
    public function createPosition(string $electionId, string $name, ?string $description, int $displayOrder, int $maxSelections): array
    {
        return $this->decode($this->client()->post("/elections/{$electionId}/positions", [
            'name' => $name, 'description' => $description, 'display_order' => $displayOrder, 'max_selections' => $maxSelections,
        ]));
    }

    public function updatePosition(string $positionId, string $name, ?string $description, int $displayOrder, int $maxSelections): void
    {
        $this->decode($this->client()->patch("/positions/{$positionId}", [
            'name' => $name, 'description' => $description, 'display_order' => $displayOrder, 'max_selections' => $maxSelections,
        ]));
    }

    public function deletePosition(string $positionId): void
    {
        $this->decode($this->client()->delete("/positions/{$positionId}"));
    }

    /** @return array{id:string,position_id:string,name:string,approval_status:string} */
    public function createCandidate(string $positionId, string $name, ?string $photoUrl, ?string $biography, ?string $manifesto): array
    {
        return $this->decode($this->client()->post("/positions/{$positionId}/candidates", [
            'name' => $name, 'photo_url' => $photoUrl, 'biography' => $biography, 'manifesto' => $manifesto,
        ]));
    }

    public function updateCandidate(string $candidateId, string $name, ?string $photoUrl, ?string $biography, ?string $manifesto): void
    {
        $this->decode($this->client()->patch("/candidates/{$candidateId}", [
            'name' => $name, 'photo_url' => $photoUrl, 'biography' => $biography, 'manifesto' => $manifesto,
        ]));
    }

    public function approveCandidate(string $candidateId): void
    {
        $this->decode($this->client()->patch("/candidates/{$candidateId}/approve"));
    }

    public function rejectCandidate(string $candidateId): void
    {
        $this->decode($this->client()->patch("/candidates/{$candidateId}/reject"));
    }

    public function disqualifyCandidate(string $candidateId): void
    {
        $this->decode($this->client()->patch("/candidates/{$candidateId}/disqualify"));
    }

    /** @return array<int,array{id:string,name:string,description:?string,display_order:int,max_selections:int}> */
    public function positions(string $electionId): array
    {
        return $this->decode($this->client()->get("/elections/{$electionId}/positions"));
    }

    /** Every candidate regardless of approval status, so staff can review PENDING ones. */
    public function candidates(string $electionId): array
    {
        return $this->decode($this->client()->get("/elections/{$electionId}/candidates"));
    }

    /**
     * @param array<int,array{registration_number:string,eligible:bool}> $entries
     * @return array{updated:int,failed:array<int,array{registration_number:string,reason:string}>}
     */
    public function setEligibility(string $electionId, array $entries): array
    {
        return $this->decode($this->client()->post("/elections/{$electionId}/eligibility", ['entries' => $entries]));
    }

    /** @return array{eligible_students:int,votes_cast:int,turnout_percent:float} */
    public function turnout(string $electionId): array
    {
        return $this->decode($this->client()->get("/elections/{$electionId}/turnout"));
    }

    /** @return array{status:string,totals:array,integrity_hash:string,generated_at:string,visible_until:string} */
    public function results(string $electionId): array
    {
        return $this->decode($this->client()->get("/elections/{$electionId}/results"));
    }

    /**
     * Sealed totals for staff review, visible as soon as the election closes -- before students
     * can see anything, and before results are published. Null (not an exception) when the
     * election hasn't closed/sealed yet, so the results page can render a normal "not ready" state.
     *
     * @return array{status:string,totals:array,integrity_hash:string,generated_at:string,published:bool,published_at:?string}|null
     */
    public function adminResults(string $electionId): ?array
    {
        $response = $this->client()->get("/elections/{$electionId}/results");
        if ($response->status() === 404) {
            return null;
        }

        return $this->decode($response);
    }

    /**
     * The one-time, irreversible act of revealing already-sealed results to students. Throws
     * VotingApiException (409) if the election isn't sealed yet, or if already published.
     *
     * @return array{published:bool,visible_until:string}
     */
    public function publishResults(string $electionId): array
    {
        return $this->decode($this->client()->post("/elections/{$electionId}/results/publish"));
    }

    /** @return array{opens_at:string,closes_at:string,results_visible_from:?string,results_visible_until:?string,status:string} */
    public function timing(string $electionId): array
    {
        return $this->decode($this->client()->get("/elections/{$electionId}/timing"));
    }

    /** @return array<int,array{id:int,actor:string,action:string,resource:string,request_id:?string,previous_hash:?string,entry_hash:string,created_at:string}> */
    public function audit(): array
    {
        return $this->decode($this->client()->get('/audit'));
    }
}
