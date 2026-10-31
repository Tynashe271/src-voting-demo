<?php

use App\Http\Controllers\Auth\AdminAuthController;
use App\Http\Controllers\AdminReportController;
use App\Http\Controllers\ElectionController;
use App\Http\Middleware\RequireAdminMfa;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| API Routes
|--------------------------------------------------------------------------
|
| This is a pure admin-configuration + read-only-reporting portal. Staff authenticate here with
| their own session + MFA (the `admin` guard); every route below then calls the Go voting
| backend's internal admin API via VotingApiClient using a separate service-level secret that
| never reaches a browser or a shipped client binary. There is no student-facing or ballot-facing
| route in this app — the student app talks to the Go backend directly, with no secret of its own.
|
| This is also the JSON surface the native (Kotlin Multiplatform) admin app uses, alongside the
| Blade UI — both end up calling the exact same VotingApiClient methods, so there is exactly one
| code path that ever holds the Go admin secret.
|
*/

// Laravel's 'api' middleware group is stateless by default (no session, no cookies) -- fine for
// token-based APIs, but the `admin` guard here is the same session-backed guard the Blade UI uses,
// and Auth::guard('admin')->login() has nothing to persist into without an active session. Adding
// these three (the session-relevant slice of the 'web' group, deliberately not all of it -- no
// CSRF here, see the file-level doc comment) is what makes a login actually stick across requests
// for a native client the same way it already does for a browser.
Route::middleware([
    \Illuminate\Cookie\Middleware\EncryptCookies::class,
    \Illuminate\Cookie\Middleware\AddQueuedCookiesToResponse::class,
    \Illuminate\Session\Middleware\StartSession::class,
])->group(function () {
    Route::prefix('admin/auth')->group(function () {
        Route::post('/login', [AdminAuthController::class, 'login']);

        // RequireAdminMfa exempts this whole prefix (see the middleware's own doc comment)
        // precisely so an un-enrolled admin can still reach login/me/logout/setup/enable/
        // disable-mfa -- without that, nobody could ever complete their first MFA enrollment
        // through the API.
        Route::middleware(['auth:admin', RequireAdminMfa::class])->group(function () {
            Route::post('/logout', [AdminAuthController::class, 'logout']);
            Route::get('/me', [AdminAuthController::class, 'me']);
            Route::post('/setup-mfa', [AdminAuthController::class, 'setupMfa']);
            Route::post('/enable-mfa', [AdminAuthController::class, 'enableMfa']);
            Route::post('/disable-mfa', [AdminAuthController::class, 'disableMfa']);
        });
    });

    Route::middleware(['auth:admin', RequireAdminMfa::class])->prefix('admin')->group(function () {
        Route::get('/elections', [ElectionController::class, 'index']);
        Route::post('/elections', [ElectionController::class, 'store']);
        Route::patch('/elections/{election}', [ElectionController::class, 'update']);
        Route::get('/elections/{election}/positions', [ElectionController::class, 'positions']);
        Route::post('/elections/{election}/positions', [ElectionController::class, 'createPosition']);
        Route::patch('/positions/{position}', [ElectionController::class, 'updatePosition']);
        Route::delete('/positions/{position}', [ElectionController::class, 'deletePosition']);
        Route::get('/elections/{election}/candidates', [ElectionController::class, 'candidates']);
        Route::post('/positions/{position}/candidates', [ElectionController::class, 'createCandidate']);
        Route::patch('/candidates/{candidate}', [ElectionController::class, 'updateCandidate']);
        Route::patch('/candidates/{candidate}/approve', [ElectionController::class, 'approveCandidate']);
        Route::patch('/candidates/{candidate}/reject', [ElectionController::class, 'rejectCandidate']);
        Route::patch('/candidates/{candidate}/disqualify', [ElectionController::class, 'disqualifyCandidate']);
        Route::post('/elections/{election}/results/publish', [ElectionController::class, 'publishResults']);
        Route::post('/elections/{election}/eligibility', [ElectionController::class, 'setEligibility']);
        Route::post('/students', [ElectionController::class, 'importStudents']);

        Route::get('/elections/{election}/turnout', [AdminReportController::class, 'turnout']);
        Route::get('/elections/{election}/results', [AdminReportController::class, 'results']);
        Route::get('/elections/{election}/timing', [AdminReportController::class, 'timing']);
        Route::get('/audit', [AdminReportController::class, 'audit']);
    });
});
