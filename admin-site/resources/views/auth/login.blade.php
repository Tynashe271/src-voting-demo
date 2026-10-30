@extends('layouts.app')

@section('title', 'Sign in — SRC Elections Admin')

@section('content')
<div class="mx-auto mt-16 max-w-sm">
    <h1 class="mb-1 text-xl font-semibold">SRC Elections Admin</h1>
    <p class="mb-6 text-sm text-slate-600">Staff sign-in. This is a service-management portal — students vote on the separate student site.</p>

    <form method="POST" action="{{ route('login') }}" class="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
        @csrf
        <div>
            <label for="email" class="block text-sm font-medium text-slate-700">Email</label>
            <input id="email" name="email" type="email" value="{{ old('email') }}" required autofocus
                   class="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-slate-500 focus:ring-slate-500 sm:text-sm">
        </div>
        <div>
            <label for="password" class="block text-sm font-medium text-slate-700">Password</label>
            <input id="password" name="password" type="password" required
                   class="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-slate-500 focus:ring-slate-500 sm:text-sm">
        </div>
        <button type="submit" class="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">Sign in</button>
    </form>
</div>
@endsection
