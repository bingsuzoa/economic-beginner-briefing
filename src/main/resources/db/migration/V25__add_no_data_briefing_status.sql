ALTER TABLE daily_briefings DROP CONSTRAINT IF EXISTS daily_briefings_status_check;
ALTER TABLE daily_briefings ADD CONSTRAINT daily_briefings_status_check
  CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'NO_DATA'));
