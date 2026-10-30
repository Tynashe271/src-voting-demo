-- Adds the columns/table the separate Laravel admin-management portal needs for its own staff
-- identity (login + MFA) and admin-action logging. Go's own application code never reads or
-- writes any of this; it exists here only so Go's migration tool remains the single schema owner
-- of every table in the shared database, per the project's own "Go migrations own this schema"
-- principle. See admin-site's AdminAccount/AdminAuditLog models for the only readers/writers.
ALTER TABLE admin_accounts ADD COLUMN staff_identifier text UNIQUE;
ALTER TABLE admin_accounts ADD COLUMN mfa_secret text;

CREATE TABLE admin_audit_logs (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor_service text NOT NULL,
  action text NOT NULL,
  resource text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Deliberately not hash-chained/tamper-evident like audit_logs: this is a lower-stakes convenience
-- log for admin-portal UI actions (login, MFA setup, which endpoint was called). Ballot/result
-- integrity is fully covered by the existing audit_logs table and stays the single source of truth
-- for that; this table must never receive ballot- or result-related entries.
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='voting_admin_portal') THEN CREATE ROLE voting_admin_portal NOLOGIN; END IF;
END $$;
GRANT USAGE ON SCHEMA public TO voting_admin_portal;
GRANT SELECT ON elections, positions, candidates, result_batches, voter_eligibility, voter_participation, audit_logs TO voting_admin_portal;
GRANT SELECT ON admin_accounts TO voting_admin_portal;
GRANT UPDATE (mfa_enabled, mfa_secret) ON admin_accounts TO voting_admin_portal;
GRANT SELECT, INSERT ON admin_audit_logs TO voting_admin_portal;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO voting_admin_portal;
