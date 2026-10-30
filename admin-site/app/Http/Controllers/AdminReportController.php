<?php

namespace App\Http\Controllers;

use App\Services\AuditService;
use App\Services\VotingApiClient;
use Illuminate\Http\JsonResponse;

/**
 * Read-only reporting surface: turnout, sealed results, election timing, and the ballot/result
 * integrity audit trail, all proxied from the Go voting backend. Go's result response already
 * carries the SHA-256 integrity hash, so "verification" here is just displaying what Go returns —
 * there is no local recomputation of vote totals or hashes anywhere in this app.
 */
class AdminReportController extends Controller
{
    public function __construct(
        protected VotingApiClient $votingApi,
        protected AuditService $auditService,
    ) {
    }

    public function turnout(string $election): JsonResponse
    {
        return response()->json(['success' => true, 'data' => $this->votingApi->turnout($election)]);
    }

    public function results(string $election): JsonResponse
    {
        $results = $this->votingApi->results($election);

        $this->auditService->logResultAccess($election);

        return response()->json(['success' => true, 'data' => $results]);
    }

    public function timing(string $election): JsonResponse
    {
        return response()->json(['success' => true, 'data' => $this->votingApi->timing($election)]);
    }

    public function audit(): JsonResponse
    {
        return response()->json(['success' => true, 'data' => $this->votingApi->audit()]);
    }
}
