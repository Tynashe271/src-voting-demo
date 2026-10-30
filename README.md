# SRC Student Electronic Voting System - backend

Go/Gin backend for the supplied SRC voting specification. It uses PostgreSQL as the authoritative transactional store and Redis for rate-limit/session-adjacent state.

There is no student self-registration or email-OTP verification step. `POST /api/v1/admin/students`
provisions a student's login in the same call that imports them into the official register:
**registration number is the username, national ID number is the password.** See
[SPECIFICATION_STATUS.md](SPECIFICATION_STATUS.md) for why this was changed from the original
spec's design and the security tradeoff that comes with it.

This repository contains the backend scaffold only. The supplied specification also requires a separate student PWA and a separate Laravel staff portal; see [SPECIFICATION_STATUS.md](SPECIFICATION_STATUS.md) for the full implementation map and production gates.

## Run locally

1. Copy `.env.example` to `.env` and replace `SESSION_SECRET` with a random value at least 32 bytes long.
2. Start the local stack: `podman compose up -d postgres redis api nginx` (the example HTTP gateway is at port 8080; local-only PostgreSQL and Redis ports 5437/6382 are mapped for development tools).
3. Apply migrations: `podman compose --profile tools run --rm migrate` (the runner records applied filenames in `schema_migrations`).
4. Access the API through `http://localhost:8080` (the API is already started by compose). For local source development, use `go run ./cmd/api` after stopping the compose API container.

`POST /api/v1/elections/:id/ballots` is intentionally the only ballot-write endpoint. Ballots contain no student identifier, while `voter_participation` uses a unique student/election constraint inside a transaction to enforce one ballot. Cookie-authenticated state-changing requests require the `X-CSRF-Token` returned by login.

Election times must be configured as 06:00 and 23:59 on the scheduled date in `UNIVERSITY_TIMEZONE`; the database rejects schedules outside that window. Use a separate internal admin service key in `.env`. Never expose the internal key to a browser. The compose credentials and HTTP gateway are local-development defaults, not production configuration. A live election requires HTTPS, restricted service/database access, separate student/admin sites and authentication, tested backups, and the independent security, privacy, load, and governance reviews in the supplied requirements.

`/healthz` checks process health and `/readyz` checks PostgreSQL and Redis. The migration command applies files in order. `deploy/roles.example.sql` shows how to provision login roles from protected credentials after applying the migration; the migration creates the `voting_app` and `voting_admin_reader` privilege roles. Use a non-owner runtime login for production.

## Internal admin API

Everything under `/api/v1/admin/*` requires the `X-Internal-Admin-Key` header to match `INTERNAL_ADMIN_API_KEY`. This is a service-to-service credential for the election-management portal, not a staff login, and must never be sent from a browser. It is how an election is actually configured:

1. `POST /api/v1/admin/students` — import/refresh the official student register (bulk); each entry
   requires a `national_id`, which becomes that student's login password immediately (hashed the
   same way a self-chosen password would be — nothing is stored or logged in plain text).
2. `POST /api/v1/admin/elections` — create an election for a given `election_date`; opens/closes at 06:00/23:59 in `UNIVERSITY_TIMEZONE` are computed automatically.
3. `POST /api/v1/admin/elections/:id/positions`, then `PATCH`/`DELETE /api/v1/admin/positions/:id` to edit or remove one before voting opens.
4. `POST /api/v1/admin/positions/:id/candidates`, then `PATCH /api/v1/admin/candidates/:id` to edit, and `PATCH /api/v1/admin/candidates/:id/approve` or `/reject` to decide whether they appear on the ballot.
5. `POST /api/v1/admin/elections/:id/eligibility` — bulk set which imported students may vote in that election.

All of the above are rejected by PostgreSQL triggers once the election leaves `UPCOMING`, so there is no separate application-level lock to keep in sync with the database. `GET /api/v1/admin/elections/:id/turnout|results|timing` and `GET /api/v1/admin/audit` remain read-only.
