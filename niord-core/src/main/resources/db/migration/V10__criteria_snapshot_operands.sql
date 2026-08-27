-- Flyway migration V10: the rest of the resolved operands on a published issue.
--
-- A published issue already froze the message series its criteria resolved to.
-- The criteria vocabulary also selects on main type, area, category and chart,
-- and until now those resolved into the query and were then forgotten -- so an
-- issue could be asked "what did you select" and answer only half.
--
-- The criteria snapshot holds the DOCUMENT, which is a different fact: a domain
-- node expands to a set of message series the document never spells out, and an
-- area MRN that has since been renamed or re-parented is only recoverable from
-- what was written down at release time. NULL means the criteria did not select
-- on that facet at all; an empty string would read as "selected, and nothing
-- matched", which is a different publication.
--
-- Idempotent like V6, V8 and V9: the ALTER is guarded on information_schema, so
-- re-running it on a schema that already has the columns is a no-op. No backfill
-- -- an issue published before these columns existed genuinely has no record of
-- these operands, and inventing one would be worse than the gap.

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

CALL niord_add_column_if_absent('PublicationIssue', 'snapshotMainTypes',
    'ALTER TABLE PublicationIssue ADD COLUMN snapshotMainTypes VARCHAR(255) NULL');

CALL niord_add_column_if_absent('PublicationIssue', 'snapshotAreaIds',
    'ALTER TABLE PublicationIssue ADD COLUMN snapshotAreaIds TEXT NULL');

CALL niord_add_column_if_absent('PublicationIssue', 'snapshotCategoryIds',
    'ALTER TABLE PublicationIssue ADD COLUMN snapshotCategoryIds TEXT NULL');

CALL niord_add_column_if_absent('PublicationIssue', 'snapshotChartNumbers',
    'ALTER TABLE PublicationIssue ADD COLUMN snapshotChartNumbers TEXT NULL');

DROP PROCEDURE niord_add_column_if_absent;
