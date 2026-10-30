CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE IF NOT EXISTS schema_migrations (version text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());
CREATE TYPE election_status AS ENUM ('UPCOMING','OPEN','CLOSED','RESULTS_VISIBLE','ARCHIVED');
CREATE TYPE result_status AS ENUM ('GENERATED','SEALED');
CREATE TABLE students (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), registration_number text UNIQUE NOT NULL, first_name text NOT NULL, last_name text NOT NULL, university_email text UNIQUE NOT NULL, faculty text, programme text, level text, registration_status text NOT NULL CHECK(registration_status='REGISTERED'), account_status text NOT NULL DEFAULT 'ACTIVE' CHECK(account_status='ACTIVE'), created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE student_accounts (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), student_id uuid UNIQUE NOT NULL REFERENCES students(id), password_hash text NOT NULL, email_verified boolean NOT NULL DEFAULT false, email_verified_at timestamptz, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE sessions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), student_id uuid NOT NULL REFERENCES students(id), token_hash bytea UNIQUE NOT NULL, expires_at timestamptz NOT NULL, revoked_at timestamptz, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE elections (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text NOT NULL, election_date date NOT NULL, opens_at timestamptz NOT NULL, closes_at timestamptz NOT NULL, status election_status NOT NULL DEFAULT 'UPCOMING', configuration_hash text, results_visible_from timestamptz, results_visible_until timestamptz, created_at timestamptz NOT NULL DEFAULT now(), CHECK(closes_at > opens_at));
CREATE TABLE positions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), election_id uuid NOT NULL REFERENCES elections(id), name text NOT NULL, description text, display_order integer NOT NULL, max_selections integer NOT NULL DEFAULT 1 CHECK(max_selections > 0), UNIQUE(election_id,name), UNIQUE(election_id,display_order));
CREATE TABLE candidates (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), position_id uuid NOT NULL REFERENCES positions(id), name text NOT NULL, photo_url text, biography text, manifesto text, approval_status text NOT NULL DEFAULT 'PENDING' CHECK(approval_status IN ('PENDING','APPROVED','REJECTED')));
CREATE TABLE voter_eligibility (student_id uuid NOT NULL REFERENCES students(id), election_id uuid NOT NULL REFERENCES elections(id), eligible boolean NOT NULL DEFAULT false, verified_at timestamptz, PRIMARY KEY(student_id,election_id));
CREATE TABLE voter_participation (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), student_id uuid NOT NULL REFERENCES students(id), election_id uuid NOT NULL REFERENCES elections(id), has_voted boolean NOT NULL DEFAULT false, voted_at timestamptz, UNIQUE(student_id,election_id));
-- Privacy boundary: this table deliberately has no student_id, receipt, IP or exact timestamp.
CREATE TABLE ballots (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), election_id uuid NOT NULL REFERENCES elections(id), submitted_bucket timestamptz NOT NULL DEFAULT date_trunc('hour', now()));
CREATE TABLE ballot_selections (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), ballot_id uuid NOT NULL REFERENCES ballots(id), position_id uuid NOT NULL REFERENCES positions(id), candidate_id uuid NOT NULL REFERENCES candidates(id), UNIQUE(ballot_id,candidate_id));
CREATE TABLE voting_receipts (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), participation_id uuid UNIQUE NOT NULL REFERENCES voter_participation(id), reference text UNIQUE NOT NULL, reference_hash bytea UNIQUE NOT NULL);
CREATE TABLE result_batches (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), election_id uuid UNIQUE NOT NULL REFERENCES elections(id), generated_at timestamptz NOT NULL DEFAULT now(), totals jsonb NOT NULL, result_hash text NOT NULL, status result_status NOT NULL DEFAULT 'GENERATED', visible_from timestamptz NOT NULL, visible_until timestamptz NOT NULL, archived_at timestamptz);
CREATE TABLE notifications (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), student_id uuid NOT NULL REFERENCES students(id), type text NOT NULL, message text NOT NULL, read_at timestamptz, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE admin_accounts (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email text UNIQUE NOT NULL, password_hash text NOT NULL, mfa_enabled boolean NOT NULL DEFAULT true, account_status text NOT NULL DEFAULT 'ACTIVE');
CREATE TABLE audit_logs (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, actor text NOT NULL, action text NOT NULL, resource text NOT NULL, request_id text, previous_hash text, entry_hash text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE security_events (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, category text NOT NULL, subject_hash text, details jsonb NOT NULL DEFAULT '{}'::jsonb, created_at timestamptz NOT NULL DEFAULT now());
CREATE OR REPLACE FUNCTION enforce_election_window() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE zone_name text := COALESCE(NULLIF(current_setting('app.university_timezone', true), ''), 'Africa/Harare');
BEGIN
  IF (NEW.opens_at AT TIME ZONE zone_name)::date <> NEW.election_date
     OR (NEW.closes_at AT TIME ZONE zone_name)::date <> NEW.election_date
     OR (NEW.opens_at AT TIME ZONE zone_name)::time <> TIME '06:00'
     OR (NEW.closes_at AT TIME ZONE zone_name)::time <> TIME '23:59' THEN
    RAISE EXCEPTION 'election must open at 06:00 and close at 23:59 on election_date in %', zone_name;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER elections_window_valid BEFORE INSERT OR UPDATE OF election_date,opens_at,closes_at ON elections FOR EACH ROW EXECUTE FUNCTION enforce_election_window();
-- Immutable records must be protected even if an application route is accidentally introduced later.
CREATE OR REPLACE FUNCTION reject_immutable_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'immutable election record'; END; $$;
CREATE OR REPLACE FUNCTION prevent_open_election_config_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE election_state election_status;
DECLARE target_election uuid;
BEGIN
  IF TG_OP = 'DELETE' THEN
    target_election := OLD.election_id;
  ELSE
    target_election := NEW.election_id;
  END IF;
  SELECT status INTO election_state FROM elections WHERE id=target_election;
  IF election_state IN ('OPEN','CLOSED','RESULTS_VISIBLE','ARCHIVED') THEN
    RAISE EXCEPTION 'election configuration is locked after opening';
  END IF;
  RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END; $$;
CREATE TRIGGER positions_locked_after_open BEFORE INSERT OR UPDATE OR DELETE ON positions FOR EACH ROW EXECUTE FUNCTION prevent_open_election_config_change();
CREATE OR REPLACE FUNCTION prevent_candidate_change_after_open() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE election_state election_status;
DECLARE position_id uuid;
BEGIN
  position_id := CASE WHEN TG_OP='DELETE' THEN OLD.position_id ELSE NEW.position_id END;
  SELECT e.status INTO election_state FROM elections e JOIN positions p ON p.election_id=e.id WHERE p.id=position_id;
  IF election_state IN ('OPEN','CLOSED','RESULTS_VISIBLE','ARCHIVED') THEN
    RAISE EXCEPTION 'candidate configuration is locked after opening';
  END IF;
  RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END; $$;
CREATE TRIGGER candidates_locked_after_open BEFORE INSERT OR UPDATE OR DELETE ON candidates FOR EACH ROW EXECUTE FUNCTION prevent_candidate_change_after_open();
CREATE OR REPLACE FUNCTION prevent_election_timing_change_after_open() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
  IF OLD.status <> 'UPCOMING' AND (
    NEW.opens_at IS DISTINCT FROM OLD.opens_at OR NEW.closes_at IS DISTINCT FROM OLD.closes_at OR NEW.election_date IS DISTINCT FROM OLD.election_date OR
    NEW.name IS DISTINCT FROM OLD.name OR NEW.configuration_hash IS DISTINCT FROM OLD.configuration_hash
  ) THEN
    RAISE EXCEPTION 'election timing is locked after opening';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER election_timing_locked BEFORE UPDATE ON elections FOR EACH ROW EXECUTE FUNCTION prevent_election_timing_change_after_open();
-- A receipt may confirm participation, but no exact timestamp creates a correlation aid.
CREATE OR REPLACE FUNCTION prevent_receipt_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'receipt record is immutable'; END; $$;
CREATE TRIGGER receipts_immutable BEFORE UPDATE OR DELETE ON voting_receipts FOR EACH ROW EXECUTE FUNCTION prevent_receipt_mutation();
CREATE TRIGGER ballots_immutable BEFORE UPDATE OR DELETE ON ballots FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER ballot_selections_immutable BEFORE UPDATE OR DELETE ON ballot_selections FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER result_batches_immutable BEFORE UPDATE OR DELETE ON result_batches FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER audit_logs_immutable BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER security_events_immutable BEFORE UPDATE OR DELETE ON security_events FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE OR REPLACE FUNCTION append_security_event(event_category text, event_subject_hash text, event_details jsonb DEFAULT '{}'::jsonb) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO security_events(category,subject_hash,details) VALUES (event_category,event_subject_hash,COALESCE(event_details,'{}'::jsonb));
END;
$$;
REVOKE UPDATE, DELETE ON ballots, ballot_selections, result_batches, voting_receipts, audit_logs, security_events FROM PUBLIC;
-- Optional non-owner service roles for deployments that apply this migration as a privileged owner.
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='voting_app') THEN CREATE ROLE voting_app NOLOGIN; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='voting_admin_reader') THEN CREATE ROLE voting_admin_reader NOLOGIN; END IF;
END $$;
GRANT USAGE ON SCHEMA public TO voting_app, voting_admin_reader;
GRANT SELECT, INSERT, UPDATE ON students,student_accounts,sessions,voter_eligibility,voter_participation,notifications TO voting_app;
GRANT SELECT, INSERT ON ballots,ballot_selections,voting_receipts,result_batches,audit_logs,security_events TO voting_app;
GRANT EXECUTE ON FUNCTION append_security_event(text,text,jsonb) TO voting_app;
GRANT SELECT ON elections,positions,candidates,result_batches,voter_eligibility,voter_participation,audit_logs TO voting_admin_reader;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO voting_app, voting_admin_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC;
