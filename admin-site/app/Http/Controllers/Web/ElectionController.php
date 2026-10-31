<?php

namespace App\Http\Controllers\Web;

use App\Exceptions\VotingApiException;
use App\Http\Controllers\Concerns\StoresCandidatePhoto;
use App\Http\Controllers\Controller;
use App\Services\AuditService;
use App\Services\VotingApiClient;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

/**
 * Blade front end for election/position/candidate/eligibility configuration. Every write is a
 * thin call into VotingApiClient (the Go backend), exactly like the JSON API controllers — this
 * class only differs in rendering views and redirecting instead of returning JSON.
 */
class ElectionController extends Controller
{
    use StoresCandidatePhoto;

    public function __construct(
        protected VotingApiClient $votingApi,
        protected AuditService $auditService,
    ) {
    }

    public function index(): View
    {
        return view('elections.index', ['elections' => $this->votingApi->listElections()]);
    }

    public function create(): View
    {
        return view('elections.create');
    }

    public function store(Request $request): RedirectResponse
    {
        $request->validate([
            'name' => 'required|string|max:200',
            'election_date' => 'required|date_format:Y-m-d',
        ]);

        $election = $this->votingApi->createElection($request->string('name'), $request->string('election_date'));

        $this->auditService->logElectionConfiguration('election_created', $election['id'], [
            'name' => $election['name'], 'election_date' => $election['election_date'],
        ]);

        return redirect()->route('elections.show', $election['id'])->with('status', 'Election created.');
    }

    public function show(string $election): View
    {
        $detail = $this->votingApi->listElections();
        $current = collect($detail)->firstWhere('id', $election);

        $positions = $this->votingApi->positions($election);
        $candidates = collect($this->votingApi->candidates($election))->groupBy('position_id');

        $turnout = $this->tryFetch(fn () => $this->votingApi->turnout($election));
        $results = $this->votingApi->adminResults($election);

        return view('elections.show', [
            'election' => $current,
            'electionId' => $election,
            'positions' => $positions,
            'candidatesByPosition' => $candidates,
            'turnout' => $turnout,
            'results' => $results,
        ]);
    }

    public function update(Request $request, string $election): RedirectResponse
    {
        $request->validate([
            'name' => 'required|string|max:200',
            'election_date' => 'required|date_format:Y-m-d',
        ]);

        $this->votingApi->updateElection($election, $request->string('name'), $request->string('election_date'));
        $this->auditService->logElectionConfiguration('election_updated', $election, $request->only(['name', 'election_date']));

        return back()->with('status', 'Election updated.');
    }

    public function storePosition(Request $request, string $election): RedirectResponse
    {
        $request->validate([
            'name' => 'required|string|max:120',
            'description' => 'nullable|string',
            'display_order' => 'required|integer|min:1',
            'max_selections' => 'required|integer|min:1',
        ]);

        $position = $this->votingApi->createPosition($election, $request->string('name'), $request->input('description'), (int) $request->input('display_order'), (int) $request->input('max_selections'));
        $this->auditService->logElectionConfiguration('position_created', $election, ['position_id' => $position['id']]);

        return back()->with('status', 'Position added.');
    }

    public function updatePosition(Request $request, string $election, string $position): RedirectResponse
    {
        $request->validate([
            'name' => 'required|string|max:120',
            'description' => 'nullable|string',
            'display_order' => 'required|integer|min:1',
            'max_selections' => 'required|integer|min:1',
        ]);

        $this->votingApi->updatePosition($position, $request->string('name'), $request->input('description'), (int) $request->input('display_order'), (int) $request->input('max_selections'));
        $this->auditService->logElectionConfiguration('position_updated', $election, ['position_id' => $position]);

        return back()->with('status', 'Position updated.');
    }

    public function destroyPosition(string $election, string $position): RedirectResponse
    {
        $this->votingApi->deletePosition($position);
        $this->auditService->logElectionConfiguration('position_deleted', $election, ['position_id' => $position]);

        return back()->with('status', 'Position removed.');
    }

