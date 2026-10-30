-- Adds a DISQUALIFIED candidate status, distinct from REJECTED (never approved) and APPROVED
-- (on the ballot, in good standing). Staff can disqualify a candidate even after the election has
-- opened: votes already cast for them are never altered (ballots are immutable and anonymous),
-- but sealResults excludes a disqualified candidate's votes from the sealed, public tally.
ALTER TABLE candidates DROP CONSTRAINT candidates_approval_status_check;
ALTER TABLE candidates ADD CONSTRAINT candidates_approval_status_check
  CHECK (approval_status IN ('PENDING','APPROVED','REJECTED','DISQUALIFIED'));

CREATE OR REPLACE FUNCTION prevent_candidate_change_after_open() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE election_state election_status;
DECLARE affected_position uuid;
BEGIN
  affected_position := CASE WHEN TG_OP='DELETE' THEN OLD.position_id ELSE NEW.position_id END;
  SELECT e.status INTO election_state FROM elections e JOIN positions p ON p.election_id=e.id WHERE p.id=affected_position;
  -- A pure disqualification (approval_status -> DISQUALIFIED, nothing else about the row changed)
  -- is the one candidate-table write allowed once the election is OPEN. Every other write --
  -- including disqualification itself once the election has CLOSED and results may already be
  -- sealed -- stays locked, same as before this migration.
  IF election_state = 'OPEN' AND TG_OP = 'UPDATE' AND NEW.approval_status = 'DISQUALIFIED' AND OLD.approval_status <> 'DISQUALIFIED'
     AND NEW.position_id = OLD.position_id AND NEW.name = OLD.name
     AND NEW.photo_url IS NOT DISTINCT FROM OLD.photo_url
     AND NEW.biography IS NOT DISTINCT FROM OLD.biography
     AND NEW.manifesto IS NOT DISTINCT FROM OLD.manifesto THEN
    RETURN NEW;
  END IF;
  IF election_state IN ('OPEN','CLOSED','RESULTS_VISIBLE','ARCHIVED') THEN
    RAISE EXCEPTION 'candidate configuration is locked after opening';
  END IF;
  RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END; $$;
