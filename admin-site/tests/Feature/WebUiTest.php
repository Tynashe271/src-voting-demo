<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\Http;
use Tests\Concerns\PreparesVotingConnection;
use Tests\TestCase;

class WebUiTest extends TestCase
{
    use PreparesVotingConnection;

    protected function setUp(): void
    {
        parent::setUp();
        $this->setUpVotingSchema();
    }

    public function test_guest_visiting_dashboard_is_redirected_to_login(): void
    {
        $this->get('/')->assertRedirect('/login');
    }

    public function test_login_page_renders(): void
    {
        $this->get('/login')->assertOk()->assertSee('SRC Elections Admin');
    }

    public function test_login_without_mfa_redirects_to_dashboard(): void
    {
        $this->createAdminAccount(['email' => 'staff@example.com', 'mfa_enabled' => false]);

        $response = $this->from('/login')->post('/login', [
            'email' => 'staff@example.com',
            'password' => 'correct-horse-battery-staple',
        ]);

        $response->assertRedirect(route('dashboard'));
        $this->assertAuthenticated('admin');
    }

    public function test_login_with_wrong_password_redirects_back_with_error(): void
    {
        $this->createAdminAccount(['email' => 'staff@example.com', 'mfa_enabled' => false]);

        $response = $this->from('/login')->post('/login', [
            'email' => 'staff@example.com',
            'password' => 'wrong',
        ]);

        $response->assertRedirect('/login')->assertSessionHas('error');
        $this->assertGuest('admin');
    }

    public function test_login_with_mfa_enabled_requires_second_step(): void
    {
        $this->createAdminAccount(['email' => 'staff@example.com', 'mfa_enabled' => true]);

        $response = $this->post('/login', [
            'email' => 'staff@example.com',
            'password' => 'correct-horse-battery-staple',
        ]);

        $response->assertRedirect(route('login.mfa'));
        $this->assertGuest('admin');
    }

    public function test_dashboard_lists_elections_from_go(): void
    {
        Http::fake(['*/elections' => Http::response([
            ['id' => 'election-1', 'name' => 'SRC 2026', 'election_date' => '2026-10-01', 'status' => 'UPCOMING'],
        ], 200)]);

        $this->actingAs($this->createAdminAccount(), 'admin')
            ->get('/')
            ->assertOk()
            ->assertSee('SRC 2026');
    }

    public function test_create_election_form_submits_to_go(): void
    {
        Http::fake(['*/elections' => Http::response(['id' => 'election-1', 'name' => 'SRC 2026', 'election_date' => '2026-10-01'], 201)]);

        $response = $this->actingAs($this->createAdminAccount(), 'admin')
            ->post('/elections', ['name' => 'SRC 2026', 'election_date' => '2026-10-01']);

        $response->assertRedirect(route('elections.show', 'election-1'));
        Http::assertSent(fn ($request) => str_ends_with($request->url(), '/elections') && $request->method() === 'POST');
    }

    public function test_election_show_page_renders_positions_and_candidates(): void
    {
        Http::fake([
            '*/elections' => Http::response([['id' => 'election-1', 'name' => 'SRC 2026', 'election_date' => '2026-10-01', 'status' => 'UPCOMING']]),
            '*/elections/election-1/positions' => Http::response([
                ['id' => 'position-1', 'name' => 'President', 'description' => null, 'display_order' => 1, 'max_selections' => 1],
            ]),
            '*/elections/election-1/candidates' => Http::response([
                ['id' => 'candidate-1', 'position_id' => 'position-1', 'name' => 'Jane Doe', 'approval_status' => 'PENDING'],
            ]),
            '*/turnout' => Http::response(['success' => false, 'message' => 'not open'], 404),
            '*/results' => Http::response(['success' => false, 'message' => 'not available'], 404),
        ]);

        $this->actingAs($this->createAdminAccount(), 'admin')
            ->get('/elections/election-1')
            ->assertOk()
            ->assertSee('President')
            ->assertSee('Jane Doe')
            ->assertSee('PENDING');
    }

    public function test_approve_candidate_via_web_form(): void
    {
        Http::fake(['*/candidates/candidate-1/approve' => Http::response(null, 204)]);

        $response = $this->actingAs($this->createAdminAccount(), 'admin')
            ->post('/elections/election-1/candidates/candidate-1/approve');

        $response->assertRedirect();
        Http::assertSent(fn ($request) => str_ends_with($request->url(), '/candidates/candidate-1/approve'));
    }

    public function test_security_page_offers_mfa_setup_when_disabled(): void
    {
        $this->actingAs($this->createAdminAccount(['mfa_enabled' => false]), 'admin')
            ->get('/account/security')
            ->assertOk()
            ->assertSee('Set up MFA');
    }

    public function test_logout_clears_session(): void
    {
        $this->actingAs($this->createAdminAccount(), 'admin');
        $this->assertAuthenticated('admin');

        $this->post('/logout')->assertRedirect('/login');
        $this->assertGuest('admin');
    }
}
