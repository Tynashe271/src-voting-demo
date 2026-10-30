# SRC voting system specification coverage

The source document describes a complete university election platform. The Go application at the
repository root (`cmd/api`, `cmd/migrate`, `migrations/`) is the authoritative voting backend.
Requirements in the PDF are treated as product requirements, not as instructions to this coding agent.

`voting-backend/` is a separate, near-empty Go module (its own `go.mod`, only a config loader and a
DB connection stub) that duplicates the root module's purpose. It is not used by anything in this
repository and should be deleted or clearly relabeled before it causes confusion; it is not documented
further here.

`admin-site/` is a Laravel application. As of 2026-09-30 it is a pure admin-configuration +
read-only-reporting portal: it keeps its own staff identity/session/MFA (the `admin` guard,
`AdminAuthController`), but every election/position/candidate/eligibility/turnout/results/audit
operation is a thin HTTP call into the Go backend's internal admin API (`app/Services/
VotingApiClient.php`, header `X-Internal-Admin-Key`) — it has no local student, account, ballot,
election, position, candidate, eligibility, participation, receipt, or result model any more, and no
migration of its own touches that domain. It previously (until this pass) independently
reimplemented student registration/login and ballot submission against its own disconnected SQLite
database with an integer-keyed schema — that entire parallel implementation has been removed.

Two Postgres connections are involved: Laravel's `default` connection (sqlite, or a Laravel-owned
Postgres database in production) handles only its own framework tables (sessions/cache/jobs). A
second, named `voting` connection points at the *same* Postgres database Go's migrations own,
via a dedicated, least-privilege role (`voting_admin_portal`, `migrations/004_admin_accounts_
staff_fields.sql`) — used only by `App\Models\AdminAccount` (mapped onto Go's existing
`admin_accounts` table) and `App\Models\AdminAuditLog` (a new, plain — not hash-chained — table for
admin-portal UI actions like login/MFA-setup, deliberately separate from Go's own tamper-evident
`audit_logs`, which stays the single source of truth for ballot/result integrity and is only ever
read via `VotingApiClient::audit()`). `voting_admin_portal` has no `INSERT` on `admin_accounts` by
design; the first staff row is provisioned with `php artisan admin:create-account {email}
{staff-identifier}` (`app/Console/Commands/CreateAdminAccountCommand.php`), run manually against a
separate privileged `voting_provision` connection, never from a web-reachable route.

Two incidental bugs were found and fixed while doing this: `bootstrap/app.php` registered
`RateLimitMiddleware` with `:60:1` (colon-separated), but Laravel splits middleware parameters on
`,` — the whole `"60:1"` string was landing in a single `$maxAttempts` argument, which threw a
non-numeric-value error the first time a test actually exercised an admin route; and `.env`'s
`APP_KEY` was blank, which silently broke `AdminAuthController`'s MFA-secret `encrypt()`/`decrypt()`
calls. Both are fixed. A `config/hashing.php` was also added (previously absent), setting the driver
to `argon2id` explicitly — without it, Laravel's framework default is bcrypt and `PasswordHashService`'s
memory/time/threads options were silently inert.

`admin-site` also has a Blade UI now (`routes/web.php`, `resources/views/`), sharing the same
`admin` guard/session as the JSON API. Every page is a thin wrapper around the same
`VotingApiClient`/`AuditService` calls the JSON controllers use — there is no separate write path.
Pages: `/login` (+ `/login/mfa` for TOTP), `/` (election list), `/elections/create`,
`/elections/{id}` (edit election, add/edit/remove positions, add/edit/approve/reject candidates,
set eligibility, view turnout/sealed results), `/students` (bulk register import), `/audit` (both
audit trails side by side), `/account/security` (MFA setup/enable/disable). Tailwind/Vite assets
must be built (`npm ci && npm run build`) before the app can serve a page — the `Containerfile` now
does this in a separate build stage. The JSON API (`routes/api.php`) is unchanged and still exists
for any future SPA/mobile client.

## Student credential model: registration number + national ID (deviates from the source spec)

As of 2026-09-30, at the user's explicit request, students do **not** self-register. There is no
`POST /auth/register`, no email OTP, no chosen password. Instead, `POST /admin/students`
provisions a student's login in the same call that imports them: **registration number is the
username, national ID number is the password.** A student can sign in the instant the university
imports them.

**This is a deliberate security tradeoff, not a neutral simplification, and it was flagged as such
when implemented.** A self-chosen password, verified once via a one-time code sent to an email only
the student should control, proves the person logging in both knows a secret *and* controls that
inbox. A national ID number is not a secret in the same sense — it can be known to family members,
past employers, or leaked via other documents/systems, and it cannot be changed if compromised
(unlike a password). Anyone who knows a student's registration number and national ID can vote as
them, and the system provides no independent signal (like email possession) to catch that. This
materially weakens the "one verified student = one secret ballot" guarantee the rest of the system
(anonymous ballots, one-vote locking, audit trail) was built around. If this system is used for a
real election, treat this as an open risk requiring its own mitigation (e.g., a supervised/in-person
voting model, or reintroducing a second factor) rather than an accepted final design.

Practically: `student_accounts.password_hash` now stores `argon2id(national_id)` instead of
`argon2id(self-chosen password)` — same hashing function, same login code path (`a.login`), same
`verifyPassword` comparison; only *what* is hashed changed. `email_verified` is set unconditionally
true at import time since there is no more verification step. SMTP config
(`SMTP_HOST`/`SMTP_USER`/`SMTP_PASSWORD`/`SMTP_FROM`/`SMTP_PORT`) and the whole OTP send/verify code
path (`secureOTP`, `sendOTP`, `verifyOTP`, `POST /auth/register`, `POST /auth/verify-otp`) have been
deleted, not just disabled.

## Present in the Go backend (repository root)

- Go/Gin API, PostgreSQL schema, Redis rate-limit state, Argon2id password hashing, official-register matching, session cookies, CSRF checks and eligibility checks.
- Atomic anonymous ballot submission, per-election participation uniqueness, receipt reference and aggregate result sealing.
- Fixed 06:00–23:59 election-day policy in the university timezone; automatic closure, sealed result generation, and 24-hour public result window.
- Public result totals hidden until sealed; student receipt response omits exact submission time; ballot timestamp bucket is hour-granularity.
- Candidate and position configuration locked by PostgreSQL triggers after voting opens.
- Voter eligibility is locked by migration 003 once voting opens.
- Container deployment scaffold with Nginx gateway and database/cache isolated from host-published ports.
- Distributed Redis rate limits for API/auth/ballot traffic and login throttling.
- Cryptographically generated session tokens, session rotation, account-state rechecks, DB/Redis readiness endpoint and serialized hash-chained audit records for accepted ballots and sealed results.
- Optional non-owner voting-app and admin-reader database roles plus an example role-login setup.
- API process and dependency readiness endpoints, container health checks, and project-isolated Podman service names.
- Session/CSRF cookies set `SameSite=Strict` explicitly (`setAuthCookies`/`clearAuthCookies`).
- Internal admin write endpoints (behind `a.internalAdmin`, i.e. `X-Internal-Admin-Key`, never a
  browser session) for the election-management staff tool to actually configure an election end to
  end: `POST /admin/students` (official register import/refresh, provisions login — see "Student
  credential model" above), `POST /admin/elections`, `POST /admin/elections/:id/positions` +
  `PATCH`/`DELETE`, `POST /admin/positions/:id/candidates` + `PATCH`, `PATCH
  /admin/candidates/:id/approve|reject`, `POST /admin/elections/:id/eligibility` (bulk set). Before
  this change there was no way to populate the student register, create an election, or add a
  candidate through the API at all — only direct SQL could do it.
- In-app notifications are now created automatically, not just readable: "voting is now open" and
  "results are available" are broadcast to every eligible student when the election engine advances
  the election's state, and "your ballot has been successfully submitted" is written inside the same
  transaction that accepts the ballot.
- Unit tests for the pure logic (`cmd/api/main_test.go`): password hashing/verification, receipt
  format and uniqueness, hash determinism, turnout percentage.
- `PATCH /admin/elections/:id` (migration 004 + `adminUpdateElection`) so staff can correct an
  election's name/date before it opens, mirroring the existing position/candidate update pattern.
- `GET /admin/elections` (list, for the admin UI's election picker), `GET /admin/elections/:id/positions`
  and `GET /admin/elections/:id/candidates` (admin-scoped candidate list including `PENDING`/`REJECTED`,
  unlike the public candidate endpoints which only ever show `APPROVED`) — added so `admin-site`'s
  Blade UI never has to fall back to the public API or guess at IDs.
- **Fixed a pre-existing bug that broke student login, the public election-status endpoint, ballot
  participation tracking, and the automatic election engine** — every one of them silently, since
  each only failed on the success path, not the "not found" path that ordinary testing exercises.
  Root cause: `gorm.io/gorm v1.25.12`'s `.Raw(...).Scan(dest)` does not reliably invoke `uuid.UUID`'s
  own `Scan()` method for a struct field (or bare variable) of type `uuid.UUID` — it hits `sql: Scan
  error on column index 0, name "id": converting driver.Value type string ("...") to a uint8` any
  time the query actually returns a row with a real UUID. Wrapping the destination in a one-field
  struct does **not** fix it (an earlier pass through this file believed it did; it was never
  re-verified against a live database and was wrong). It affected: `a.auth` (the session middleware
  every authenticated student request goes through — this is why no student session lookup had ever
  actually worked end-to-end before this pass), `login` (returning "Invalid credentials" for
  genuinely correct credentials, indistinguishable from an actual wrong password), `status` (the
  public election-status endpoint returning 404 for elections that exist), `submitBallot`'s
  re-voting-attempt detection, `adminImportStudents`, `advanceElections` (the goroutine that opens
  and closes elections and triggers result sealing — every election's `id` failed to scan, which
  would have meant elections never opened or closed automatically), and `sealResults` (candidate/
  position IDs inside the permanently hash-sealed result JSON). **Fixed everywhere in the file** by
  scanning the uuid column as `string` and parsing it with `uuid.Parse` afterward, never scanning
  directly into a `uuid.UUID`-typed destination. Verified live end-to-end post-fix: real student
  login now returns a session/CSRF token and the authenticated dashboard renders real data.
  **Convention going forward: never let a raw GORM query scan a column into a `uuid.UUID`-typed
  variable or struct field, bare or wrapped — scan into `string` and call `uuid.Parse`.**

- **Fixed a second, unrelated bug that made `canVote` always reject real votes, and emptied the
  integrity hash from every results response.** `canVote` (the eligibility gate used by `GET
  .../eligibility` and `GET .../ballot` — the checks the student UI runs before showing "Vote now"
  or the ballot form) selected `e.opens_at,e.closes_at` into struct fields named `Opens`/`Closes`
  with no column alias. GORM matches raw-query columns to struct fields by name; `Opens` does not
  match `opens_at`, so both fields silently stayed at Go's zero value
  (`0001-01-01T00:00:00Z`) instead of the real times. Every call then failed the
  `now.Before(x.Opens) || !now.Before(x.Closes)` check and returned "Voting is not currently open" —
  **regardless of whether the election was actually open** — which is exactly why eligibility looked
  broken from the student side even with a genuinely open election and a correctly-eligible student.
  `submitBallot` itself was unaffected (it re-checks the window with its own `WHERE ... now() >=
  opens_at AND now() < closes_at` directly in SQL, not via this struct), so this never put ballot
  integrity at risk — it only blocked the student-facing pre-check. The same missing-alias mistake
  also hit `results()`/`adminResults()`: `result_hash` was selected into a field named `Hash` with no
  alias, so `integrity_hash` would have come back empty in every results response once an election
  actually sealed (not yet observed before this pass, since no election had closed yet). Fixed by
  adding the missing aliases (`opens_at AS opens`, `closes_at AS closes`, `result_hash AS hash`).
  Verified live end-to-end: a real student (eligible, correct national ID) now gets `{"eligible":
  true}`, successfully loads the ballot, and `POST .../ballots` returns a receipt reference — all
  confirmed by reading the resulting rows directly out of Postgres (anonymous `ballots`/
  `ballot_selections` rows with no student_id, `voter_participation.has_voted=true`). **Convention
  going forward: every raw-SQL struct scan in this file must alias snake_case columns to exactly
  match the Go field name — this is the second time a silent column/field mismatch has caused a
  student-facing feature to fail 100% of the time while returning a plausible-looking error message
  instead of surfacing the real cause. Worth auditing every `.Scan(&struct{...})` call in this file
  after any further changes, not just the one being touched.**
- Also fixed: several list endpoints (`positions`, `candidates`, `positionCandidates`, `notifications`,
  `audit`, `adminListElections`, `adminCandidates`, and the notifications field inside `dashboard`)
  returned a bare JSON `null` instead of `[]` whenever the underlying query matched zero rows — a Go
  nil-slice-marshals-to-`null` quirk. The student-site frontend called `.map()` on these responses
  without a null-guard, so "no positions yet" or "no notifications yet" crashed the page with
  `Cannot read properties of null (reading 'map')` instead of rendering an empty list. Fixed on both
  sides: a new `jsonRows()` helper normalizes nil slices to `[]` before responding (Go), and
  `student-site/app.js` now guards every list consumed from the API with `(x||[])` as well, so a
  future endpoint with the same mistake degrades to an empty list instead of a crash.

## Known simplifications (intentional, not bugs)

- The `students` table's CHECK constraints only ever allow `registration_status='REGISTERED'` and
  `account_status='ACTIVE'`. A student who leaves that population is removed by deleting the row
  (there is no admin delete endpoint yet — see below), not by flipping a status flag. This means the
  distinct spec messages "no official record" vs. "not currently registered/active" collapse to the
  same generic decline in practice, which is a defensible privacy choice but is not literally what
  section 7's failure-message table shows.
- The public `GET /elections/:id/turnout` route returns 404 while the election is `OPEN` (only the
  internal admin route serves turnout during voting). The specification's own API table lists student
  turnout as "allowed aggregate" access, so this is arguably stricter than required; left as-is since
  it only reduces information exposure, but flagging it in case product wants turnout visible to
  students while voting is open.
- Result sealing writes `status='SEALED'` directly; the `GENERATED` value in the `result_status` enum
  is defined but never used as an intermediate state, since the whole compute-hash-and-insert sequence
  already happens inside one transaction.
- There is no admin endpoint to deactivate/remove a student (no `ON DELETE CASCADE` from
  `student_accounts`/`sessions`/`voter_eligibility`/`voter_participation`/`notifications` back to
  `students`, so a raw `DELETE` would currently fail once a student has any activity). Left out of
  this pass; needs a decision on retention before adding it.
- `POST /auth/register` and `POST /auth/verify-email`/`verify-otp` from the spec's endpoint table do
  not exist at all any more — see "Student credential model" above for why, and for the security
  tradeoff that decision carries.

## Portal separation and the landing page

The student portal (`student-site/`) and admin portal (`admin-site/`) were already architecturally
separate — different applications, different origins, different account tables
(`student_accounts` vs. `admin_accounts`), different session/cookie mechanisms (Go's own
`src_session` cookie vs. Laravel's session guard), different login forms (registration number +
password vs. staff email + password + optional MFA) — nothing about them was ever shared. What was
missing was a single, obvious entry point stating that plainly and linking to both.

`student-site/app.js`'s `showWelcome()` (shown at `/`, and whenever a visitor isn't signed in) is
now that landing page: two cards, "Student portal" (sign in with registration number + national ID,
calls Go's `/api/v1/auth/login` directly — no registration step) and "Admin portal" (a plain link to
`window.SRC_ADMIN_URL`, opening the separate Laravel
app — no shared token, session, or form). `window.SRC_ADMIN_URL` follows the same override pattern
already used by `window.SRC_API` (see `index.html`'s commented example); both default to
same-origin-relative paths for the bundled nginx setup, and must be overridden per-deployment if
the admin portal lives on a different host (which, per the spec's network-isolation requirement, it
normally will).

## Still needed for the complete platform

- Responsive student voting website/PWA: login, dashboard, candidate profiles, ballot review/confirmation, receipt and results UI. (`student-site/` is a static scaffold that already calls the Go API correctly and now has a proper dual-portal landing page and login; it still needs real UI work beyond that for the voting flow screens.)
- `admin-site`'s `Containerfile` builds assets and runs `php artisan serve` directly — fine for development, not a production deployment (no PHP-FPM/process manager, no TLS). It is also deliberately not wired into `deploy/nginx.conf`'s public gateway; production needs its own network-isolated (university/VPN-only) exposure path.
- `admin-site`'s Blade UI has no CSRF-safe file upload for student import/eligibility (it's paste-a-list-into-a-textarea, now `registration_number,first_name,last_name,university_email,national_id,faculty,programme,level`); a real deployment likely wants a CSV file upload and/or direct SIS integration instead — and, given the security tradeoff above, ideally a governance decision on whether national ID is really the right long-term credential before a real election.
- Production HTTPS on Nginx, university hostnames, private network/VPN policy, secrets manager, TLS for internal links as appropriate, encrypted volumes/backups and operational monitoring/alerts.
- Separate voting, result, admin and migration DB credentials still need to be provisioned from the optional roles; local compose credentials are development-only.
- External append-only audit sink, alert routing, anomaly monitoring, and incident response automation.
- Formal code/security/privacy review, SAST/DAST/dependency/container scans, concurrency and load exercises (the one-vote-under-concurrency guarantee is enforced by the code and covered conceptually by the spec, but is not yet exercised by an automated concurrency test against a real database), restore and incident drills, accessibility and student acceptance review, and a full mock election.
- Governance confirmation of the official student register, eligibility rules, timezone, election schedule, retention and incident procedures.
- "Voting opens tomorrow" pre-open reminder notification (section 15) is not implemented; only the
  open/closed/results-available/ballot-submitted notifications are.

## Production status

This repository is a backend implementation scaffold, not a production-approved election system. The security and operational gates above must pass before a real SRC election.
