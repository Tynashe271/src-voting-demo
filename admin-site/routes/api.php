<?php

use App\Http\Controllers\Auth\AdminAuthController;
use App\Http\Controllers\AdminReportController;
use App\Http\Controllers\ElectionController;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| API Routes
|--------------------------------------------------------------------------
|
| This is a pure admin-configuration + read-only-reporting portal. Staff authenticate here with
| their own session + MFA (the `admin` guard); every route below then calls the Go voting
| backend's internal admin API via VotingApiClient using a separate service-level secret that
| never reaches a browser. There is no student-facing or ballot-facing route in this app — the
| student PWA talks to the Go backend directly.
|
*/

Route::prefix('admin/auth')->group(function () {
    Route::post('/login', [AdminAuthController::class, 'login']);
    Route::post('/logout', [AdminAuthController::class, 'logout'])->middleware('auth:admin');
    Route::get('/me', [AdminAuthController::class, 'me'])->middleware('auth:admin');
    Route::post('/setup-mfa', [AdminAuthController::class, 'setupMfa'])->middleware('auth:admin');
    Route::post('/enable-mfa', [AdminAuthController::class, 'enableMfa'])->middleware('auth:admin');
    Route::post('/disable-mfa', [AdminAuthController::class, 'disableMfa'])->middleware('auth:admin');
});

Route::middleware('auth:admin')->prefix('admin')->group(function () {
    Route::post('/students', [ElectionController::class, 'importStudents']);

    Route::post('/elections', [ElectionController::class, 'store']);
    Route::patch('/elections/{election}', [ElectionController::class, 'update']);
    Route::post('/elections/{election}/positions', [ElectionController::class, 'createPosition']);
    Route::patch('/positions/{position}', [ElectionController::class, 'updatePosition']);
    Route::delete('/positions/{position}', [ElectionController::class, 'deletePosition']);
    Route::post('/positions/{position}/candidates', [ElectionController::class, 'createCandidate']);
    Route::patch('/candidates/{candidate}', [ElectionController::class, 'updateCandidate']);
    Route::patch('/candidates/{candidate}/approve', [ElectionController::class, 'approveCandidate']);
    Route::patch('/candidates/{candidate}/reject', [ElectionController::class, 'rejectCandidate']);
    Route::post('/elections/{election}/eligibility', [ElectionController::class, 'setEligibility']);

    Route::get('/elections/{election}/turnout', [AdminReportController::class, 'turnout']);
    Route::get('/elections/{election}/results', [AdminReportController::class, 'results']);
    Route::get('/elections/{election}/timing', [AdminReportController::class, 'timing']);
    Route::get('/audit', [AdminReportController::class, 'audit']);
});
