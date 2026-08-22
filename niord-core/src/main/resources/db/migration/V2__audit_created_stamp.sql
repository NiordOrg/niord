-- Flyway migration V2: give IssueAuditEntry its own created stamp.
--
-- V1 created the table without one. BaseEntity does not carry a created column
-- -- only VersionedEntity does -- and an append-only audit row must not be
-- versioned, so it needs its own. Without it the Historik panel has nothing to
-- order by except the surrogate id, which is an implementation detail rather
-- than a time.
--
-- The DEFAULT is not decoration. "ADD COLUMN ... NOT NULL" with no default
-- fails outright on a table that already holds rows, so the migration would
-- have succeeded on a freshly created table and failed anywhere the audit had
-- already been written to. Found exactly that way, on a local database with
-- test rows in it.
--
-- Any row that existed before this migration therefore carries the migration
-- time rather than its real one. That is a small, bounded inaccuracy -- those
-- rows predate the column entirely -- and it is recorded here rather than left
-- for somebody to discover in the panel.
--
-- A separate migration rather than an edit to V1: Flyway checksums applied
-- migrations, so changing V1 after it has run anywhere fails validation on the
-- next start.

ALTER TABLE IssueAuditEntry
    ADD COLUMN created DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
