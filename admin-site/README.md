# SRC Elections Admin (Laravel)

Pure admin-configuration + read-only-reporting portal for the SRC voting system. It has no
student, ballot, election, position, candidate, eligibility, or result data of its own — every
one of those operations is a call through `App\Services\VotingApiClient` into the Go voting
backend's internal admin API (`../cmd/api`). See `../SPECIFICATION_STATUS.md` for the full
architecture writeup.

## Local setup (against a real Go backend + Postgres)

This app is only useful running alongside the Go backend at the repo root. From the repo root:

1. `podman compose up -d postgres redis` — brings up the shared database Go's migrations own.
2. `go run ./cmd/migrate` (with `DATABASE_URL` pointed at that Postgres) — applies `migrations/`,
   including `004_admin_accounts_staff_fields.sql` which this app depends on.
3. Provision the `voting_admin_portal_login` Postgres role from `deploy/roles.example.sql` — this
   is the least-privilege credential this app's `voting` connection uses. Also create a separate,
   more privileged credential for `DB_VOTING_PROVISION_*` (used only by the one-off
   `admin:create-account` command below, never by the running app).
4. `go run ./cmd/api` — the Go API this app talks to.

Then in `admin-site/`:

5. Copy `.env.example` to `.env` and fill in: `DB_VOTING_*` (the role from step 3),
   `DB_VOTING_PROVISION_*` (step 3's second credential), `VOTING_API_BASE_URL` (Go's
   `/api/v1/admin`, e.g. `http://127.0.0.1:8080/api/v1/admin` for local dev or
   `http://api:8080/api/v1/admin` inside the compose network), and `VOTING_API_KEY` (must exactly
   match the repo-root `.env`'s `INTERNAL_ADMIN_API_KEY` — this is a service-to-service secret,
   never a staff login, and must never be sent to a browser).
6. `composer run setup` — installs dependencies, generates `APP_KEY`, creates the local
   `database/database.sqlite` (this app's *own* framework tables only: sessions/cache/jobs — never
   voting-domain data), runs its framework migrations, and builds the Tailwind/Vite assets.
7. `php artisan admin:create-account you@university.ac.zw your-staff-id` — the *only* way to
   create the first staff account; prints a one-time temporary password. The runtime
   `voting_admin_portal_login` role deliberately cannot `INSERT` into `admin_accounts`, so this
   uses the separate provisioning credential from step 3/5 instead.
8. `php artisan serve` and sign in at `/login` with the account from step 7. Set up MFA
   immediately at `/account/security` — it's optional at login but strongly recommended.

## What's real vs. what's in this app

- **Owned by Go** (this app never writes these tables directly): `students`, `elections`,
  `positions`, `candidates`, `voter_eligibility`, `voter_participation`, `ballots`,
  `ballot_selections`, `voting_receipts`, `result_batches`, `notifications`, and the hash-chained
  `audit_logs` (ballot/result integrity — read-only here via `VotingApiClient::audit()`).
- **Owned by Go's schema, used only by this app**: `admin_accounts` (staff identity/MFA — Go's own
  code never reads or writes it) and `admin_audit_logs` (this app's own, *not* tamper-evident,
  admin-action log — deliberately separate from Go's ballot/result trail).
- **Owned entirely by this app** (its own `default`/sqlite connection): only framework
  plumbing — sessions, cache, queued jobs. Nothing voting-related.

## Tests

`php artisan test` — all controllers that talk to Go are tested with `Http::fake()`, so the suite
does not need a live Go backend or Postgres to run (`tests/Concerns/PreparesVotingConnection.php`
builds a throwaway sqlite schema for `admin_accounts`/`admin_audit_logs` instead). The steps above
are for exercising the *real* integration end to end, which the automated suite intentionally
does not depend on.
