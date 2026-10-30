<?php

namespace App\Http\Controllers\Web;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use App\Services\VotingApiClient;
use Illuminate\View\View;

/**
 * Displays both audit trails side by side: Go's hash-chained ballot/result integrity log
 * (read-only, via VotingApiClient) and this app's own admin-portal action log (login, MFA,
 * config-call attempts). They are deliberately separate — see AdminAuditLog's docblock.
 */
class AuditController extends Controller
{
    public function __construct(
        protected VotingApiClient $votingApi,
        protected AuditService $auditService,
    ) {
    }

    public function index(): View
    {
        return view('audit.index', [
            'votingAudit' => $this->votingApi->audit(),
            'adminAudit' => $this->auditService->getRecent(),
        ]);
    }
}
