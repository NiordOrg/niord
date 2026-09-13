-- Flyway migration V17: the new model serves the public, and nothing switches it.
--
-- THE RULE this enforces in the schema: once this is deployed, the public list
-- is the new-model issues plus the legacy rows no published issue has taken
-- over. There is no per-series switch, no estate-wide switch, and no direction
-- to run it in -- a legacy row stands until the issue imported from it is
-- published, and from that publish on the issue stands in its place. An abort
-- is a full database restore, not a setting.
--
--   PublicationSeries.publicAuthority
--       goes. It was the only thing a deployment could turn the two halves on
--       and off with, and its NEW value is now the only behaviour.
--
--   IssueAuditEntry rows with action = 'SERIES_AUTHORITY_CHANGED'
--       go. The action records a change to that column, so the rows describe an
--       event that can no longer happen; and the column is read back into a
--       Java enum whose constant is gone, so a row left behind fails the read
--       of any audit trail that includes it.
--
--   ShadowDiffRun
--       goes. The table held one row per comparison of the new engine against a
--       legacy release, which was evidence for a decision that is no longer
--       taken. Nothing reads it: the sweep that wrote it, the report that
--       rendered it and the endpoints that served it are all gone with the
--       switch they were evidence for. Its unique key and its series index are
--       part of the table and go with it, so V5 needs no separate undoing.
--
-- THE EARLIER MIGRATIONS KEEP THE TEXT THE SCHEMA HAS OUTGROWN. V1 still
-- creates publicAuthority in its CREATE TABLE, V12 still names s.publicAuthority
-- among the clauses to index if PublicationSeries ever grows past the single
-- page MySQL answers it from today, and V5 still builds ShadowDiffRun. All three stand exactly as written: a
-- migration that has run is the record of what the schema did on the day it ran,
-- and editing one moves a checksum other databases have already stored. This
-- script is where a reader who followed any of those pointers learns that the
-- column, the action and the table are gone.
--
-- Idempotent by the V16 idiom: MySQL 8 has no DROP COLUMN IF EXISTS, so the
-- check goes in a procedure reading information_schema. Safe to run twice, and
-- safe on a database where the column, the table or the rows were never there.

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

CALL niord_drop_column_if_present('PublicationSeries', 'publicAuthority',
    'ALTER TABLE PublicationSeries DROP COLUMN publicAuthority');

DROP PROCEDURE niord_drop_column_if_present;

-- The audit rows. Guarded on the table rather than written as a bare DELETE,
-- which is the idiom of the column drop above reused for symmetry: a schema
-- Flyway builds from nothing runs V1 before this, so IssueAuditEntry is there
-- to delete from. What the guard actually buys is the database whose schema was
-- shaped outside this chain -- baselined above V1, or restored from a dump taken
-- before it -- where the table can be absent and a bare DELETE would fail.

DROP PROCEDURE IF EXISTS niord_delete_rows_if_table_present;

DELIMITER $$
CREATE PROCEDURE niord_delete_rows_if_table_present(
    IN p_table VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = p_table) THEN
        SET @sql = p_ddl;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$
DELIMITER ;

CALL niord_delete_rows_if_table_present('IssueAuditEntry',
    'DELETE FROM IssueAuditEntry WHERE action = ''SERIES_AUTHORITY_CHANGED''');

DROP PROCEDURE niord_delete_rows_if_table_present;

-- The evidence table. No procedure here: MySQL does guard DROP TABLE on
-- existence, and that is the only case to cover -- a schema Flyway builds from
-- nothing still runs V5 before this, so the table is there to drop; a database
-- restored from a dump taken before V5 has never had it.

DROP TABLE IF EXISTS ShadowDiffRun;
