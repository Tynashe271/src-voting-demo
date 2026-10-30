<?php

namespace App\Http\Controllers\Web;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use App\Services\VotingApiClient;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

/**
 * Bulk-loads the official student register into the Go backend via VotingApiClient::importStudents.
 * There is no separate student self-registration step: importing a student here provisions their
 * login in the same call — registration_number is the username, national_id becomes their password
 * (hashed by Go, never stored or logged in plain text here). Accepts simple one-line-per-student CSV
 * pasted into a textarea rather than a file upload, to keep this page dependency-free; a real
 * deployment may prefer wiring this to the university's SIS export.
 */
class StudentImportController extends Controller
{
    public function __construct(
        protected VotingApiClient $votingApi,
        protected AuditService $auditService,
    ) {
    }

    public function index(): View
    {
        return view('students.index');
    }

    public function store(Request $request): RedirectResponse
    {
        $request->validate(['students_csv' => 'required|string']);

        $students = [];
        foreach (preg_split('/\r\n|\r|\n/', $request->string('students_csv')) as $line) {
            $line = trim($line);
            if ($line === '') {
                continue;
            }
            $fields = array_map('trim', explode(',', $line));
            if (count($fields) < 5) {
                return back()->withInput()->with('error', "Each line needs at least registration_number,first_name,last_name,university_email,national_id — got: \"{$line}\"");
            }
            $students[] = [
                'registration_number' => $fields[0],
                'first_name' => $fields[1],
                'last_name' => $fields[2],
                'university_email' => $fields[3],
                'national_id' => $fields[4],
                'faculty' => $fields[5] ?? null,
                'programme' => $fields[6] ?? null,
                'level' => $fields[7] ?? null,
            ];
        }

        if ($students === []) {
            return back()->with('error', 'Enter at least one student.');
        }

        $result = $this->votingApi->importStudents($students);
        $this->auditService->log('students_imported', 'students', ['upserted' => $result['upserted'] ?? 0]);

        $message = "Imported/refreshed {$result['upserted']} student(s).";
        if (! empty($result['failed'])) {
            $message .= ' '.count($result['failed']).' failed: '.collect($result['failed'])
                ->map(fn ($f) => "{$f['registration_number']} ({$f['reason']})")->implode(', ').'.';
        }

        return back()->with('status', $message);
    }
}
