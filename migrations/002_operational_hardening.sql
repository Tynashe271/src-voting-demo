-- Apply after 001_initial.sql. Keeps schedule/configuration frozen while allowing
-- the election engine to advance the lifecycle state.
CREATE OR REPLACE FUNCTION prevent_election_timing_change_after_open() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
  IF OLD.status <> 'UPCOMING' AND (
    NEW.opens_at IS DISTINCT FROM OLD.opens_at OR
    NEW.closes_at IS DISTINCT FROM OLD.closes_at OR
    NEW.election_date IS DISTINCT FROM OLD.election_date OR
    NEW.name IS DISTINCT FROM OLD.name OR
    NEW.configuration_hash IS DISTINCT FROM OLD.configuration_hash
  ) THEN
    RAISE EXCEPTION 'election timing and configuration are locked after opening';
  END IF;
  RETURN NEW;
END; $$;

