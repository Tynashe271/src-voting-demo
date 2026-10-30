<?php

namespace App\Services;

use App\Models\AdminAuditLog;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Support\Facades\Auth;

/**
 * Logs admin-portal UI actions (login, MFA setup, which Go endpoint was called) to
 * admin_audit_logs on the shared `voting` connection. This is a plain, not tamper-evident log —
 * ballot/result integrity has its own separate hash-chained trail in Go's own audit_logs table
 * (read via VotingApiClient::audit()). Never route ballot- or result-related entries here.
 */
class AuditService
{
    public function log(string $action, string $resource, array $metadata = []): void
    {
        AdminAuditLog::create([
            'actor_service' => $this->getActor(),
            'action' => $action,
            'resource' => $resource,
            'metadata' => array_merge($metadata, [
                'ip_address' => request()->ip(),
                'user_agent' => request()->userAgent(),
            ]),
            'created_at' => now(),
        ]);
    }

    public function logElectionConfiguration(string $action, string $electionId, array $changes): void
    {
        $this->log($action, "election:{$electionId}", [
            'changes' => $changes,
            'election_id' => $electionId,
        ]);
    }

    public function logCandidateManagement(string $action, string $candidateId, array $details): void
    {
        $this->log($action, "candidate:{$candidateId}", [
            'details' => $details,
            'candidate_id' => $candidateId,
        ]);
    }

    public function logVoterEligibility(string $action, string $electionId, array $details): void
    {
        $this->log($action, "voter_eligibility:{$electionId}", [
            'details' => $details,
            'election_id' => $electionId,
        ]);
    }

    public function logAdminAuth(string $action, string $adminId, array $details = []): void
    {
        $this->log($action, "admin_auth:{$adminId}", array_merge($details, [
            'admin_id' => $adminId,
        ]));
    }

    public function logResultAccess(string $electionId): void
    {
        $this->log('result_access', "election:{$electionId}", [
            'election_id' => $electionId,
        ]);
    }

    public function logSecurityEvent(string $event, array $details): void
    {
        $this->log($event, 'security', [
            'security_event' => true,
            'details' => $details,
        ]);
    }

    protected function getActor(): string
    {
        if (Auth::guard('admin')->check()) {
            $admin = Auth::guard('admin')->user();

            return "admin:{$admin->id}:{$admin->staff_identifier}";
        }

        return 'system:scheduled_task';
    }

    public function getRecent(int $limit = 200): Collection
    {
        return AdminAuditLog::orderBy('created_at', 'desc')->limit($limit)->get();
    }

    public function getLogsForResource(string $resource, int $limit = 100): Collection
    {
        return AdminAuditLog::where('resource', $resource)->orderBy('created_at', 'desc')->limit($limit)->get();
    }

    public function getLogsForActor(string $actor, int $limit = 100): Collection
    {
        return AdminAuditLog::where('actor_service', $actor)->orderBy('created_at', 'desc')->limit($limit)->get();
    }

    public function getRecentSecurityEvents(int $limit = 50): Collection
    {
        return AdminAuditLog::where('resource', 'security')->orderBy('created_at', 'desc')->limit($limit)->get();
    }
}
