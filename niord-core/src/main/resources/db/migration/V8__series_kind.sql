-- Flyway migration V8: what KIND of publication a series is.
--
-- `cadence` answers "how often", and its NONE value covers two different things:
-- a series with no calendar -- eleven NCAGS editions, eight ice-service notices,
-- four editions of Dansk Fyrliste -- and a publication that came out once and
-- stopped. Reading NONE as "one-off" merges them, and the merge is not academic:
-- it is what put an eleven-issue series in the one-off list.
--
-- THE ISSUE COUNT IS USED ONCE, AND ONLY HERE. Classifying by "cadence = NONE
-- and at most one issue" is a migration rule about an estate that is already
-- written, not a rule the running system applies. After this, `kind` is read
-- rather than recomputed -- which is what makes a second issue on a one-off a
-- refusal instead of a silent reclassification of the publication.
--
-- Idempotent by construction, like V6. Hibernate may already have added the
-- column in a development database, so the ALTER is guarded on
-- information_schema; and the backfill only touches rows still holding the
-- column default, so it can never overwrite a kind somebody has since chosen.

DROP PROCEDURE IF EXISTS niord_add_column_if_absent;

DELIMITER $$
CREATE PROCEDURE niord_add_column_if_absent(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND COLUMN_NAME = p_column) THEN
        SET @sql = p_ddl;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$
DELIMITER ;

-- DEFAULT 'SCHEDULED' so the column can be NOT NULL on a populated table, and
-- because it is what a series created through the form is until told otherwise.
CALL niord_add_column_if_absent('PublicationSeries', 'kind',
    'ALTER TABLE PublicationSeries ADD COLUMN kind
         ENUM(''SCHEDULED'',''UNSCHEDULED'',''ONE_OFF'') NOT NULL DEFAULT ''SCHEDULED''');

DROP PROCEDURE niord_add_column_if_absent;

-- The one-time classification. A cadenced series is SCHEDULED and already holds
-- that from the default, so only the cadence = NONE rows are decided here.
--
-- Restricted to rows still at the default: a row that already says UNSCHEDULED
-- or ONE_OFF was classified by a human or by the importer, and this migration
-- has nothing to add to that.
UPDATE PublicationSeries s
   SET s.kind = CASE
           WHEN (SELECT COUNT(*) FROM PublicationIssue i WHERE i.series_id = s.id) > 1
                THEN 'UNSCHEDULED'
           ELSE 'ONE_OFF'
       END
 WHERE s.cadence = 'NONE'
   AND s.kind = 'SCHEDULED';
