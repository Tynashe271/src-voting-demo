<?php

namespace App\Http\Controllers\Web;

use App\Http\Controllers\Controller;
use App\Services\PasswordHashService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\View\View;
use PragmaRX\Google2FA\Google2FA;

/**
 * Lets the logged-in staff member manage their own MFA — same logic as
 * Auth\AdminAuthController::setupMfa/enableMfa/disableMfa, rendered as Blade pages instead of JSON.
 */
class SecurityController extends Controller
{
    public function __construct(
        protected PasswordHashService $passwordHashService,
        protected Google2FA $google2FA,
    ) {
    }

    public function show(Request $request): View
    {
        $admin = Auth::guard('admin')->user();
        $setupSecret = $request->session()->get('mfa_setup_secret');

        return view('account.security', [
            'admin' => $admin,
            'qrCodeUrl' => $setupSecret ? $this->google2FA->getQRCodeUrl(config('app.name'), $admin->email, $setupSecret) : null,
            'setupSecret' => $setupSecret,
        ]);
    }

    public function setupMfa(Request $request): RedirectResponse
    {
        $secret = $this->google2FA->generateSecretKey();
        $request->session()->put('mfa_setup_secret', $secret);

        return redirect()->route('account.security');
    }

    public function enableMfa(Request $request): RedirectResponse
    {
        $request->validate(['otp' => 'required|string|digits:6']);

        $secret = $request->session()->get('mfa_setup_secret');
        if (! $secret) {
            return back()->with('error', 'MFA setup session expired. Please start over.');
        }

        if (! $this->google2FA->verifyKey($secret, $request->string('otp'))) {
            return back()->with('error', 'Invalid verification code.');
        }

        Auth::guard('admin')->user()->update(['mfa_enabled' => true, 'mfa_secret' => encrypt($secret)]);
        $request->session()->forget('mfa_setup_secret');

        return redirect()->route('account.security')->with('status', 'MFA enabled.');
    }

    public function disableMfa(Request $request): RedirectResponse
    {
        $request->validate(['password' => 'required|string']);

        $admin = Auth::guard('admin')->user();
        if (! $this->passwordHashService->verify($request->string('password'), $admin->password_hash)) {
            return back()->with('error', 'Invalid password.');
        }

        $admin->update(['mfa_enabled' => false, 'mfa_secret' => null]);

        return redirect()->route('account.security')->with('status', 'MFA disabled.');
    }
}
