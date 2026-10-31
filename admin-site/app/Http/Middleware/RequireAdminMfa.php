<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Symfony\Component\HttpFoundation\Response;

/**
 * MFA is opt-in at the database level (admin_accounts.mfa_enabled) so an already-enrolled admin can
 * still disable it deliberately, but a freshly created or not-yet-enrolled account must not be able
 * to use anything else first. This runs after auth:admin and blocks every route except the
 * self-service MFA/account surface itself until mfa_enabled is true.
 *
 * Exemption is path-based (not route-name-based) so the same middleware covers both the Blade UI
 * (/account/security*, /logout) and the native-app JSON API (/api/admin/auth/*, which is exactly
 * that surface's API equivalent: login, me, logout, setup/enable/disable MFA) without having to
 * keep two parallel exemption lists in sync.
 */
class RequireAdminMfa
{
    public function handle(Request $request, Closure $next): Response
    {
        $admin = Auth::guard('admin')->user();
        $exempt = $request->is('account/security*', 'logout', 'api/admin/auth/*');

        if ($admin && ! $admin->mfa_enabled && ! $exempt) {
            if ($request->is('api/*') || $request->expectsJson()) {
                return response()->json([
                    'success' => false,
                    'message' => 'Set up multi-factor authentication before continuing. This account cannot be used without it.',
                    'mfa_setup_required' => true,
                ], 403);
            }

            return redirect()
                ->route('account.security')
                ->with('error', 'Set up multi-factor authentication before continuing. This account cannot be used without it.');
        }

        return $next($request);
    }
}
