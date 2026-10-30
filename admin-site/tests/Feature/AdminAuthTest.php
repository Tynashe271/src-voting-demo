<?php

namespace Tests\Feature;

use Tests\Concerns\PreparesVotingConnection;
use Tests\TestCase;

class AdminAuthTest extends TestCase
{
    use PreparesVotingConnection;

    protected function setUp(): void
    {
        parent::setUp();
        $this->setUpVotingSchema();
    }

    public function test_login_succeeds_with_correct_credentials_and_no_mfa(): void
    {
        $this->createAdminAccount(['email' => 'staff@example.com', 'mfa_enabled' => false]);

        $response = $this->postJson('/api/admin/auth/login', [
            'email' => 'staff@example.com',
            'password' => 'correct-horse-battery-staple',
        ]);

        $response->assertOk()->assertJson(['success' => true]);
    }

    public function test_login_rejects_wrong_password(): void
    {
        $this->createAdminAccount(['email' => 'staff@example.com', 'mfa_enabled' => false]);

        $response = $this->postJson('/api/admin/auth/login', [
            'email' => 'staff@example.com',
            'password' => 'wrong-password',
        ]);

        $response->assertStatus(401)->assertJson(['success' => false]);
    }

    public function test_login_rejects_unknown_email(): void
    {
        $response = $this->postJson('/api/admin/auth/login', [
            'email' => 'nobody@example.com',
            'password' => 'anything',
        ]);

        $response->assertStatus(401);
    }

    public function test_login_rejects_inactive_account(): void
    {
        $this->createAdminAccount(['email' => 'staff@example.com', 'mfa_enabled' => false, 'account_status' => 'INACTIVE']);

        $response = $this->postJson('/api/admin/auth/login', [
            'email' => 'staff@example.com',
            'password' => 'correct-horse-battery-staple',
        ]);

        $response->assertStatus(403);
    }

    public function test_login_requires_otp_when_mfa_enabled(): void
    {
        $this->createAdminAccount(['email' => 'staff@example.com', 'mfa_enabled' => true]);

        $response = $this->postJson('/api/admin/auth/login', [
            'email' => 'staff@example.com',
            'password' => 'correct-horse-battery-staple',
        ]);

        $response->assertStatus(422);
    }

    public function test_unauthenticated_admin_routes_are_rejected(): void
    {
        $this->getJson('/api/admin/auth/me')->assertStatus(401);
        $this->getJson('/api/admin/audit')->assertStatus(401);
    }
}
