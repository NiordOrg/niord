-- Flyway migration V7: one message is in one issue once.
--
-- DATA-MODEL §8.3 specifies UNIQUE (issue_id, messageUid) on both IssueMember and
-- IssueOverride. V6 deliberately left them out and said why: adding a unique
-- constraint to a populated table is a claim about the DATA, not about the
-- schema, and one duplicate fails the ALTER and takes the deploy with it.
--
-- The claim has now been measured rather than assumed. The cutover pre-flight
-- counts both over the whole estate, and against the deployed test database --
-- 1,077 imported issues, order 10^5 member rows -- it reports:
--
--     "duplicateMemberships": 0,
--     "duplicateOverrides": 0
--
-- So the constraints describe what is already true, and adding them is recording
-- an invariant rather than imposing one.
--
-- WHY THEY ARE WORTH HAVING. A duplicate membership is not a tidiness problem: the
-- message prints twice in the report, and the member count an editor reads
-- disagrees with the document that comes out. A duplicate override is worse --
-- which one applies is whichever the query returned first, so an include and an
-- exclude on the same message resolve differently between runs.
--
-- Guarded on information_schema for the same reason V6's indexes are: MySQL 8 has
-- no ADD CONSTRAINT IF NOT EXISTS, and this has to be safe against a schema
-- Hibernate may already have updated in place.
--
-- IF THIS MIGRATION FAILS on a future dataset, it is not a migration defect: it
-- means that dataset genuinely holds a duplicate. Run the pre-flight, which names
-- the offending issue and message, before changing anything here.

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

CALL niord_add_unique_if_absent('IssueMember', 'UK_issue_member_issue_uid',
    'ALTER TABLE IssueMember ADD CONSTRAINT UK_issue_member_issue_uid UNIQUE (issue_id, messageUid)');

CALL niord_add_unique_if_absent('IssueOverride', 'UK_issue_override_issue_uid',
    'ALTER TABLE IssueOverride ADD CONSTRAINT UK_issue_override_issue_uid UNIQUE (issue_id, messageUid)');

DROP PROCEDURE niord_add_unique_if_absent;
