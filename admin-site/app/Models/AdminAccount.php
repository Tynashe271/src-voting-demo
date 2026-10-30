<?php

namespace App\Models;

use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;

/**
 * Maps to the admin_accounts table Go's migrations own on the shared `voting` Postgres connection
 * (see migrations/001_initial.sql and migrations/004_admin_accounts_staff_fields.sql at the repo
 * root). Go's own application code never reads or writes this table; Laravel is its only client.
 * There is deliberately no migration for it in this app — see
 * app/Console/Commands/CreateAdminAccountCommand.php for how the first row is provisioned.
 */
class AdminAccount extends Authenticatable
{
    use Notifiable;

    protected $connection = 'voting';

    protected $table = 'admin_accounts';

    public $incrementing = false;

    protected $keyType = 'string';

    public $timestamps = false;

    protected $fillable = [
        'mfa_enabled',
        'mfa_secret',
    ];

    protected $hidden = [
        'password_hash',
        'mfa_secret',
    ];

    protected $casts = [
        'mfa_enabled' => 'boolean',
        'account_status' => 'string',
    ];

    public function getAuthPassword()
    {
        return $this->password_hash;
    }
}
