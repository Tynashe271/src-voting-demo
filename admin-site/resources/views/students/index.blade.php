@extends('layouts.app')

@section('title', 'Students — SRC Elections Admin')

@section('content')
<h1 class="mb-2 text-xl font-semibold">Import / refresh official student register</h1>
<p class="mb-2 text-sm text-slate-600">One student per line: <code class="rounded bg-slate-100 px-1">registration_number,first_name,last_name,university_email,national_id,faculty,programme,level</code> (faculty/programme/level optional). Existing registration numbers are refreshed, not duplicated.</p>
<p class="mb-6 text-sm text-amber-700">There is no separate student sign-up step: importing a student here also sets their login — <strong>registration number is the username, national ID is the password</strong>. A student can sign in the moment they're imported.</p>

<form method="POST" action="{{ route('students.store') }}" class="max-w-2xl space-y-4 rounded-lg border border-slate-200 bg-white p-6">
    @csrf
    <textarea name="students_csv" rows="10" required placeholder="R12345,Jane,Doe,jane.doe@students.university.ac.zw,63-123456A78,Science,Computer Science,2"
              class="block w-full rounded-md border-slate-300 font-mono text-sm shadow-sm focus:border-slate-500 focus:ring-slate-500">{{ old('students_csv') }}</textarea>
    <button type="submit" class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">Import</button>
</form>
@endsection
