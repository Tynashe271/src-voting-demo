<?php

use App\Http\Controllers\Web\AuditController;
use App\Http\Controllers\Web\AuthController;
use App\Http\Controllers\Web\ElectionController;
use App\Http\Controllers\Web\SecurityController;
use App\Http\Controllers\Web\StudentImportController;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| Web Routes
|--------------------------------------------------------------------------
|
| The Blade admin UI. It shares the `admin` guard/session with routes/api.php and, like the JSON
| API, never writes to the voting domain directly — every controller here calls VotingApiClient,
| which talks to the Go backend's internal admin API.
|
*/

Route::middleware('guest:admin')->group(function () {
    Route::get('/login', [AuthController::class, 'showLogin'])->name('login');
    Route::post('/login', [AuthController::class, 'login']);
    Route::get('/login/mfa', [AuthController::class, 'showMfa'])->name('login.mfa');
    Route::post('/login/mfa', [AuthController::class, 'verifyMfa']);
});

Route::middleware('auth:admin')->group(function () {
    Route::post('/logout', [AuthController::class, 'logout'])->name('logout');

    Route::get('/', [ElectionController::class, 'index'])->name('dashboard');
    Route::get('/elections/create', [ElectionController::class, 'create'])->name('elections.create');
    Route::post('/elections', [ElectionController::class, 'store'])->name('elections.store');
    Route::get('/elections/{election}', [ElectionController::class, 'show'])->name('elections.show');
    Route::patch('/elections/{election}', [ElectionController::class, 'update'])->name('elections.update');
    Route::post('/elections/{election}/positions', [ElectionController::class, 'storePosition'])->name('positions.store');
    Route::patch('/elections/{election}/positions/{position}', [ElectionController::class, 'updatePosition'])->name('positions.update');
    Route::delete('/elections/{election}/positions/{position}', [ElectionController::class, 'destroyPosition'])->name('positions.destroy');
    Route::post('/elections/{election}/positions/{position}/candidates', [ElectionController::class, 'storeCandidate'])->name('candidates.store');
    Route::patch('/elections/{election}/candidates/{candidate}', [ElectionController::class, 'updateCandidate'])->name('candidates.update');
    Route::post('/elections/{election}/candidates/{candidate}/approve', [ElectionController::class, 'approveCandidate'])->name('candidates.approve');
    Route::post('/elections/{election}/candidates/{candidate}/reject', [ElectionController::class, 'rejectCandidate'])->name('candidates.reject');
    Route::post('/elections/{election}/eligibility', [ElectionController::class, 'setEligibility'])->name('eligibility.store');

    Route::get('/students', [StudentImportController::class, 'index'])->name('students.index');
    Route::post('/students', [StudentImportController::class, 'store'])->name('students.store');

    Route::get('/audit', [AuditController::class, 'index'])->name('audit.index');

    Route::get('/account/security', [SecurityController::class, 'show'])->name('account.security');
    Route::post('/account/security/setup-mfa', [SecurityController::class, 'setupMfa'])->name('account.security.setup');
    Route::post('/account/security/enable-mfa', [SecurityController::class, 'enableMfa'])->name('account.security.enable');
    Route::post('/account/security/disable-mfa', [SecurityController::class, 'disableMfa'])->name('account.security.disable');
});
