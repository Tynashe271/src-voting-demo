<?php

use App\Exceptions\VotingApiException;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        // Render (like Heroku/Fly) terminates TLS at its edge and forwards plain HTTP internally,
        // setting X-Forwarded-Proto/Host/For. Without trusting that header, $request->isSecure()
        // is always false behind the proxy, so url()/redirect() generate http:// links even on an
        // https:// site -- which breaks the post-login redirect (and any Secure-cookie-dependent
        // follow-up request following that http:// hop). The proxy is the only hop in front of
        // this container, so trusting all proxies for these headers is appropriate here.
        $middleware->trustProxies(at: '*');

        $middleware->append([
            \App\Http\Middleware\SecurityHeadersMiddleware::class,
        ]);

        // Middleware params are comma-separated, not colon-separated (':60:1' silently passed
        // "60:1" as a single malformed $maxAttempts, tripping a non-numeric arithmetic error the
        // first time this middleware actually ran under test).
        $middleware->api(prepend: [
            \App\Http\Middleware\RateLimitMiddleware::class . ':60,1',
        ]);

        // The Blade admin UI shares the same `admin` guard/session as the JSON API — an
        // unauthenticated visit to a web page redirects to the login form instead of the API's
        // 401 JSON response (shouldRenderJsonWhen below decides which one a given request gets).
        $middleware->redirectGuestsTo('/login');
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->expectsJson(),
        );

        // Surface the Go voting backend's own status/message unchanged (e.g. its 409 when an
        // election is already open) rather than wrapping it in a generic 500. The Blade UI gets
        // redirected back with the same message as a flash error instead of a raw JSON body.
        $exceptions->render(function (VotingApiException $e, Request $request) {
            if ($request->is('api/*') || $request->expectsJson()) {
                return response()->json(['success' => false, 'message' => $e->getMessage()], $e->status);
            }

            return back()->withInput()->with('error', $e->getMessage());
        });
    })
    // Election open/close, result tallying/sealing, and result-visibility hiding are all handled
    // automatically by the Go backend's electionEngine() goroutine (every 30s) — there is nothing
    // left for this app to schedule.
    ->create();
