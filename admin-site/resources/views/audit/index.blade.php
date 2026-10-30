@extends('layouts.app')

@section('title', 'Audit — SRC Elections Admin')

@section('content')
<h1 class="mb-6 text-xl font-semibold">Audit trails</h1>

<div class="mb-8">
    <h2 class="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Ballot &amp; result integrity (Go, hash-chained)</h2>
    <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                <tr><th class="px-4 py-2">Time</th><th class="px-4 py-2">Actor</th><th class="px-4 py-2">Action</th><th class="px-4 py-2">Resource</th></tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
                @forelse ($votingAudit as $entry)
                    <tr>
                        <td class="px-4 py-2 whitespace-nowrap text-slate-600">{{ $entry['created_at'] }}</td>
                        <td class="px-4 py-2">{{ $entry['actor'] }}</td>
                        <td class="px-4 py-2 font-medium">{{ $entry['action'] }}</td>
                        <td class="px-4 py-2 text-slate-600">{{ $entry['resource'] }}</td>
                    </tr>
                @empty
                    <tr><td colspan="4" class="px-4 py-4 text-center text-slate-500">No entries yet.</td></tr>
                @endforelse
            </tbody>
        </table>
    </div>
</div>

<div>
    <h2 class="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Admin portal actions (this app, not tamper-evident)</h2>
    <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                <tr><th class="px-4 py-2">Time</th><th class="px-4 py-2">Actor</th><th class="px-4 py-2">Action</th><th class="px-4 py-2">Resource</th></tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
                @forelse ($adminAudit as $entry)
                    <tr>
                        <td class="px-4 py-2 whitespace-nowrap text-slate-600">{{ $entry->created_at }}</td>
                        <td class="px-4 py-2">{{ $entry->actor_service }}</td>
                        <td class="px-4 py-2 font-medium">{{ $entry->action }}</td>
                        <td class="px-4 py-2 text-slate-600">{{ $entry->resource }}</td>
                    </tr>
                @empty
                    <tr><td colspan="4" class="px-4 py-4 text-center text-slate-500">No entries yet.</td></tr>
                @endforelse
            </tbody>
        </table>
    </div>
</div>
@endsection
