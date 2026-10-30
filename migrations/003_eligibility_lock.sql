-- Freeze the eligibility register once voting opens.
CREATE OR REPLACE FUNCTION prevent_eligibility_change_after_open() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target_election uuid;
DECLARE election_state election_status;
BEGIN
  target_election := CASE WHEN TG_OP='DELETE' THEN OLD.election_id ELSE NEW.election_id END;
  SELECT status INTO election_state FROM elections WHERE id=target_election;
  IF election_state IN ('OPEN','CLOSED','RESULTS_VISIBLE','ARCHIVED') THEN
    RAISE EXCEPTION 'voter eligibility is locked after election opening';
  END IF;
  RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END; $$;
CREATE TRIGGER voter_eligibility_locked BEFORE INSERT OR UPDATE OR DELETE ON voter_eligibility FOR EACH ROW EXECUTE FUNCTION prevent_eligibility_change_after_open();
