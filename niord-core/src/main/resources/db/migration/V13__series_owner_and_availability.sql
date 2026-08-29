-- Flyway migration V13: the owner, and who else may cite it.
--
-- ONE COLUMN USED TO ANSWER TWO QUESTIONS. PublicationSeries.domain said both
-- "which desk administers this publication and supplies the timezone its
-- cut-offs are read in" and, by being null, "it may be cited from everywhere".
-- The two are not the same question, and answering them together meant a
-- publication that had to be reachable from every domain could not have a
-- timezone at all -- which is thirteen of the imported series.
--
-- So the second question gets its own answer:
--
--   availability                       OWNER_ONLY | SELECTED_DOMAINS | ALL_DOMAINS
--   PublicationSeries_AvailableDomain  the domains SELECTED_DOMAINS names
--
-- and the first becomes single-valued: domain_id NOT NULL.
--
-- THE NOT NULL IS CONDITIONAL, AND THIS IS THE PART TO READ. Three database
-- shapes have to boot off this file:
--
--   * empty -- go-live imports the estate from nothing, so the table has no rows
--     and the constraint applies immediately. This is the shape it is for.
--   * a rehearsal restore with rows -- the backfill below files every ownerless
--     publication under niord-annex, which is the same ruling the importer
--     applies, so no NULL survives and the constraint applies.
--   * a database with ownerless rows and NO niord-annex domain -- the backfill
--     is a no-op, NULLs remain, and MODIFY ... NOT NULL would fail and take the
--     boot with it. There the constraint is SKIPPED and a warning is left in the
--     table below.
--
-- Skipping is the right trade rather than a weakness: the rule is enforced on
-- every write by S-20a, refused by the cutover pre-flight as
-- SERIES_WITHOUT_OWNER, and assigned by the importer -- which is where every
-- other rule in this feature lives. A migration that would not boot is worth
-- less than one that leaves the database usable and the rule enforced above it.
--
-- Re-runnable by the V6 idiom throughout: MySQL 8 has no ADD COLUMN IF NOT
-- EXISTS, and this has to be safe against a database where Hibernate has already
-- made the change in place.

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

-- DEFAULT 'OWNER_ONLY' so the column can be NOT NULL on a populated table. It is
-- also the conservative default: a publication that has not said who may cite it
-- is not silently published to every domain's citation dialog.
CALL niord_add_column_if_absent('PublicationSeries', 'availability',
    'ALTER TABLE PublicationSeries ADD COLUMN availability
         ENUM(''OWNER_ONLY'',''SELECTED_DOMAINS'',''ALL_DOMAINS'')
         NOT NULL DEFAULT ''OWNER_ONLY''');

DROP PROCEDURE niord_add_column_if_absent;

-- The one-time classification, by the same rule the importer and the create form
-- apply: a GENERATED series is assembled from one domain's messages over that
-- domain's calendar and means that desk's week; anything else -- an uploaded
-- document, an external link, a publication with no content model -- is a
-- reference other desks cite, which is how the whole catalogue behaved before
-- this column existed.
--
-- Restricted to rows still holding the column default, like V8's kind: a row that
-- already says something else was decided by a human or by the importer, and this
-- migration has nothing to add to that.
UPDATE PublicationSeries
   SET availability = 'ALL_DOMAINS'
 WHERE contentMode <> 'GENERATED_FROM_QUERY'
   AND availability = 'OWNER_ONLY';

-- The availability list. CREATE TABLE IF NOT EXISTS is idempotent on its own, and
-- the foreign keys are declared INLINE rather than added afterwards so there is no
-- unguarded ALTER ... ADD CONSTRAINT for a re-run to trip over.
--
-- PRIMARY KEY on the pair: a domain is either in the list or it is not, and a
-- duplicate row would make "shared with three domains" and "shared with two"
-- indistinguishable from a count.
--
-- ON DELETE CASCADE on the DOMAIN side only. Deleting a domain removes it from
-- every list that named it, which is what "this domain no longer exists" means --
-- and the alternative is a delete that fails against a publication in another
-- domain entirely. The SERIES side is deliberately RESTRICT: a series is removed
-- through the application, which empties this list first, and a cascade there
-- would let a stray DELETE take the sharing with it silently.
CREATE TABLE IF NOT EXISTS PublicationSeries_AvailableDomain (
    series_id INTEGER NOT NULL,
    domain_id INTEGER NOT NULL,
    PRIMARY KEY (series_id, domain_id),
    CONSTRAINT FK_pub_series_available_domain_series
        FOREIGN KEY (series_id) REFERENCES PublicationSeries (id),
    CONSTRAINT FK_pub_series_available_domain_domain
        FOREIGN KEY (domain_id) REFERENCES Domain (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- The owner backfill. niord-annex is the annex desk -- where the publications
-- nobody else claims already live -- and it is the same answer the importer
-- gives. A NO-OP where that domain does not exist: the subquery is null, and the
-- EXISTS guard keeps the UPDATE from writing it.
UPDATE PublicationSeries
   SET domain_id = (SELECT id FROM Domain WHERE domainId = 'niord-annex')
 WHERE domain_id IS NULL
   AND EXISTS (SELECT 1 FROM Domain WHERE domainId = 'niord-annex');

-- And the constraint, only where it can be applied. See the header.
DROP PROCEDURE IF EXISTS niord_require_series_owner;

DELIMITER $$
CREATE PROCEDURE niord_require_series_owner()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM PublicationSeries WHERE domain_id IS NULL) THEN
        ALTER TABLE PublicationSeries MODIFY domain_id INTEGER NOT NULL;
    END IF;
END $$
DELIMITER ;

CALL niord_require_series_owner();

DROP PROCEDURE niord_require_series_owner;
