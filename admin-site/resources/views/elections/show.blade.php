@extends('layouts.app')

@section('title', ($election['name'] ?? 'Election').' — SRC Elections Admin')

@section('content')
<div class="mb-6">
    <a href="{{ route('dashboard') }}" class="text-sm text-slate-600 hover:underline">&larr; All elections</a>
</div>

<div class="mb-8 grid grid-cols-1 gap-6 md:grid-cols-2">
    <div class="rounded-lg border border-slate-200 bg-white p-6">
        <h2 class="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Election details</h2>
        @if ($election)
            <form method="POST" action="{{ route('elections.update', $electionId) }}" class="space-y-3">
                @csrf @method('PATCH')
                <div>
                    <label class="block text-sm font-medium text-slate-700">Name</label>
                    <input name="name" type="text" value="{{ old('name', $election['name']) }}" required maxlength="200"
                           class="mt-1 block w-full rounded-md border-slate-300 shadow-sm sm:text-sm">
                </div>
                <div>
                    <label class="block text-sm font-medium text-slate-700">Election date</label>
                    <input name="election_date" type="date" value="{{ old('election_date', $election['election_date']) }}" required
                           class="mt-1 block w-full rounded-md border-slate-300 shadow-sm sm:text-sm">
                </div>
                <p class="text-xs text-slate-500">Status: <span class="font-medium">{{ $election['status'] }}</span>. Editable only before voting opens.</p>
                <button type="submit" class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">Save changes</button>
            </form>
        @else
            <p class="text-sm text-slate-600">Election not found.</p>
        @endif
    </div>

    <div class="rounded-lg border border-slate-200 bg-white p-6">
        <h2 class="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Turnout &amp; results</h2>
        @if ($turnout)
            <dl class="mb-4 grid grid-cols-3 gap-2 text-sm">
                <div><dt class="text-slate-500">Eligible</dt><dd class="text-lg font-semibold">{{ $turnout['eligible_students'] }}</dd></div>
                <div><dt class="text-slate-500">Votes cast</dt><dd class="text-lg font-semibold">{{ $turnout['votes_cast'] }}</dd></div>
                <div><dt class="text-slate-500">Turnout</dt><dd class="text-lg font-semibold">{{ number_format($turnout['turnout_percent'], 1) }}%</dd></div>
            </dl>
        @else
            <p class="mb-4 text-sm text-slate-600">Turnout is not currently available for this election.</p>
        @endif

        @if ($results)
            <div class="border-t border-slate-100 pt-4">
                <div class="mb-3 flex items-center justify-between">
                    <p class="text-sm font-medium">
                        Sealed results
                        @if ($results['published'])
                            <span class="ml-2 inline-flex rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800">Published {{ \Illuminate\Support\Carbon::parse($results['published_at'])->format('d M Y, H:i') }}</span>
                        @else
                            <span class="ml-2 inline-flex rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">Staff-only preview &mdash; not published to students</span>
                        @endif
                    </p>
                    @if (! $results['published'])
                        <form method="POST" action="{{ route('results.publish', $electionId) }}"
                              onsubmit="return confirm('Publish results to all students? This cannot be undone or edited afterwards.');">
                            @csrf
                            <button type="submit" class="rounded-md bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-800">Publish results</button>
                        </form>
                    @endif
                </div>
                {{-- Simple per-position bar chart: bar width is this candidate's share of that
                     position's total votes, so results read at a glance without a JS charting lib. --}}
                @foreach (collect($results['totals'])->groupBy('Position') as $positionName => $rows)
                    @php
                        $positionTotal = $rows->sum('Votes');
                        $maxVotes = $rows->max('Votes') ?: 1;
                    @endphp
                    <div class="mb-4">
                        <p class="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{{ $positionName }} &mdash; {{ $positionTotal }} vote{{ $positionTotal === 1 ? '' : 's' }}</p>
                        <div class="space-y-1.5">
                            @foreach ($rows->sortByDesc('Votes') as $row)
                                @php $pct = $positionTotal > 0 ? round($row['Votes'] * 100 / $positionTotal, 1) : 0; @endphp
                                <div class="flex items-center gap-2 text-sm">
                                    <span class="w-32 shrink-0 truncate" title="{{ $row['Candidate'] }}">{{ $row['Candidate'] }}</span>
                                    <div class="h-4 flex-1 overflow-hidden rounded bg-slate-100">
                                        <div class="h-full rounded bg-slate-700" style="width: {{ $maxVotes > 0 ? round($row['Votes'] * 100 / $maxVotes, 1) : 0 }}%"></div>
                                    </div>
                                    <span class="w-20 shrink-0 text-right text-slate-600">{{ $row['Votes'] }} ({{ $pct }}%)</span>
                                </div>
                            @endforeach
                        </div>
                    </div>
                @endforeach
                <p class="mt-2 break-all text-xs text-slate-400">Integrity hash: {{ $results['integrity_hash'] }}</p>
            </div>
        @else
            <p class="border-t border-slate-100 pt-4 text-sm text-slate-600">Results are not sealed yet &mdash; the election must close first.</p>
        @endif
    </div>
</div>

<div class="mb-8 rounded-lg border border-slate-200 bg-white p-6">
    <h2 class="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Voter eligibility</h2>
    <form method="POST" action="{{ route('eligibility.store', $electionId) }}" class="space-y-3">
        @csrf
        <div>
            <label class="block text-sm font-medium text-slate-700">Registration numbers</label>
            <textarea name="registration_numbers" rows="4" placeholder="One per line, or comma-separated" required
                      class="mt-1 block w-full rounded-md border-slate-300 shadow-sm sm:text-sm font-mono"></textarea>
        </div>
        <div class="flex items-center gap-4">
            <label class="flex items-center gap-2 text-sm"><input type="radio" name="eligible" value="1" checked> Mark eligible</label>
            <label class="flex items-center gap-2 text-sm"><input type="radio" name="eligible" value="0"> Mark not eligible</label>
            <button type="submit" class="ml-auto rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">Apply</button>
        </div>
        <p class="text-xs text-slate-500">Students must already exist in the register (see <a href="{{ route('students.index') }}" class="underline">Students</a>) and eligibility can only be changed before voting opens.</p>
    </form>
</div>

<div class="mb-6 flex items-center justify-between">
    <h2 class="text-lg font-semibold">Positions &amp; candidates</h2>
</div>

<div class="space-y-6">
    @foreach ($positions as $position)
        <div class="rounded-lg border border-slate-200 bg-white p-6">
            <div class="mb-4 flex items-start justify-between gap-4">
                <form method="POST" action="{{ route('positions.update', [$electionId, $position['id']]) }}" class="grid flex-1 grid-cols-2 gap-3 sm:grid-cols-4">
                    @csrf @method('PATCH')
                    <input name="name" value="{{ $position['name'] }}" required maxlength="120" class="col-span-2 rounded-md border-slate-300 text-sm font-medium sm:col-span-1">
                    <input name="description" value="{{ $position['description'] }}" placeholder="Description" class="col-span-2 rounded-md border-slate-300 text-sm sm:col-span-1">
                    <input name="display_order" type="number" min="1" value="{{ $position['display_order'] }}" required class="rounded-md border-slate-300 text-sm" title="Display order">
                    <input name="max_selections" type="number" min="1" value="{{ $position['max_selections'] }}" required class="rounded-md border-slate-300 text-sm" title="Max selections">
                    <button type="submit" class="col-span-2 rounded-md border border-slate-300 px-3 py-1 text-xs font-medium hover:bg-slate-50 sm:col-span-4">Save position</button>
                </form>
                <form method="POST" action="{{ route('positions.destroy', [$electionId, $position['id']]) }}" onsubmit="return confirm('Remove this position?');">
                    @csrf @method('DELETE')
                    <button type="submit" class="text-xs text-red-600 hover:underline">Remove</button>
                </form>
            </div>

            <div class="space-y-2">
                @forelse ($candidatesByPosition->get($position['id'], []) as $candidate)
                    <div class="flex items-center justify-between rounded-md border border-slate-100 px-3 py-2">
                        <div class="flex items-center gap-3">
                            @if (! empty($candidate['photo_url']))
                                <img src="{{ $candidate['photo_url'] }}" alt="" class="h-10 w-10 shrink-0 rounded-full object-cover">
                            @else
                                <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-200 text-sm font-semibold text-slate-500">{{ strtoupper(substr($candidate['name'], 0, 1)) }}</span>
                            @endif
                            <div>
                            <span class="text-sm font-medium">{{ $candidate['name'] }}</span>
                            <span class="ml-2 inline-flex rounded-full px-2 py-0.5 text-xs font-medium
                                @class([
                                    'bg-amber-100 text-amber-800' => $candidate['approval_status'] === 'PENDING',
                                    'bg-emerald-100 text-emerald-800' => $candidate['approval_status'] === 'APPROVED',
                                    'bg-red-100 text-red-800' => $candidate['approval_status'] === 'REJECTED',
                                    'bg-slate-800 text-white' => $candidate['approval_status'] === 'DISQUALIFIED',
                                ])">{{ $candidate['approval_status'] }}</span>
                            </div>
                        </div>
                        <div class="flex items-center gap-3">
                            @if (! in_array($candidate['approval_status'], ['APPROVED', 'DISQUALIFIED']))
                                <form method="POST" action="{{ route('candidates.approve', [$electionId, $candidate['id']]) }}">
                                    @csrf
                                    <button type="submit" class="text-xs font-medium text-emerald-700 hover:underline">Approve</button>
                                </form>
                            @endif
                            @if (! in_array($candidate['approval_status'], ['REJECTED', 'DISQUALIFIED']))
                                <form method="POST" action="{{ route('candidates.reject', [$electionId, $candidate['id']]) }}">
                                    @csrf
                                    <button type="submit" class="text-xs font-medium text-red-600 hover:underline">Reject</button>
                                </form>
                            @endif
                            {{-- Disqualify is the only candidate action still allowed once voting has opened
                                 (migration 006) -- it's offered for an APPROVED candidate at any time, including
                                 mid-election, so staff can act on a problem discovered after voting has started. --}}
                            @if ($candidate['approval_status'] === 'APPROVED')
                                <form method="POST" action="{{ route('candidates.disqualify', [$electionId, $candidate['id']]) }}" onsubmit="return confirm('Disqualify {{ $candidate['name'] }}? Votes already cast for them will not be counted. This cannot be undone.');">
                                    @csrf
                                    <button type="submit" class="text-xs font-medium text-slate-800 hover:underline">Disqualify</button>
                                </form>
                            @endif
                        </div>
                    </div>
                @empty
                    <p class="text-sm text-slate-500">No candidates yet.</p>
                @endforelse
            </div>

            <form method="POST" action="{{ route('candidates.store', [$electionId, $position['id']]) }}" enctype="multipart/form-data" class="mt-4 grid grid-cols-1 gap-2 border-t border-slate-100 pt-4 sm:grid-cols-4">
                @csrf
                <input name="name" placeholder="Candidate name" required maxlength="200" class="rounded-md border-slate-300 text-sm sm:col-span-2">
                <div class="sm:col-span-2">
                    <label class="block text-xs font-medium text-slate-600">Picture (so students recognise the candidate)</label>
                    <input name="photo" type="file" accept="image/*" class="mt-1 block w-full text-xs">
                </div>
                <input name="photo_url" placeholder="...or paste a photo URL instead" class="rounded-md border-slate-300 text-sm sm:col-span-2">
                <button type="submit" class="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium hover:bg-slate-50">Add candidate</button>
                <textarea name="biography" placeholder="Biography (optional)" rows="2" class="rounded-md border-slate-300 text-sm sm:col-span-2"></textarea>
                <textarea name="manifesto" placeholder="Manifesto (optional)" rows="2" class="rounded-md border-slate-300 text-sm sm:col-span-2"></textarea>
            </form>
        </div>
    @endforeach

    <div class="rounded-lg border border-dashed border-slate-300 bg-white p-6">
        <h3 class="mb-3 text-sm font-semibold text-slate-700">Add a position</h3>
        <form method="POST" action="{{ route('positions.store', $electionId) }}" class="grid grid-cols-1 gap-3 sm:grid-cols-4">
            @csrf
            <input name="name" placeholder="Position name" required maxlength="120" class="rounded-md border-slate-300 text-sm">
            <input name="description" placeholder="Description (optional)" class="rounded-md border-slate-300 text-sm">
            <input name="display_order" type="number" min="1" placeholder="Order" required class="rounded-md border-slate-300 text-sm">
            <input name="max_selections" type="number" min="1" value="1" placeholder="Max selections" required class="rounded-md border-slate-300 text-sm">
            <button type="submit" class="col-span-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 sm:w-auto sm:justify-self-start">Add position</button>
        </form>
    </div>
</div>
@endsection
