<?php

namespace Tests\Concerns;

use App\Models\AdminAccount;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

/**
 * Builds a throwaway schema on the `voting` connection mirroring the two tables Go's migrations
 * own in production (admin_accounts, admin_audit_logs — see migrations/004 at the repo root), so
 * admin-auth feature tests can run without a live Postgres instance. phpunit.xml points the
 * `voting` connection at an in-memory sqlite database for exactly this purpose. Production never
 * runs a Laravel migration against this connection — this trait is test-only.
 */
trait PreparesVotingConnection
{
    protected function setUpVotingSchema(): void
    {
        $schema = Schema::connection('voting');
        $schema->dropIfExists('admin_audit_logs');
        $schema->dropIfExists('admin_accounts');

        $schema->create('admin_accounts', function ($table) {
            $table->uuid('id')->primary();
            $table->string('email')->unique();
            $table->string('password_hash');
            $table->boolean('mfa_enabled')->default(true);
            $table->string('account_status')->default('ACTIVE');
            $table->string('staff_identifier')->unique()->nullable();
            $table->text('mfa_secret')->nullable();
        });

        $schema->create('admin_audit_logs', function ($table) {
            $table->id();
            $table->string('actor_service');
            $table->string('action');
            $table->string('resource');
            $table->json('metadata')->default('{}');
            $table->timestamp('created_at')->useCurrent();
        });
    }

    protected function createAdminAccount(array $attributes = []): AdminAccount
    {
        $account = new AdminAccount();
        $account->setConnection('voting');
        $account->forceFill(array_merge([
            'id' => (string) Str::uuid(),
            'email' => 'staff@example.com',
            'password_hash' => Hash::make('correct-horse-battery-staple'),
            // Default true: RequireAdminMfa now blocks everything but /account/security for an
            // un-enrolled admin, so a test exercising ordinary admin actions needs an already-
            // enrolled account. Pass ['mfa_enabled' => false] explicitly for a test that's actually
            // about the not-yet-enrolled state (e.g. the security page's own setup prompt).
            'mfa_enabled' => true,
            'account_status' => 'ACTIVE',
            'staff_identifier' => 'staff-001',
        ], $attributes));
        $account->save();

        return $account;
    }
}
