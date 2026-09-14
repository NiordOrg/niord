-- Flyway migration V19: the record of a release or amend that is RUNNING.
--
-- Publishing or amending an issue renders its documents inside the request, and
-- on a compiled annual that is tens of seconds. The client that pressed the
-- button knows to wait, because its own request is still open. Nobody else does:
-- a refreshed page, a second tab and another admin all read an idle issue and
-- offer the same button again, and the second press does the whole thing over --
-- archiving the documents the first one is in the middle of writing.
--
-- Two columns, and they hold what the second reader is missing:
--
--   PublicationIssue.workingAction   PUBLISH or AMEND, while one is in flight
--   PublicationIssue.workingSince    when that work began
--
-- NOT PART OF THE ISSUE'S RECORD, which is why they are nullable and why nothing
-- backfills them. They say nothing about the publication: they are a fact about a
-- request that is in progress, true for as long as that request lives and false
-- for the whole of the issue's history on either side of it. No existing row can
-- have a value, because no request from before this migration is still running.
--
-- ON THE ISSUE ROW rather than in a table of their own, because every reader that
-- needs them has the issue row in hand already -- the workbench answers one
-- screen in one response, and a second table would be a second query on the one
-- read that exists to avoid them.
--
-- The pair is written and cleared by ONE conditional statement each, never
-- through the entity: the mapping declares both columns insertable = false,
-- updatable = false precisely so that an ordinary save of the issue -- which the
-- publish itself performs, from a copy loaded before the marker was written --
-- cannot carry a stale NULL over a marker that is live. See IssueWorkMarker.
--
-- Idempotent by the V6/V8/V15 idiom: MySQL 8 has no ADD COLUMN IF NOT EXISTS, and
-- this has to be safe against a development database where Hibernate has already
-- added the columns in place.

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

-- VARCHAR, not a native ENUM, and that is the one deliberate difference from the
-- status columns beside it. The vocabulary here is not the issue's lifecycle --
-- it is the set of actions long enough to be worth guarding, and the day a third
-- one joins them an ENUM would need an ALTER TABLE against a live schema where
-- this needs nothing. 16 characters holds both values with room to spare.
CALL niord_add_column_if_absent('PublicationIssue', 'workingAction',
    'ALTER TABLE PublicationIssue ADD COLUMN workingAction VARCHAR(16) NULL');

-- DATETIME(6), matching every other instant on this table: the marker is compared
-- against a ceiling in the seconds range, and a column that rounded to whole
-- seconds would make two markers a render apart look simultaneous.
CALL niord_add_column_if_absent('PublicationIssue', 'workingSince',
    'ALTER TABLE PublicationIssue ADD COLUMN workingSince DATETIME(6) NULL');

DROP PROCEDURE niord_add_column_if_absent;
