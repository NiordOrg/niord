-- Flyway migration V4: membershipProvenance must be able to say IMPORTED.
--
-- Ruling B5-iv (Rasmus, 2026-08-23): the six tag-carrying annexes import with ONE
-- member row each, not zero. The locked tag holds exactly one message and is the
-- only surviving record of what that annex contained -- the series carries two
-- live messages a year (B/1 ice service, B/2 NCAGS) and no query of any shape can
-- select one and not the other, because the only discriminator is the message
-- body. Once the tag is gone, so is the answer.
--
-- None of the four existing values can carry that row honestly:
--
--   EXACT           claims a replay reproduces it. Nothing reproduces it.
--   EXPLAINED_DIFF  claims it differs from a derivation. There is no derivation.
--   UNION_SNAPSHOT  claims it holds more than one instant produces. It holds one
--                   message, named once.
--   NO_MEMBERSHIP   claims there are no members. There is one, and discarding it
--                   is what the ruling exists to prevent.
--
-- So IMPORTED: named by hand, never derived, nothing to reproduce.
--
-- MODIFY rather than a new column: widening an enum leaves every existing value
-- valid, so this loses nothing.
--
-- A separate migration rather than an edit to V1, because Flyway checksums
-- applied migrations and V1 has run on the test environment.

ALTER TABLE PublicationIssue
    MODIFY COLUMN membershipProvenance
        ENUM ('EXACT', 'EXPLAINED_DIFF', 'UNION_SNAPSHOT', 'NO_MEMBERSHIP', 'IMPORTED');
