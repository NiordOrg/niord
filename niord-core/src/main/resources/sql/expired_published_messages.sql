
SELECT
  DISTINCT m.uid
FROM
  Message m
WHERE

  m.status = 'PUBLISHED'

  -- The must be at least one associated date interval
  AND EXISTS(
    SELECT di.id FROM DateInterval di WHERE di.message_id = m.id
  )

  -- But no date interval with null or future to-date
  AND NOT EXISTS(
      SELECT di.id FROM DateInterval di WHERE di.message_id = m.id
          AND (di.toDate IS NULL OR di.toDate > :now)
  )

