<?php

namespace App\Http\Controllers\Web;

use App\Http\Controllers\Controller;
use App\Models\AdminAccount;
use App\Services\AuditService;
use App\Services\PasswordHashService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\View\View;
use PragmaRX\Google2FA\Google2FA;

/**
 * Session-based login for the Blade admin UI, sharing the same `admin` guard as the JSON API
 * (routes/api.php). Credential/MFA verification logic mirrors
 * App\Http\Controllers\Auth\AdminAuthController exactly; this controller only differs in how it
 * responds (redirects + flashed views instead of JSON).
 */
class AuthController extends Controller
{
    public function __construct(
        protected PasswordHashService $passwordHashService,
        protected Google2FA $google2FA,
        protected AuditService $auditService,
    ) {
    }

    public function showLogin(): View
    {
        return view('auth.login');
    }

    public function login(Request $request): RedirectResponse
    {
        $request->validate([
            'email' => 'required|email',
            'password' => 'required|string',
        ]);

        $admin = AdminAccount::where('email', $request->string('email'))->first();

        if (! $admin || ! $this->passwordHashService->verify($request->string('password'), $admin->password_hash)) {
            return back()->withInput($request->only('email'))->with('error', 'Invalid credentials.');
        }

        if ($admin->account_status !== 'ACTIVE') {
            return back()->withInput($request->only('email'))->with('error', 'Your admin account is not active.');
        }

        if ($admin->mfa_enabled) {
            $request->session()->put('mfa_pending_admin_id', $admin->id);

            return redirect()->route('login.mfa');
        }

        Auth::guard('admin')->login($admin);
        $request->session()->regenerate();
        $this->auditService->logAdminAuth('admin_login', $admin->id, ['mfa_used' => false]);

        return redirect()->intended(route('dashboard'));
    }

    public function showMfa(Request $request): View|RedirectResponse
    {
        if (! $request->session()->has('mfa_pending_admin_id')) {
            return redirect()->route('login');
        }

        return view('auth.mfa');
    }

    public function verifyMfa(Request $request): RedirectResponse
    {
        $request->validate(['otp' => 'required|string|digits:6']);

        $adminId = $request->session()->get('mfa_pending_admin_id');
        $admin = $adminId ? AdminAccount::find($adminId) : null;

        if (! $admin || ! $admin->mfa_secret || ! $this->google2FA->verifyKey(decrypt($admin->mfa_secret), $request->string('otp'))) {
            return back()->with('error', 'Invalid verification code.');
        }

        $request->session()->forget('mfa_pending_admin_id');
        Auth::guard('admin')->login($admin);
        $request->session()->regenerate();
        $this->auditService->logAdminAuth('admin_login', $admin->id, ['mfa_used' => true]);

        return redirect()->intended(route('dashboard'));
    }

    public function logout(Request $request): RedirectResponse
    {
        $admin = Auth::guard('admin')->user();
        Auth::guard('admin')->logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        if ($admin) {
            $this->auditService->logAdminAuth('admin_logout', $admin->id);
        }

        return redirect()->route('login');
    }
}
