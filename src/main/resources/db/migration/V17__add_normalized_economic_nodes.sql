CREATE TABLE economic_slots (
  id BIGSERIAL PRIMARY KEY,
  slot_key VARCHAR(128) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE economic_slot_values (
  id BIGSERIAL PRIMARY KEY,
  slot_id BIGINT NOT NULL REFERENCES economic_slots(id),
  value_key VARCHAR(128) NOT NULL,
  name VARCHAR(128) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (slot_id, value_key)
);

ALTER TABLE economic_events
  ADD COLUMN node_kind VARCHAR(16),
  ADD COLUMN scope_key VARCHAR(128),
  ADD COLUMN slot_id BIGINT REFERENCES economic_slots(id),
  ADD COLUMN slot_value_id BIGINT REFERENCES economic_slot_values(id),
  ADD COLUMN ended_at DATE;

CREATE INDEX idx_economic_events_active_state
  ON economic_events (scope_key, subject_key, slot_id, event_date DESC)
  WHERE node_kind = 'STATE' AND ended_at IS NULL;

INSERT INTO economic_slots (slot_key, name) VALUES
('RATE_DECISION', '기준금리 결정'),
('POLICY_STANCE', '정책 기조'),
('INFLATION_STATUS', '물가 상태'),
('EXCHANGE_RATE_LEVEL', '환율 수준'),
('MARKET_DIRECTION', '시장 방향');

INSERT INTO economic_slot_values (slot_id, value_key, name)
SELECT s.id, v.value_key, v.name FROM economic_slots s CROSS JOIN (VALUES
  ('RATE_HIKE', '인상'), ('RATE_HOLD', '동결'), ('RATE_CUT', '인하')
) AS v(value_key, name) WHERE s.slot_key = 'RATE_DECISION';

INSERT INTO economic_slot_values (slot_id, value_key, name)
SELECT s.id, v.value_key, v.name FROM economic_slots s CROSS JOIN (VALUES
  ('HAWKISH', '매파적'), ('DOVISH', '비둘기파적'), ('CAUTIOUS_ON_CUT', '인하에 신중'), ('NEUTRAL', '중립')
) AS v(value_key, name) WHERE s.slot_key = 'POLICY_STANCE';

INSERT INTO economic_slot_values (slot_id, value_key, name)
SELECT s.id, v.value_key, v.name FROM economic_slots s CROSS JOIN (VALUES
  ('ABOVE_TARGET', '목표 상회'), ('NEAR_TARGET', '목표 근접'), ('BELOW_TARGET', '목표 하회')
) AS v(value_key, name) WHERE s.slot_key = 'INFLATION_STATUS';

INSERT INTO economic_slot_values (slot_id, value_key, name)
SELECT s.id, v.value_key, v.name FROM economic_slots s CROSS JOIN (VALUES
  ('RISING', '상승'), ('FALLING', '하락'), ('STABLE', '보합')
) AS v(value_key, name) WHERE s.slot_key IN ('EXCHANGE_RATE_LEVEL', 'MARKET_DIRECTION');
