@extends('layouts.app')

@section('title', 'Verification code — SRC Elections Admin')

@section('content')
<div class="mx-auto mt-16 max-w-sm">
    <h1 class="mb-1 text-xl font-semibold">Enter your verification code</h1>
    <p class="mb-6 text-sm text-slate-600">Open your authenticator app and enter the current 6-digit code.</p>

    <form method="POST" action="{{ route('login.mfa') }}" class="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
        @csrf
        <div>
            <label for="otp" class="block text-sm font-medium text-slate-700">Verification code</label>
            <input id="otp" name="otp" type="text" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required autofocus
                   class="mt-1 block w-full rounded-md border-slate-300 text-center text-lg tracking-widest shadow-sm focus:border-slate-500 focus:ring-slate-500">
        </div>
        <button type="submit" class="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">Verify</button>
    </form>
</div>
@endsection
