-- Separates "results computed and tamper-proof-sealed at close" (result_batches, already
-- immutable) from "results actually revealed to students" (this new table). Previously both
-- happened in the same instant via sealResults()'s visible_from=now(), so there was no way for
-- staff to review the sealed totals/charts before students could see them -- the public and admin
-- results reads used the exact same now()-windowed query. result_batches cannot be UPDATEd after
-- insert (result_batches_immutable trigger), so "publish" can't be an update to that row; it's a
-- second, equally immutable, insert-only record instead.
CREATE TABLE result_publications (
  election_id uuid PRIMARY KEY REFERENCES elections(id),
  published_at timestamptz NOT NULL DEFAULT now(),
  visible_until timestamptz NOT NULL
);
CREATE TRIGGER result_publications_immutable BEFORE UPDATE OR DELETE ON result_publications
  FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
REVOKE UPDATE, DELETE ON result_publications FROM PUBLIC;

DO $$ BEGIN
  IF EXISTS (SELECT FROM pg_roles WHERE rolname='voting_app') THEN
    GRANT SELECT, INSERT ON result_publications TO voting_app;
  END IF;
  IF EXISTS (SELECT FROM pg_roles WHERE rolname='voting_admin_reader') THEN
    GRANT SELECT ON result_publications TO voting_admin_reader;
  END IF;
  IF EXISTS (SELECT FROM pg_roles WHERE rolname='voting_admin_portal') THEN
    GRANT SELECT ON result_publications TO voting_admin_portal;
  END IF;
END $$;
