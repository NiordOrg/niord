-- Flyway migration V6: the indexes that answer "which issues is this message in".
--
-- V1 created IssueMember and IssueOverride without the indexes DATA-MODEL §8.3
-- specifies, and nothing noticed because until now nothing asked by messageUid:
-- every read went the other way, from an issue to its members. The message
-- detail's publications panel asks the inverse, on every message an editor
-- opens.
--
-- Without issue_member_uid_k that is a full scan of a table holding order 10^5
-- rows and growing by ~9,000 a year -- one per message opened, on a panel that
-- is a footnote on the screen. The estate makes it worse than the row count
-- suggests: an IN_FORCE_AT_CUTOFF series re-lists every message still in force
-- in every weekly edition, so the table is dominated by repeated memberships of
-- the same few thousand messages.
--
-- INDEXES ONLY. §8.3 also specifies UNIQUE (issue_id, messageUid) on both
-- tables, and it is deliberately not created here. Adding a unique constraint to
-- a populated table is a claim about the data, not about the schema: if the
-- imported estate holds one duplicate the ALTER fails and takes the deploy with
-- it. The local slice is clean, but the local slice is a replay, not the import.
-- Check the deployed estate first --
--   SELECT COUNT(*) FROM (SELECT issue_id, messageUid FROM IssueMember
--                         GROUP BY issue_id, messageUid HAVING COUNT(*) > 1) t;
-- -- and add the constraints in their own migration once it answers zero. The
-- entity annotations are left off for the same reason: declaring a constraint
-- the schema does not have would make the mapping describe a database nobody
-- has.
--
-- Idempotent by construction. MySQL 8 has no CREATE INDEX IF NOT EXISTS, and
-- this migration has to be safe against a schema Hibernate may already have
-- updated in place -- the local development database was recovered exactly that
-- way -- so each statement is guarded on information_schema and prepared only
-- when the index is genuinely missing.

DROP PROCEDURE IF EXISTS niord_add_index_if_absent;

DELIMITER $$
CREATE PROCEDURE niord_add_index_if_absent(
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

-- C9's editor-tier question: which issue(s) does this message belong to.
CALL niord_add_index_if_absent('IssueMember', 'issue_member_uid_k',
    'CREATE INDEX issue_member_uid_k ON IssueMember (messageUid)');

-- The ordered replay: render this issue in its frozen order.
CALL niord_add_index_if_absent('IssueMember', 'issue_member_issue_sort_k',
    'CREATE INDEX issue_member_issue_sort_k ON IssueMember (issue_id, sortIndex)');

-- The same question against the curator overrides, which the panel reads in the
-- same direction: what did anybody decide about THIS message.
CALL niord_add_index_if_absent('IssueOverride', 'issue_override_uid_k',
    'CREATE INDEX issue_override_uid_k ON IssueOverride (messageUid)');

DROP PROCEDURE niord_add_index_if_absent;
