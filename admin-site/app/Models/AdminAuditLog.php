<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

/**
 * Maps to the admin_audit_logs table Go's migrations own on the shared `voting` Postgres
 * connection (migrations/004_admin_accounts_staff_fields.sql at the repo root). This is a plain,
 * NOT tamper-evident log of admin-portal UI actions (login, MFA setup, which endpoint was called).
 * Ballot/result integrity has its own separate, hash-chained trail in Go's own `audit_logs` table
 * (read-only via VotingApiClient::audit()) — never write ballot- or result-related entries here.
 */
class AdminAuditLog extends Model
{
    protected $connection = 'voting';

    protected $table = 'admin_audit_logs';

    public $timestamps = false;

    protected $fillable = [
        'actor_service',
        'action',
        'resource',
        'metadata',
        'created_at',
    ];

    protected $casts = [
        'metadata' => 'array',
        'created_at' => 'datetime',
    ];
}
