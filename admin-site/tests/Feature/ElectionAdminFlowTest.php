<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\Http;
use Tests\Concerns\PreparesVotingConnection;
use Tests\TestCase;

class ElectionAdminFlowTest extends TestCase
{
    use PreparesVotingConnection;

    protected function setUp(): void
    {
        parent::setUp();
        $this->setUpVotingSchema();
    }

    protected function actingAsAdmin(): void
    {
        $this->actingAs($this->createAdminAccount(), 'admin');
    }

    public function test_create_election_calls_go_and_never_touches_a_local_election_table(): void
    {
        $this->actingAsAdmin();
        Http::fake(['*/elections' => Http::response(['id' => 'election-1', 'name' => 'SRC 2026', 'election_date' => '2026-10-01', 'opens_at' => '2026-10-01T06:00:00Z', 'closes_at' => '2026-10-01T23:59:00Z'], 201)]);

        $response = $this->postJson('/api/admin/elections', ['name' => 'SRC 2026', 'election_date' => '2026-10-01']);

        $response->assertCreated()->assertJsonPath('data.election.id', 'election-1');
        Http::assertSent(fn ($request) => str_ends_with($request->url(), '/elections') && $request->hasHeader('X-Internal-Admin-Key'));
    }

    public function test_create_position_and_candidate_flow(): void
    {
        $this->actingAsAdmin();
        Http::fake([
            '*/positions' => Http::response(['id' => 'position-1', 'election_id' => 'election-1', 'name' => 'President'], 201),
            '*/candidates' => Http::response(['id' => 'candidate-1', 'position_id' => 'position-1', 'name' => 'Jane Doe', 'approval_status' => 'PENDING'], 201),
        ]);

        $this->postJson('/api/admin/elections/election-1/positions', [
            'name' => 'President', 'display_order' => 1, 'max_selections' => 1,
        ])->assertCreated()->assertJsonPath('data.position.id', 'position-1');

        $this->postJson('/api/admin/positions/position-1/candidates', [
            'name' => 'Jane Doe',
        ])->assertCreated()->assertJsonPath('data.candidate.id', 'candidate-1');
    }

    public function test_approve_candidate(): void
    {
        $this->actingAsAdmin();
        Http::fake(['*/candidates/candidate-1/approve' => Http::response(null, 204)]);

        $this->patchJson('/api/admin/candidates/candidate-1/approve')->assertOk()->assertJson(['success' => true]);
    }

    public function test_go_rejection_surfaces_as_the_same_status_and_message(): void
    {
        $this->actingAsAdmin();
        Http::fake(['*/positions' => Http::response(['success' => false, 'message' => 'Position could not be created. The election may already be open, or the name/order may be in use.'], 409)]);

        $response = $this->postJson('/api/admin/elections/election-1/positions', [
            'name' => 'President', 'display_order' => 1, 'max_selections' => 1,
        ]);

        $response->assertStatus(409)->assertJson(['success' => false, 'message' => 'Position could not be created. The election may already be open, or the name/order may be in use.']);
    }

    public function test_set_eligibility_and_import_students(): void
    {
        $this->actingAsAdmin();
        Http::fake([
            '*/students' => Http::response(['upserted' => 1, 'failed' => []], 200),
            '*/eligibility' => Http::response(['updated' => 1, 'failed' => []], 200),
        ]);

        $this->postJson('/api/admin/students', ['students' => [[
            'registration_number' => 'R100', 'first_name' => 'Jane', 'last_name' => 'Doe', 'university_email' => 'jane@students.university.ac.zw', 'national_id' => '63-123456A78',
        ]]])->assertOk()->assertJsonPath('data.upserted', 1);

        $this->postJson('/api/admin/elections/election-1/eligibility', [
            'entries' => [['registration_number' => 'R100', 'eligible' => true]],
        ])->assertOk()->assertJsonPath('data.updated', 1);
    }

    public function test_turnout_results_and_audit_reads(): void
    {
        $this->actingAsAdmin();
        Http::fake([
            '*/turnout' => Http::response(['eligible_students' => 10, 'votes_cast' => 3, 'turnout_percent' => 30]),
            '*/results' => Http::response(['status' => 'SEALED', 'totals' => [], 'integrity_hash' => 'abc']),
            '*/audit' => Http::response([['id' => 1, 'action' => 'BALLOT_ACCEPTED']]),
        ]);

        $this->getJson('/api/admin/elections/election-1/turnout')->assertOk()->assertJsonPath('data.votes_cast', 3);
        $this->getJson('/api/admin/elections/election-1/results')->assertOk()->assertJsonPath('data.integrity_hash', 'abc');
        $this->getJson('/api/admin/audit')->assertOk()->assertJsonPath('data.0.action', 'BALLOT_ACCEPTED');
    }

    public function test_config_writes_are_rejected_without_admin_session(): void
    {
        $this->postJson('/api/admin/elections', ['name' => 'SRC 2026', 'election_date' => '2026-10-01'])
            ->assertStatus(401);
    }
}
