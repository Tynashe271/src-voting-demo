<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use App\Models\AdminAccount;
use App\Services\PasswordHashService;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\Auth;
use Illuminate\Validation\ValidationException;
use PragmaRX\Google2FA\Google2FA;

class AdminAuthController extends Controller
{
    protected PasswordHashService $passwordHashService;
    protected Google2FA $google2FA;
    protected AuditService $auditService;

    public function __construct(PasswordHashService $passwordHashService, Google2FA $google2FA, AuditService $auditService)
    {
        $this->passwordHashService = $passwordHashService;
        $this->google2FA = $google2FA;
        $this->auditService = $auditService;
    }

    /**
     * Login admin
     */
    public function login(Request $request): JsonResponse
    {
        $request->validate([
            'email' => 'required|email',
            'password' => 'required|string',
        ]);

        $admin = AdminAccount::where('email', $request->email)->first();

        if (!$admin) {
            return response()->json([
                'success' => false,
                'message' => 'Invalid credentials.',
            ], 401);
        }

        // Check if account is active
        if ($admin->account_status !== 'ACTIVE') {
            return response()->json([
                'success' => false,
                'message' => 'Your admin account is not active.',
            ], 403);
        }

        // Verify password
        if (!$this->passwordHashService->verify($request->password, $admin->password_hash)) {
            return response()->json([
                'success' => false,
                'message' => 'Invalid credentials.',
            ], 401);
        }

        // Check if MFA is enabled
        if ($admin->mfa_enabled) {
            $request->validate([
                'otp' => 'required|string|digits:6',
            ]);

            if (!$this->verifyMfa($admin, $request->otp)) {
                return response()->json([
                    'success' => false,
                    'message' => 'Invalid MFA code.',
                ], 401);
            }
        }

        // Authenticate admin
        Auth::guard('admin')->login($admin);

        $this->auditService->logAdminAuth('admin_login', $admin->id, [
            'success' => true,
            'mfa_used' => $admin->mfa_enabled,
        ]);

        return response()->json([
            'success' => true,
            'message' => 'Login successful.',
            'data' => [
                'admin' => [
                    'id' => $admin->id,
                    'staff_identifier' => $admin->staff_identifier,
                    'email' => $admin->email,
                    'mfa_enabled' => $admin->mfa_enabled,
                ],
            ],
        ]);
    }

    /**
     * Logout admin
     */
    public function logout(Request $request): JsonResponse
    {
        $admin = Auth::guard('admin')->user();
        
        Auth::guard('admin')->logout();

        $request->session()->invalidate();
        $request->session()->regenerateToken();

        if ($admin) {
            $this->auditService->logAdminAuth('admin_logout', $admin->id);
        }

        return response()->json([
            'success' => true,
            'message' => 'Logged out successfully.',
        ]);
    }

    /**
     * Setup MFA for admin
     */
    public function setupMfa(Request $request): JsonResponse
    {
        $admin = Auth::guard('admin')->user();

        $secret = $this->google2FA->generateSecretKey();
        $qrCodeUrl = $this->google2FA->getQRCodeUrl(
            config('app.name'),
            $admin->email,
            $secret
        );

        // Store secret temporarily (not enabled yet)
        session(['mfa_setup_secret' => $secret]);

        return response()->json([
            'success' => true,
            'data' => [
                'secret' => $secret,
                'qr_code_url' => $qrCodeUrl,
            ],
        ]);
    }

    /**
     * Enable MFA for admin
     */
    public function enableMfa(Request $request): JsonResponse
    {
        $request->validate([
            'otp' => 'required|string|digits:6',
        ]);

        $admin = Auth::guard('admin')->user();
        $secret = session('mfa_setup_secret');

        if (!$secret) {
            return response()->json([
                'success' => false,
                'message' => 'MFA setup session expired. Please start over.',
            ], 400);
        }

        if (!$this->google2FA->verifyKey($secret, $request->otp)) {
            return response()->json([
                'success' => false,
                'message' => 'Invalid OTP code.',
            ], 400);
        }

        $admin->update([
            'mfa_enabled' => true,
            'mfa_secret' => encrypt($secret),
        ]);

        session()->forget('mfa_setup_secret');

        return response()->json([
            'success' => true,
            'message' => 'MFA enabled successfully.',
        ]);
    }

    /**
     * Disable MFA for admin
     */
    public function disableMfa(Request $request): JsonResponse
    {
        $request->validate([
            'password' => 'required|string',
        ]);

        $admin = Auth::guard('admin')->user();

        // Verify password
        if (!$this->passwordHashService->verify($request->password, $admin->password_hash)) {
            return response()->json([
                'success' => false,
                'message' => 'Invalid password.',
            ], 401);
        }

        $admin->update([
            'mfa_enabled' => false,
            'mfa_secret' => null,
        ]);

        return response()->json([
            'success' => true,
            'message' => 'MFA disabled successfully.',
        ]);
    }

    /**
     * Get authenticated admin profile
     */
    public function me(Request $request): JsonResponse
    {
        $admin = Auth::guard('admin')->user();

        return response()->json([
            'success' => true,
            'data' => [
                'admin' => [
                    'id' => $admin->id,
                    'staff_identifier' => $admin->staff_identifier,
                    'email' => $admin->email,
                    'mfa_enabled' => $admin->mfa_enabled,
                    'account_status' => $admin->account_status,
                ],
            ],
        ]);
    }

    /**
     * Verify MFA code
     */
    protected function verifyMfa(AdminAccount $admin, string $otp): bool
    {
        if (!$admin->mfa_enabled || !$admin->mfa_secret) {
            return false;
        }

        $secret = decrypt($admin->mfa_secret);
        return $this->google2FA->verifyKey($secret, $otp);
    }
}
