@extends('layouts.app')

@section('title', 'Elections — SRC Elections Admin')

@section('content')
<div class="mb-6 flex items-center justify-between">
    <h1 class="text-xl font-semibold">Elections</h1>
    <a href="{{ route('elections.create') }}" class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">New election</a>
</div>

@if (empty($elections))
    <p class="text-sm text-slate-600">No elections yet. Create one to get started.</p>
@else
    <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                <tr>
                    <th class="px-4 py-3">Name</th>
                    <th class="px-4 py-3">Date</th>
                    <th class="px-4 py-3">Status</th>
                    <th class="px-4 py-3"></th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
                @foreach ($elections as $election)
                    <tr>
                        <td class="px-4 py-3 font-medium">{{ $election['name'] }}</td>
                        <td class="px-4 py-3 text-slate-600">{{ $election['election_date'] }}</td>
                        <td class="px-4 py-3">
                            <span class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium
                                @class([
                                    'bg-amber-100 text-amber-800' => $election['status'] === 'UPCOMING',
                                    'bg-emerald-100 text-emerald-800' => $election['status'] === 'OPEN',
                                    'bg-slate-200 text-slate-700' => in_array($election['status'], ['CLOSED', 'RESULTS_VISIBLE']),
                                ])">{{ $election['status'] }}</span>
                        </td>
                        <td class="px-4 py-3 text-right">
                            <a href="{{ route('elections.show', $election['id']) }}" class="text-sm font-medium text-slate-900 hover:underline">Manage &rarr;</a>
                        </td>
                    </tr>
                @endforeach
            </tbody>
        </table>
    </div>
@endif
@endsection
