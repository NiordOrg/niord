-- Flyway migration V3: nominalCutoffDay must be able to hold Wednesday.
--
-- The column was created as enum('MONDAY','SUNDAY'). The weekly EfS is released
-- every WEDNESDAY -- "EfS uge 27 is released Wednesday of week 27" -- and S-5
-- makes nominalCutoffDay REQUIRED for any series with cadence = WEEKLY. So the
-- primary production series could not record its own release day, and every
-- weekly series had to claim it releases on Monday or Sunday.
--
-- HOW IT HAPPENED, because it is worth not repeating. DATA-MODEL section 3.1.4
-- declares the Java type as "CutoffDay (MONDAY...SUNDAY)" -- an ellipsis meaning
-- all seven -- and transcribes the DDL beside it as enum('MONDAY','SUNDAY'). The
-- ellipsis was read as a two-element set. The generated schema followed the DDL
-- column, and the Java enum was then written from the schema, so all three
-- agreed with each other and none of them agreed with the domain.
--
-- Nothing computed wrongly: the field is NOMINAL, and the real cut-off is the
-- stamped release instant. It is a default offered in the UI and the basis for
-- gap detection, not an input to membership. That is why the tests did not catch
-- it -- the fixtures pick MONDAY and nothing asserts the set is complete.
--
-- MODIFY rather than a new column: widening an enum leaves every existing value
-- valid, so this loses nothing. At the time of writing the table holds no rows
-- on any environment, but the statement is safe either way.
--
-- A separate migration rather than an edit to V1: Flyway checksums applied
-- migrations, so changing V1 after it has run anywhere fails validation on the
-- next start.

ALTER TABLE PublicationSeries
    MODIFY COLUMN nominalCutoffDay
        ENUM ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY');
