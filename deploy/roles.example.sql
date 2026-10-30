-- Run as a trusted PostgreSQL administrator after migrations/001_initial.sql.
-- In psql, set credentials interactively with \prompt, then execute these statements.
\prompt 'Password for voting_app_login: ' voting_app_password
CREATE ROLE voting_app_login LOGIN PASSWORD :'voting_app_password';
GRANT voting_app TO voting_app_login;

\prompt 'Password for voting_admin_reader_login: ' voting_admin_reader_password
CREATE ROLE voting_admin_reader_login LOGIN PASSWORD :'voting_admin_reader_password';
GRANT voting_admin_reader TO voting_admin_reader_login;

-- voting_admin_portal (migrations/004) is the credential for the separate Laravel admin-management
-- portal's `voting` database connection: read-only on election/result/eligibility data, plus the
-- narrow write access it actually needs (its own MFA fields on admin_accounts, its own
-- admin_audit_logs table). It intentionally cannot INSERT into admin_accounts; see
-- SPECIFICATION_STATUS.md for how the first staff row gets provisioned.
\prompt 'Password for voting_admin_portal_login: ' voting_admin_portal_password
CREATE ROLE voting_admin_portal_login LOGIN PASSWORD :'voting_admin_portal_password';
GRANT voting_admin_portal TO voting_admin_portal_login;

-- Configure DATABASE_URL with voting_app_login for the API process.
-- Give Laravel voting_admin_portal_login for its `voting` connection (DB_VOTING_*).
-- Do not use the migration/owner credentials in either application.
