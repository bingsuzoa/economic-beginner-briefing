ALTER TABLE economic_events
  ADD COLUMN value_type VARCHAR(16),
  ADD COLUMN base_currency VARCHAR(3),
  ADD COLUMN quote_currency VARCHAR(3),
  ADD COLUMN base_amount INTEGER,
  ADD COLUMN milestone_type VARCHAR(32),
  ADD COLUMN milestone_period_value INTEGER,
  ADD COLUMN milestone_period_unit VARCHAR(16),
  ADD COLUMN milestone_reference_date DATE;
