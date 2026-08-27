-- Flyway migration V11: one row per message per issue, on both membership tables.
--
-- Specified with the model and deliberately left out of the first schema: adding
-- a unique constraint to a populated table is a claim about the data, and one
-- duplicate anywhere fails the deploy of everything shipped with it. The claim
-- has since been checked -- the cut-over pre-flight counts duplicate memberships
-- and duplicate overrides over the whole estate, and both were zero across
-- 1,077 imported issues and 64,250 member rows -- so the constraint can go in
-- knowing what it will find.
--
-- What it buys is that the two shapes a duplicate could take are now impossible
-- rather than merely absent. A message appearing twice in one issue prints
-- twice in the document; two overrides for one message are either redundant or
-- contradictory, and the resolver would have to pick one. The curation service
-- already deletes-then-flushes before writing a replacement precisely so this
-- constraint can exist, and until now nothing enforced that it had.
--
-- Idempotent like V6, V8, V9 and V10: guarded on information_schema.STATISTICS,
-- so re-running against a schema that already carries the indexes is a no-op.

DROP PROCEDURE IF EXISTS niord_add_unique_if_absent;

DELIMITER $$
CREATE PROCEDURE niord_add_unique_if_absent(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND INDEX_NAME = p_index) THEN
        SET @sql = p_ddl;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$
DELIMITER ;

CALL niord_add_unique_if_absent('IssueMember', 'UK_issuemember_issue_message',
    'ALTER TABLE IssueMember ADD CONSTRAINT UK_issuemember_issue_message
         UNIQUE (issue_id, messageUid)');

CALL niord_add_unique_if_absent('IssueOverride', 'UK_issueoverride_issue_message',
    'ALTER TABLE IssueOverride ADD CONSTRAINT UK_issueoverride_issue_message
         UNIQUE (issue_id, messageUid)');

DROP PROCEDURE niord_add_unique_if_absent;
