@extends('layouts.app')

@section('title', 'New election — SRC Elections Admin')

@section('content')
<h1 class="mb-6 text-xl font-semibold">New election</h1>

<form method="POST" action="{{ route('elections.store') }}" class="max-w-md space-y-4 rounded-lg border border-slate-200 bg-white p-6">
    @csrf
    <div>
        <label for="name" class="block text-sm font-medium text-slate-700">Election name</label>
        <input id="name" name="name" type="text" value="{{ old('name') }}" required maxlength="200"
               class="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-slate-500 focus:ring-slate-500 sm:text-sm">
    </div>
    <div>
        <label for="election_date" class="block text-sm font-medium text-slate-700">Election date</label>
        <input id="election_date" name="election_date" type="date" value="{{ old('election_date') }}" required
               class="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-slate-500 focus:ring-slate-500 sm:text-sm">
        <p class="mt-1 text-xs text-slate-500">Voting opens at 06:00 and closes at 23:59 in the university's configured timezone on this date — this cannot be changed once voting opens.</p>
    </div>
    <button type="submit" class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">Create election</button>
</form>
@endsection
