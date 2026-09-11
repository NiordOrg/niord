-- Flyway migration V16: the closing week is arithmetic, and only arithmetic.
--
--   PublicationIssue.weekToLabel
--       goes. What an edition is CALLED where its SECOND week prints is not a
--       decision anybody has to make: a window that swallowed a period the
--       publication skipped derives that week itself, so a naming pattern
--       carrying the week token, a plus and the week-to token prints "36+37"
--       from the numbers with nothing typed anywhere. A publication that writes
--       the pair some other way -- "36 og 37" -- writes the whole of it into
--       weekLabel, which is one field the pattern reads rather than two that
--       have to be kept in step.
--
--       (Written out in words rather than in tokens: the placeholder syntax
--       Flyway resolves before it parses a script is the same syntax the naming
--       patterns use, and an unresolvable one here refuses the whole migration.)
--
-- weekLabel and yearLabel stay exactly as V15 declared them, and so do week,
-- weekTo and year: the numbers are still derived from the cut-off and are still
-- what the list ordering, the timeline strip, the gap arithmetic and the archive
-- read.
--
-- NO BACKFILL and nothing to preserve: an edition covering two periods is
-- already numbered for both, and one written any other way is written into
-- weekLabel, which this does not touch.
--
-- Idempotent by the V15 idiom, inverted: MySQL 8 has no DROP COLUMN IF EXISTS,
-- so the check goes in a procedure. Safe to run twice, and safe on a database
-- where V15 ran against a schema Hibernate had already shaped and the column
-- was never added at all.

DROP PROCEDURE IF EXISTS niord_drop_column_if_present;

DELIMITER $$
CREATE PROCEDURE niord_drop_column_if_present(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
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

CALL niord_drop_column_if_present('PublicationIssue', 'weekToLabel',
    'ALTER TABLE PublicationIssue DROP COLUMN weekToLabel');

DROP PROCEDURE niord_drop_column_if_present;
