<?php

namespace Tests\Feature;

use App\Exceptions\VotingApiException;
use App\Services\VotingApiClient;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class VotingApiClientTest extends TestCase
{
    protected function client(): VotingApiClient
    {
        config(['services.voting_api.base_url' => 'http://go-api.test/api/v1/admin', 'services.voting_api.key' => 'test-service-key']);

        return app(VotingApiClient::class);
    }

    public function test_import_students_sends_expected_request(): void
    {
        Http::fake(['go-api.test/*' => Http::response(['upserted' => 1, 'failed' => []], 200)]);

        $result = $this->client()->importStudents([
            ['registration_number' => 'R100', 'first_name' => 'Jane', 'last_name' => 'Doe', 'university_email' => 'jane@students.university.ac.zw', 'national_id' => '63-123456A78'],
        ]);

        $this->assertSame(1, $result['upserted']);
        Http::assertSent(function ($request) {
            return $request->url() === 'http://go-api.test/api/v1/admin/students'
                && $request->method() === 'POST'
                && $request->hasHeader('X-Internal-Admin-Key', 'test-service-key')
                && $request['students'][0]['registration_number'] === 'R100';
        });
    }

    public function test_create_election_sends_expected_request(): void
    {
        Http::fake(['go-api.test/*' => Http::response(['id' => 'election-1', 'name' => 'SRC 2026', 'election_date' => '2026-10-01'], 201)]);

        $election = $this->client()->createElection('SRC 2026', '2026-10-01');

        $this->assertSame('election-1', $election['id']);
        Http::assertSent(fn ($request) => $request->url() === 'http://go-api.test/api/v1/admin/elections'
            && $request->method() === 'POST'
            && $request['name'] === 'SRC 2026'
            && $request['election_date'] === '2026-10-01');
    }

    public function test_update_election_uses_patch(): void
    {
        Http::fake(['go-api.test/*' => Http::response(['id' => 'election-1'], 200)]);

        $this->client()->updateElection('election-1', 'SRC 2026 (corrected)', '2026-10-02');

        Http::assertSent(fn ($request) => $request->url() === 'http://go-api.test/api/v1/admin/elections/election-1'
            && $request->method() === 'PATCH');
    }

    public function test_create_position_sends_expected_payload(): void
    {
        Http::fake(['go-api.test/*' => Http::response(['id' => 'position-1'], 201)]);

        $this->client()->createPosition('election-1', 'President', 'Head of SRC', 1, 1);

        Http::assertSent(fn ($request) => $request->url() === 'http://go-api.test/api/v1/admin/elections/election-1/positions'
            && $request['name'] === 'President'
            && $request['display_order'] === 1
            && $request['max_selections'] === 1);
    }

    public function test_delete_position_uses_delete(): void
    {
        Http::fake(['go-api.test/*' => Http::response(null, 204)]);

        $this->client()->deletePosition('position-1');

        Http::assertSent(fn ($request) => $request->url() === 'http://go-api.test/api/v1/admin/positions/position-1'
            && $request->method() === 'DELETE');
    }

    public function test_approve_and_reject_candidate_hit_distinct_routes(): void
    {
        Http::fake(['go-api.test/*' => Http::response(null, 204)]);

        $this->client()->approveCandidate('candidate-1');
        $this->client()->rejectCandidate('candidate-2');

        Http::assertSent(fn ($request) => str_ends_with($request->url(), '/candidates/candidate-1/approve'));
        Http::assertSent(fn ($request) => str_ends_with($request->url(), '/candidates/candidate-2/reject'));
    }

    public function test_set_eligibility_sends_entries(): void
    {
        Http::fake(['go-api.test/*' => Http::response(['updated' => 2, 'failed' => []], 200)]);

        $result = $this->client()->setEligibility('election-1', [
            ['registration_number' => 'R100', 'eligible' => true],
            ['registration_number' => 'R101', 'eligible' => false],
        ]);

        $this->assertSame(2, $result['updated']);
        Http::assertSent(fn ($request) => $request['entries'][1]['registration_number'] === 'R101');
    }

    public function test_turnout_results_timing_and_audit_are_gets(): void
    {
        Http::fake([
            '*/turnout' => Http::response(['eligible_students' => 10, 'votes_cast' => 4, 'turnout_percent' => 40.0]),
            '*/results' => Http::response(['status' => 'SEALED', 'totals' => [], 'integrity_hash' => 'abc']),
            '*/timing' => Http::response(['status' => 'OPEN']),
            '*/audit' => Http::response([['id' => 1, 'action' => 'BALLOT_ACCEPTED']]),
        ]);

        $this->assertEquals(40.0, $this->client()->turnout('election-1')['turnout_percent']);
        $this->assertSame('abc', $this->client()->results('election-1')['integrity_hash']);
        $this->assertSame('OPEN', $this->client()->timing('election-1')['status']);
        $this->assertSame('BALLOT_ACCEPTED', $this->client()->audit()[0]['action']);
    }

    public function test_failed_response_raises_voting_api_exception_with_upstream_status_and_message(): void
    {
        Http::fake(['go-api.test/*' => Http::response(['success' => false, 'message' => 'Voting is not currently open.'], 409)]);

        try {
            $this->client()->setEligibility('election-1', [['registration_number' => 'R100', 'eligible' => true]]);
            $this->fail('Expected a VotingApiException to be thrown.');
        } catch (VotingApiException $e) {
            $this->assertSame(409, $e->status);
            $this->assertSame('Voting is not currently open.', $e->getMessage());
        }
    }

    public function test_never_sends_key_to_a_third_party_host(): void
    {
        Http::fake(['*' => Http::response([])]);

        $this->client()->audit();

        Http::assertSent(fn ($request) => str_starts_with($request->url(), 'http://go-api.test/'));
    }
}
