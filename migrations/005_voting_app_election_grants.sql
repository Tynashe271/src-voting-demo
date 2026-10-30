-- Migration 001 created the least-privilege `voting_app` role (used by the API's own DATABASE_URL
-- in production, per roles.example.sql) but never granted it any access to elections, positions, or
-- candidates — only the read-only voting_admin_reader/voting_admin_portal roles got SELECT on them.
-- This went unnoticed because local/dev compose runs the API against the Postgres owner role, not
-- voting_app_login. Every public read path (status/positions/candidates/ballot) and every internal
-- admin write endpoint (create/update election, add/edit/remove position, add/edit/approve/reject
-- candidate) runs through the same a.db connection, so voting_app needs real access to these tables.
GRANT SELECT, INSERT, UPDATE ON elections TO voting_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON positions TO voting_app;
GRANT SELECT, INSERT, UPDATE ON candidates TO voting_app;
