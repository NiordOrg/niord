-- Flyway migration V12: the indexes the issue read paths actually need.
--
-- PublicationIssue was created by V1 with no index beyond its primary key and
-- the unique keys on publicId and legacyPublicationId, and it is the one table
-- in this feature that grows without bound: ~1,077 rows arrive with the import
-- and every series adds one per cadence period thereafter, forever. The two
-- indexes below are sized off the queries that exist, not off the annotation
-- gap.
--
--   (series_id, status) -- "the issues of this series that are OPEN", asked by
--   the lifecycle guard on create, the publish transaction's predecessor and
--   successor lookups, the delete guard, the checklist, the draft builder and
--   the released-issue count behind rule S-18. InnoDB already indexes series_id
--   alone for the foreign key; what it cannot do is discriminate on status, and
--   every one of those reads does. On a weekly series after a decade that is the
--   difference between reading 2 rows and reading 500.
--
--   (status, publicFrom) -- the ANONYMOUS public list. The public adapter asks
--   for PUBLISHED issues whose window has opened and bounds them by publicFrom,
--   and it is the only read here served to an unauthenticated caller at whatever
--   rate the public site is hit. Ordering the index on status first and
--   publicFrom second matches the query: an equality on status, then a range.
--
-- PUBLICATIONSERIES DELIBERATELY GETS NONE. Its reads filter on status and
-- publicAuthority and would look like index candidates from the annotations
-- alone, but the table holds 23 rows and is not on a growth curve -- one row per
-- publication the authority produces. An index there would cost a write on every
-- series save to save nothing measurable on a read MySQL answers from a single
-- page. If the estate ever reaches a scale where that stops being true, the
-- clauses to index are s.status and s.publicAuthority.
--
-- Idempotent by construction, the V6 idiom. MySQL 8 has no
-- CREATE INDEX IF NOT EXISTS, and this has to be safe both against a re-run and
-- against a database where Hibernate has already made the change in place.

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

CALL niord_add_index_if_absent('PublicationIssue', 'publication_issue_series_status_k',
    'CREATE INDEX publication_issue_series_status_k ON PublicationIssue (series_id, status)');

CALL niord_add_index_if_absent('PublicationIssue', 'publication_issue_status_public_from_k',
    'CREATE INDEX publication_issue_status_public_from_k ON PublicationIssue (status, publicFrom)');

DROP PROCEDURE niord_add_index_if_absent;
