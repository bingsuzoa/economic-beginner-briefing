INSERT INTO economic_slots (slot_key, name, active)
VALUES ('EXCHANGE_RATE_DIRECTION', '환율 방향', true)
ON CONFLICT (slot_key) DO NOTHING;

INSERT INTO economic_slot_values (slot_id, value_key, name, active)
SELECT s.id, v.value_key, v.name, true
FROM economic_slots s
CROSS JOIN (VALUES ('RISING', '상승'), ('FALLING', '하락'), ('FLAT', '보합')) v(value_key, name)
WHERE s.slot_key = 'EXCHANGE_RATE_DIRECTION'
ON CONFLICT (slot_id, value_key) DO NOTHING;

UPDATE economic_events e
SET slot_id = direction.id,
    slot_value_id = direction_value.id
FROM economic_slots old_slot,
     economic_slot_values old_value,
     economic_slots direction,
     economic_slot_values direction_value
WHERE e.slot_id = old_slot.id
  AND e.slot_value_id = old_value.id
  AND old_slot.slot_key = 'EXCHANGE_RATE_LEVEL'
  AND old_value.value_key IN ('RISING', 'FALLING', 'STABLE', 'FLAT')
  AND direction.slot_key = 'EXCHANGE_RATE_DIRECTION'
  AND direction_value.slot_id = direction.id
  AND direction_value.value_key = CASE old_value.value_key WHEN 'STABLE' THEN 'FLAT' ELSE old_value.value_key END;

DELETE FROM economic_slot_values v
USING economic_slots s
WHERE v.slot_id = s.id
  AND s.slot_key = 'EXCHANGE_RATE_LEVEL'
  AND v.value_key IN ('RISING', 'FALLING', 'STABLE', 'FLAT');

CREATE TABLE economic_principle_chunks (
  id BIGSERIAL PRIMARY KEY,
  content TEXT NOT NULL,
  concepts TEXT NOT NULL DEFAULT '',
  from_concept VARCHAR(255),
  to_concept VARCHAR(255),
  mechanism VARCHAR(255),
  source_type VARCHAR(32) NOT NULL,
  source_title VARCHAR(512) NOT NULL,
  source_section VARCHAR(512),
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_economic_principle_chunks_active ON economic_principle_chunks(active);
