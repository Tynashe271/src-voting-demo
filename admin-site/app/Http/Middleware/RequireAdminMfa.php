<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Symfony\Component\HttpFoundation\Response;

/**
 * MFA is opt-in at the database level (admin_accounts.mfa_enabled) so an already-enrolled admin can
 * still disable it deliberately, but a freshly created or not-yet-enrolled account must not be able
 * to use anything else first. This runs after auth:admin and blocks every route except the security
 * page itself (and logout) until mfa_enabled is true.
 */
class RequireAdminMfa
{
    public function handle(Request $request, Closure $next): Response
    {
        $admin = Auth::guard('admin')->user();

        if ($admin && ! $admin->mfa_enabled && ! $request->routeIs('account.security*', 'logout')) {
            return redirect()
                ->route('account.security')
                ->with('error', 'Set up multi-factor authentication before continuing. This account cannot be used without it.');
        }

        return $next($request);
    }
}
