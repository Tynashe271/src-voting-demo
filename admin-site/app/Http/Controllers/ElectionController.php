<?php

namespace App\Http\Controllers;

use App\Services\AuditService;
use App\Services\VotingApiClient;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Admin-configuration surface only. Every write here is a thin call into the Go voting backend's
 * internal admin API via VotingApiClient — this controller must never touch a local election/
 * position/candidate model or table, since none exist in this app any more; Go's Postgres schema
 * is the single source of truth. All routes sit behind the `auth:admin` guard (see routes/api.php).
 */
class ElectionController extends Controller
{
    public function __construct(
        protected VotingApiClient $votingApi,
        protected AuditService $auditService,
    ) {
    }

    public function importStudents(Request $request): JsonResponse
    {
        $request->validate([
            'students' => 'required|array|min:1',
            'students.*.registration_number' => 'required|string',
            'students.*.first_name' => 'required|string',
            'students.*.last_name' => 'required|string',
            'students.*.university_email' => 'required|email',
            'students.*.national_id' => 'required|string|min:4|max:64',
            'students.*.faculty' => 'nullable|string',
            'students.*.programme' => 'nullable|string',
            'students.*.level' => 'nullable|string',
        ]);

        $result = $this->votingApi->importStudents($request->input('students'));

        $this->auditService->log('students_imported', 'students', [
            'upserted' => $result['upserted'] ?? 0,
            'failed_count' => count($result['failed'] ?? []),
        ]);

        return response()->json(['success' => true, 'data' => $result]);
    }

    public function store(Request $request): JsonResponse
    {
        $request->validate([
            'name' => 'required|string|max:200',
            'election_date' => 'required|date_format:Y-m-d',
        ]);

        $election = $this->votingApi->createElection($request->string('name'), $request->string('election_date'));

        $this->auditService->logElectionConfiguration('election_created', $election['id'], [
            'name' => $election['name'],
            'election_date' => $election['election_date'],
        ]);

        return response()->json(['success' => true, 'message' => 'Election created successfully.', 'data' => ['election' => $election]], 201);
    }

    public function update(Request $request, string $election): JsonResponse
    {
        $request->validate([
            'name' => 'required|string|max:200',
            'election_date' => 'required|date_format:Y-m-d',
        ]);

        $updated = $this->votingApi->updateElection($election, $request->string('name'), $request->string('election_date'));

        $this->auditService->logElectionConfiguration('election_updated', $election, [
            'name' => $updated['name'],
            'election_date' => $updated['election_date'],
        ]);

        return response()->json(['success' => true, 'message' => 'Election updated successfully.', 'data' => ['election' => $updated]]);
    }

    public function createPosition(Request $request, string $election): JsonResponse
    {
        $request->validate([
            'name' => 'required|string|max:120',
            'description' => 'nullable|string',
            'display_order' => 'required|integer|min:1',
            'max_selections' => 'required|integer|min:1',
        ]);

        $position = $this->votingApi->createPosition($election, $request->string('name'), $request->input('description'), (int) $request->input('display_order'), (int) $request->input('max_selections'));

        $this->auditService->logElectionConfiguration('position_created', $election, [
            'position_id' => $position['id'],
            'position_name' => $position['name'],
        ]);

        return response()->json(['success' => true, 'message' => 'Position created successfully.', 'data' => ['position' => $position]], 201);
    }

    public function updatePosition(Request $request, string $position): JsonResponse
    {
        $request->validate([
            'name' => 'required|string|max:120',
            'description' => 'nullable|string',
            'display_order' => 'required|integer|min:1',
            'max_selections' => 'required|integer|min:1',
        ]);

        $this->votingApi->updatePosition($position, $request->string('name'), $request->input('description'), (int) $request->input('display_order'), (int) $request->input('max_selections'));

        $this->auditService->logElectionConfiguration('position_updated', $position, $request->only(['name', 'description', 'display_order', 'max_selections']));

        return response()->json(['success' => true, 'message' => 'Position updated successfully.']);
    }

    public function deletePosition(string $position): JsonResponse
    {
        $this->votingApi->deletePosition($position);

        $this->auditService->logElectionConfiguration('position_deleted', $position, []);

        return response()->json(['success' => true, 'message' => 'Position removed successfully.']);
    }

    public function createCandidate(Request $request, string $position): JsonResponse
    {
        $request->validate([
            'name' => 'required|string|max:200',
            'photo_url' => 'nullable|string',
            'biography' => 'nullable|string',
            'manifesto' => 'nullable|string',
        ]);

        $candidate = $this->votingApi->createCandidate($position, $request->string('name'), $request->input('photo_url'), $request->input('biography'), $request->input('manifesto'));

        $this->auditService->logCandidateManagement('candidate_created', $candidate['id'], [
            'position_id' => $position,
            'candidate_name' => $candidate['name'],
        ]);

        return response()->json(['success' => true, 'message' => 'Candidate created successfully.', 'data' => ['candidate' => $candidate]], 201);
    }

    public function updateCandidate(Request $request, string $candidate): JsonResponse
    {
        $request->validate([
            'name' => 'required|string|max:200',
            'photo_url' => 'nullable|string',
            'biography' => 'nullable|string',
            'manifesto' => 'nullable|string',
        ]);

        $this->votingApi->updateCandidate($candidate, $request->string('name'), $request->input('photo_url'), $request->input('biography'), $request->input('manifesto'));

        $this->auditService->logCandidateManagement('candidate_updated', $candidate, $request->only(['name', 'photo_url', 'biography', 'manifesto']));

        return response()->json(['success' => true, 'message' => 'Candidate updated successfully.']);
    }

    public function approveCandidate(string $candidate): JsonResponse
    {
        $this->votingApi->approveCandidate($candidate);

        $this->auditService->logCandidateManagement('candidate_approved', $candidate, []);

        return response()->json(['success' => true, 'message' => 'Candidate approved successfully.']);
    }

    public function rejectCandidate(string $candidate): JsonResponse
    {
        $this->votingApi->rejectCandidate($candidate);

        $this->auditService->logCandidateManagement('candidate_rejected', $candidate, []);

        return response()->json(['success' => true, 'message' => 'Candidate rejected successfully.']);
    }

    public function setEligibility(Request $request, string $election): JsonResponse
    {
        $request->validate([
            'entries' => 'required|array|min:1',
            'entries.*.registration_number' => 'required|string',
            'entries.*.eligible' => 'required|boolean',
        ]);

        $result = $this->votingApi->setEligibility($election, $request->input('entries'));

        $this->auditService->logVoterEligibility('eligibility_updated', $election, [
            'updated' => $result['updated'] ?? 0,
            'failed_count' => count($result['failed'] ?? []),
        ]);

        return response()->json(['success' => true, 'data' => $result]);
    }
}
