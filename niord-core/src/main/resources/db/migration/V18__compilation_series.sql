-- Flyway migration V18: a series whose members are another series' published issues.
--
-- A COMPILATION is a third membership regime, not a new content mode and not a
-- new criterion kind. Its content mode stays GENERATED_FROM_QUERY -- so every
-- gate that asks "does this produce a document" keeps working -- and the seam is
-- the TIME RELATION, which already says how a period selects content. A series
-- in the new relation names a SOURCE SERIES, and an issue of it holds the union
-- of the frozen member rows of that source's PUBLISHED issues whose effective
-- cut-off falls inside its own period.
--
-- Three enums widen and four columns appear:
--
--   PublicationSeries.timeRelation            + COMPILED_FROM_SOURCE
--   PublicationSeries.sourceSeries_id           which series is compiled
--   IssueMember.source                        + COMPILED
--   IssueMember.sourceIssuePublicId             which source issue printed this row
--   PublicationIssue.membershipProvenance     + COMPILED
--   PublicationIssue.snapshotSourceSeriesId     the operand, recorded at freeze
--   PublicationIssue.snapshotSourceIssueIds     the answer, recorded at freeze
--
-- MODIFY rather than a new column for the three enums, by the V4 argument:
-- widening an enum leaves every existing value valid, so it loses nothing.
--
-- A publicId TEXT rather than a foreign key on IssueMember, and that is a
-- decision. The compiled row is the COMPILATION's own frozen record of what it
-- printed. A retired source issue is deletable today; a foreign key would either
-- block that or need a cascade rule, and both make one issue's record depend on
-- another issue's row surviving. The publicId stays readable after a deletion,
-- and the name, week and year are read live by publicId where the row is still
-- there.
--
-- NO BACKFILL, by construction: no compiled series and no compiled row exists in
-- any database this file can reach, because the vocabulary it needs arrives with
-- this migration.
--
-- Re-runnable throughout, by the V6/V13 idiom: MySQL 8 has no ADD COLUMN IF NOT
-- EXISTS, and this has to be safe against a database where Hibernate has already
-- made the change in place. The FK and the indexes are guarded the same way, off
-- TABLE_CONSTRAINTS and STATISTICS, because ADD CONSTRAINT and CREATE INDEX are
-- no more idempotent than ADD COLUMN.

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

DROP PROCEDURE IF EXISTS niord_add_constraint_if_absent;

DELIMITER $$
CREATE PROCEDURE niord_add_constraint_if_absent(
    IN p_table VARCHAR(64), IN p_constraint VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = p_table
                     AND CONSTRAINT_NAME = p_constraint) THEN
        SET @sql = p_ddl;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$
DELIMITER ;

-- --------------------------------------------------------------- the vocabulary

ALTER TABLE PublicationSeries
    MODIFY COLUMN timeRelation
        ENUM ('PUBLISHED_IN_INTERVAL', 'IN_FORCE_AT_CUTOFF', 'COMPILED_FROM_SOURCE');

ALTER TABLE IssueMember
    MODIFY COLUMN source
        ENUM ('CRITERIA', 'OVERRIDE_INCLUDE', 'IMPORTED', 'COMPILED') NOT NULL;

ALTER TABLE PublicationIssue
    MODIFY COLUMN membershipProvenance
        ENUM ('EXACT', 'EXPLAINED_DIFF', 'UNION_SNAPSHOT', 'NO_MEMBERSHIP', 'IMPORTED', 'COMPILED');

-- ------------------------------------------------------------------ the columns

-- NULLABLE, and it stays nullable: only a compilation names a source, and S-24
-- refuses the pairing in both directions above the database.
CALL niord_add_column_if_absent('PublicationSeries', 'sourceSeries_id',
    'ALTER TABLE PublicationSeries ADD COLUMN sourceSeries_id INTEGER NULL');

-- RESTRICT by omission, which is the right answer here. Deleting a series that
-- another series compiles must fail rather than silently empty the compilation's
-- derivation; the application removes a series through its own action, and that
-- action is where a readable refusal belongs.
CALL niord_add_constraint_if_absent('PublicationSeries', 'FK_pub_series_source_series',
    'ALTER TABLE PublicationSeries ADD CONSTRAINT FK_pub_series_source_series
         FOREIGN KEY (sourceSeries_id) REFERENCES PublicationSeries (id)');

CALL niord_add_index_if_absent('PublicationSeries', 'pub_series_source_k',
    'CREATE INDEX pub_series_source_k ON PublicationSeries (sourceSeries_id)');

-- The same width as PublicationIssue.publicId, because that is what it holds.
CALL niord_add_column_if_absent('IssueMember', 'sourceIssuePublicId',
    'ALTER TABLE IssueMember ADD COLUMN sourceIssuePublicId VARCHAR(36) NULL');

-- Indexed because the member list groups by it: a compiled annual runs to
-- upwards of a thousand rows over fifty source issues, and the sources panel
-- reads the distinct values off the list in one pass.
CALL niord_add_index_if_absent('IssueMember', 'issue_member_source_issue_k',
    'CREATE INDEX issue_member_source_issue_k ON IssueMember (sourceIssuePublicId)');

-- The snapshot header, on the same terms as snapshotSeriesIds beside it: the
-- OPERAND is a series, and the ANSWER is a set of issues that cannot be
-- re-derived once more issues fall into the period.
CALL niord_add_column_if_absent('PublicationIssue', 'snapshotSourceSeriesId',
    'ALTER TABLE PublicationIssue ADD COLUMN snapshotSourceSeriesId VARCHAR(64) NULL');

CALL niord_add_column_if_absent('PublicationIssue', 'snapshotSourceIssueIds',
    'ALTER TABLE PublicationIssue ADD COLUMN snapshotSourceIssueIds TEXT NULL');

DROP PROCEDURE niord_add_column_if_absent;
DROP PROCEDURE niord_add_index_if_absent;
DROP PROCEDURE niord_add_constraint_if_absent;
