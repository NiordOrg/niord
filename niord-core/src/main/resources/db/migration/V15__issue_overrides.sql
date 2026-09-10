-- Flyway migration V15: what ONE edition says differently.
--
-- Four columns and a flag, and between them they are everything a series
-- decides that a single edition occasionally has to decide for itself.
--
--   PublicationIssue.weekLabel / weekToLabel / yearLabel
--       what the edition is CALLED where its derived number is printed.
--       The numbers themselves -- week, weekTo, year -- stay exactly as they
--       are: derived from the cut-off, numeric, and read by the list ordering,
--       the timeline strip, the gap arithmetic and the archive. These carry the
--       free text that goes on the cover, because a double week is written
--       "36+37" or "36 og 37" and neither of those is a number. Sharing one
--       column between the two facts meant either the arithmetic broke or the
--       cover did.
--
--   PublicationIssue.reportId
--       the report THIS edition renders with, where it is not the series'.
--       The escape hatch that six imported `dont-use-` series exist because
--       there was none: an edition that had to be set out differently required
--       cloning the whole series, which fragments the archive it was cloned
--       from.
--
--   PublicationIssueDesc.fileNameOverridden
--       the twin of nameOverridden, and it exists for the same reason. A file
--       name the series' pattern produced re-renders whenever the period moves;
--       one somebody typed is a decision, and re-deriving over it discards the
--       decision silently. The flag is what tells them apart -- fileName alone
--       cannot, because that column also holds the name of an uploaded document
--       and the name the last release happened to write.
--
-- NO BACKFILL, and none is possible: every column's absence IS its correct
-- value. A NULL label means the derived number prints, a NULL reportId means the
-- edition follows its series, and a false flag means the file name follows the
-- pattern -- which is what every one of the 1,077 archived rows and every row
-- created since is doing today. There is nothing to compute and nothing to
-- guess.
--
-- Idempotent by the V6/V8 idiom throughout: MySQL 8 has no ADD COLUMN IF NOT
-- EXISTS, and this has to be safe against a development database where
-- Hibernate has already added the columns in place.

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

-- VARCHAR(64) and nullable, matching `edition` beside them -- which has been
-- free text since V1 and needs nothing new, because an edition was never a
-- number in this estate either. Nullable because absent is the ordinary state
-- and it has to be distinguishable from an empty label somebody typed.
CALL niord_add_column_if_absent('PublicationIssue', 'weekLabel',
    'ALTER TABLE PublicationIssue ADD COLUMN weekLabel VARCHAR(64) NULL');

CALL niord_add_column_if_absent('PublicationIssue', 'weekToLabel',
    'ALTER TABLE PublicationIssue ADD COLUMN weekToLabel VARCHAR(64) NULL');

CALL niord_add_column_if_absent('PublicationIssue', 'yearLabel',
    'ALTER TABLE PublicationIssue ADD COLUMN yearLabel VARCHAR(64) NULL');

-- As wide as PublicationSeries.reportId, since it holds the same identifiers.
CALL niord_add_column_if_absent('PublicationIssue', 'reportId',
    'ALTER TABLE PublicationIssue ADD COLUMN reportId VARCHAR(64) NULL');

-- BIT NOT NULL DEFAULT 0, exactly as nameOverridden and fileSourceSticky are
-- declared in V1: that is what Hibernate maps a boolean to on MySQL, and a
-- column of a different type here would make the mapping describe a database
-- nobody has. The default is what makes the NOT NULL applicable to a populated
-- table, and it is also the correct value for every existing row.
CALL niord_add_column_if_absent('PublicationIssueDesc', 'fileNameOverridden',
    'ALTER TABLE PublicationIssueDesc ADD COLUMN fileNameOverridden BIT NOT NULL DEFAULT b''0''');

DROP PROCEDURE niord_add_column_if_absent;
