INSERT INTO economic_slots (slot_key, name)
VALUES ('CORPORATE_STRUCTURE_STATUS', '기업 구조개편 상태');

INSERT INTO economic_slot_values (slot_id, value_key, name)
SELECT s.id, v.value_key, v.name
FROM economic_slots s
CROSS JOIN (VALUES
  ('SPLIT_ANNOUNCED', '분할 발표'),
  ('SPLIT_APPROVED', '분할 승인'),
  ('SPLIT_COMPLETED', '분할 완료'),
  ('SPLIT_CANCELLED', '분할 취소')
) v(value_key, name)
WHERE s.slot_key = 'CORPORATE_STRUCTURE_STATUS';
