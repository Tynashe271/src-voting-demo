-- The student login credential (registration_number + national_id) is set by the university import,
-- not chosen by the student -- and a national ID is not a secret in the way a self-chosen password
-- is. Forcing a change on first login means the import-time credential stops being the one that
-- actually protects the account the moment a real student signs in with it.
ALTER TABLE student_accounts ADD COLUMN must_change_password boolean NOT NULL DEFAULT true;