    public function storeCandidate(Request $request, string $election, string $position): RedirectResponse
    {
        $request->validate([
            'name' => 'required|string|max:200',
            'photo' => 'nullable|image|max:4096',
            'photo_url' => 'nullable|string',
            'biography' => 'nullable|string',
            'manifesto' => 'nullable|string',
        ]);

        $photoUrl = $this->storeCandidatePhoto($request) ?? $request->input('photo_url');

        $candidate = $this->votingApi->createCandidate($position, $request->string('name'), $photoUrl, $request->input('biography'), $request->input('manifesto'));
        $this->auditService->logCandidateManagement('candidate_created', $candidate['id'], ['position_id' => $position]);

        return back()->with('status', 'Candidate added — pending approval.');
    }

    public function updateCandidate(Request $request, string $election, string $candidate): RedirectResponse
    {
        $request->validate([
            'name' => 'required|string|max:200',
            'photo' => 'nullable|image|max:4096',
            'photo_url' => 'nullable|string',
            'biography' => 'nullable|string',
            'manifesto' => 'nullable|string',
        ]);

        $photoUrl = $this->storeCandidatePhoto($request) ?? $request->input('photo_url');

        $this->votingApi->updateCandidate($candidate, $request->string('name'), $photoUrl, $request->input('biography'), $request->input('manifesto'));
        $this->auditService->logCandidateManagement('candidate_updated', $candidate, []);

        return back()->with('status', 'Candidate updated.');
    }

    public function approveCandidate(string $election, string $candidate): RedirectResponse
    {
        $this->votingApi->approveCandidate($candidate);
        $this->auditService->logCandidateManagement('candidate_approved', $candidate, []);

        return back()->with('status', 'Candidate approved.');
    }

    public function rejectCandidate(string $election, string $candidate): RedirectResponse
    {
        $this->votingApi->rejectCandidate($candidate);
        $this->auditService->logCandidateManagement('candidate_rejected', $candidate, []);

        return back()->with('status', 'Candidate rejected.');
    }

    // Unlike approve/reject, this is allowed even after voting has opened -- see
    // migrations/006_candidate_disqualification.sql. Already-cast votes for the candidate are
    // never touched; they're excluded from the sealed tally when the election closes.
    public function disqualifyCandidate(string $election, string $candidate): RedirectResponse
    {
        $this->votingApi->disqualifyCandidate($candidate);
        $this->auditService->logCandidateManagement('candidate_disqualified', $candidate, []);

        return back()->with('status', 'Candidate disqualified. Any votes already cast for them will not be counted.');
    }

    // The one-time, irreversible act of revealing already-sealed results to students. The Go
    // backend enforces that this can only happen once (409 on a repeat call) and only after the
    // election has actually closed and sealed -- this is a thin pass-through, not where the
    // integrity guarantee lives.
    public function publishResults(string $election): RedirectResponse
    {
        $this->votingApi->publishResults($election);
        $this->auditService->logElectionConfiguration('results_published', $election, []);

        return back()->with('status', 'Results published. Students can now see them.');
    }

    public function setEligibility(Request $request, string $election): RedirectResponse
    {
        $request->validate([
            'registration_numbers' => 'required|string',
            'eligible' => 'required|in:1,0',
        ]);

        $entries = collect(preg_split('/\r\n|\r|\n|,/', $request->string('registration_numbers')))
            ->map(fn ($line) => trim($line))
            ->filter()
            ->map(fn ($regNumber) => ['registration_number' => $regNumber, 'eligible' => $request->boolean('eligible')])
            ->values()
            ->all();

        if ($entries === []) {
            return back()->with('error', 'Enter at least one registration number.');
        }

        $result = $this->votingApi->setEligibility($election, $entries);
        $this->auditService->logVoterEligibility('eligibility_updated', $election, ['updated' => $result['updated'] ?? 0]);

        $message = "Updated {$result['updated']} student(s).";
        if (! empty($result['failed'])) {
            $message .= ' '.count($result['failed']).' entr(y/ies) failed: '.collect($result['failed'])->pluck('reason')->unique()->implode('; ').'.';
        }

        return back()->with('status', $message);
    }

    /** Results/turnout are legitimately unavailable at various points in the election lifecycle (404 from Go); render that as "not yet available" rather than an error page. */
    protected function tryFetch(callable $fetch): ?array
    {
        try {
            return $fetch();
        } catch (VotingApiException $e) {
            return null;
        }
    }
}
