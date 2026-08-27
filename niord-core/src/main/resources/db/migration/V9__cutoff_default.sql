-- Flyway migration V9: where a series' cut-off falls by default.
--
-- A cut-off is the end of an issue's content period -- the instant membership
-- is decided at -- and the publication moment is when somebody pressed publish.
-- For the weekly series the two are minutes apart and the release stamps the
-- cut-off. For the annual series they can be a year apart: the 2018 edition of
-- EfS A describes what was in force when 2018 opened, however late in January
-- it was finished, and the accumulated 2003 list describes what was published
-- during 2003 and came out in 2016. The publish dialog needs to know which
-- shape it is offering, and that is a fact about the series.
--
-- Idempotent like V6 and V8: the ALTER is guarded on information_schema, and the
-- backfill only touches rows still at the column default, so a value an admin
-- has since chosen is never overwritten. The backfill is the same rule the
-- importer and the create form apply: yearly in-force lists are decided where
-- their period opens, yearly accumulated lists where it closes, everything else
-- at the release.

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

CALL niord_add_column_if_absent('PublicationSeries', 'cutoffDefault',
    'ALTER TABLE PublicationSeries ADD COLUMN cutoffDefault
         ENUM(''RELEASE_MOMENT'',''PERIOD_START'',''PERIOD_END'') NOT NULL DEFAULT ''RELEASE_MOMENT''');

DROP PROCEDURE niord_add_column_if_absent;

UPDATE PublicationSeries s
   SET s.cutoffDefault = CASE
           WHEN s.timeRelation = 'IN_FORCE_AT_CUTOFF' THEN 'PERIOD_START'
           ELSE 'PERIOD_END'
       END
 WHERE s.cadence = 'YEARLY'
   AND s.cutoffDefault = 'RELEASE_MOMENT';
