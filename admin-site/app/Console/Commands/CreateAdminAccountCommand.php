<?php

namespace App\Console\Commands;

use App\Services\PasswordHashService;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use RuntimeException;

/**
 * The only supported way to provision a staff admin account. admin_accounts lives on Go's shared
 * schema and the runtime `voting` connection/role deliberately has no INSERT on it, so this
 * command uses a separate, privileged `voting_provision` connection instead (see
 * config/database.php). Run manually per new hire; never wire this into a web-reachable route.
 */
class CreateAdminAccountCommand extends Command
{
    protected $signature = 'admin:create-account {email} {staff-identifier}';

    protected $description = 'Provision a new staff admin account directly in the shared voting database';

    public function handle(PasswordHashService $passwordHashService): int
    {
        $email = $this->argument('email');
        $staffIdentifier = $this->argument('staff-identifier');

        $existing = DB::connection('voting_provision')->table('admin_accounts')
            ->where('email', $email)->orWhere('staff_identifier', $staffIdentifier)->exists();
        if ($existing) {
            $this->error('An admin account with that email or staff identifier already exists.');

            return self::FAILURE;
        }

        $password = Str::password(20);

        try {
            DB::connection('voting_provision')->table('admin_accounts')->insert([
                'id' => (string) Str::uuid(),
                'email' => $email,
                'password_hash' => $passwordHashService->hash($password),
                'staff_identifier' => $staffIdentifier,
                'mfa_enabled' => false,
                'account_status' => 'ACTIVE',
            ]);
        } catch (RuntimeException|\Throwable $e) {
            $this->error('Could not create the admin account: '.$e->getMessage());

            return self::FAILURE;
        }

        $this->info('Admin account created.');
        $this->line("Email:            {$email}");
        $this->line("Staff identifier: {$staffIdentifier}");
        $this->line("Temporary password: {$password}");
        $this->warn('Share this password with the staff member over a secure channel and ask them to enable MFA immediately after first login. It is not stored anywhere and cannot be recovered.');

        return self::SUCCESS;
    }
}
