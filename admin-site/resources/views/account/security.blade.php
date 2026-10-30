@extends('layouts.app')

@section('title', 'Security — SRC Elections Admin')

@section('content')
<h1 class="mb-6 text-xl font-semibold">Account security</h1>

<div class="max-w-md rounded-lg border border-slate-200 bg-white p-6">
    <p class="mb-4 text-sm">Signed in as <span class="font-medium">{{ $admin->email }}</span></p>

    @if ($admin->mfa_enabled)
        <p class="mb-4 text-sm text-emerald-700">Multi-factor authentication is enabled.</p>
        <form method="POST" action="{{ route('account.security.disable') }}" class="space-y-3">
            @csrf
            <label class="block text-sm font-medium text-slate-700">Confirm your password to disable MFA</label>
            <input name="password" type="password" required class="block w-full rounded-md border-slate-300 shadow-sm sm:text-sm">
            <button type="submit" class="rounded-md border border-red-300 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-50">Disable MFA</button>
        </form>
    @elseif ($setupSecret)
        <p class="mb-3 text-sm text-slate-600">Scan this into your authenticator app, or enter the secret manually, then confirm a code to finish enabling MFA.</p>
        <p class="mb-3 break-all rounded bg-slate-100 px-3 py-2 font-mono text-xs">{{ $setupSecret }}</p>
        <form method="POST" action="{{ route('account.security.enable') }}" class="space-y-3">
            @csrf
            <label class="block text-sm font-medium text-slate-700">Verification code</label>
            <input name="otp" type="text" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required class="block w-full rounded-md border-slate-300 text-center tracking-widest shadow-sm sm:text-sm">
            <button type="submit" class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">Enable MFA</button>
        </form>
    @else
        <p class="mb-4 text-sm text-amber-700">Multi-factor authentication is not enabled. Enabling it is strongly recommended for admin accounts.</p>
        <form method="POST" action="{{ route('account.security.setup') }}">
            @csrf
            <button type="submit" class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">Set up MFA</button>
        </form>
    @endif
</div>
@endsection
