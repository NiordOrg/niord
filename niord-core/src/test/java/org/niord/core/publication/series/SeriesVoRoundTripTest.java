package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every settable field on a series survives entity -> VO -> entity.
 *
 * Written after nominalCutoffDayOfMonth and nominalCutoffMonth were found missing
 * from the VO. Nothing was corrupted by that -- updateFromVo simply never assigned
 * them, so an update left them alone -- but they could not be SET or READ through
 * the API at all, and S-6 requires both for a MONTHLY or YEARLY cadence. Four real
 * series in the archive are YEARLY. None of them could ever have been activated,
 * and the failure would have surfaced at cutover as a validation error naming a
 * field with no input to fix it.
 *
 * REFLECTIVE ON PURPOSE. A test that listed the fields would have been written from
 * the same mental model that dropped them, and would have dropped them again. This
 * one fails when a field is ADDED to the entity and forgotten, which is the moment
 * the mistake is cheap. That is why the skip list below names fields explicitly and
 * gives each a reason: a blanket skip would let the next one through.
 */
public class SeriesVoRoundTripTest {

    /**
     * Fields deliberately outside the VO round-trip.
     *
     * Both are entity references the REST layer resolves from an id the VO does
     * carry (categoryId, domainId), so round-tripping the object itself would be
     * asserting the wrong thing. Everything else must survive.
     */
    private static final Set<String> RESOLVED_BY_ID = Set.of("category", "domain");

    /**
     * Fields deliberately off the wire entirely.
     *
     * nominalCutoffTimeZone is vestigial. The timezone is a DOMAIN setting and the
     * domains genuinely differ, so a per-series column is a second source that can
     * disagree with the one that counts. cutoffZone() reads the domain and nothing
     * else; the column stays only because dropping it is a migration.
     */
    private static final Set<String> NOT_ON_THE_WIRE = Set.of("nominalCutoffTimeZone",
            // Read on the wire, never written through it. Which model serves a
            // series to the public is the cutover flip -- its own action, with a
            // reason and an audit entry -- and a save that could carry it would be
            // a way round all three.
            "publicAuthority");

    /**
     * Fields the VALUE OBJECT declares that no entity field backs.
     *
     * The probe above walks the ENTITY and catches a setting an admin cannot
     * reach. This set is the mirror image: a field the wire carries and the
     * database does not, which is fine when it is derived and a bug when it is a
     * setting somebody meant to persist. Each is named with its reason, so the
     * next one has to be argued for rather than added.
     */
    private static final Map<String, String> DERIVED_ON_THE_VO = Map.of(
            // The id form of the entity reference the REST layer resolves. The
            // round-trip probe already excludes the entity side for the same reason.
            "domainId", "the id of the domain reference, resolved by the REST layer",
            // An observation about the calendar. Stored, it would go stale the
            // moment the calendar moved on -- which is why RETIRED is a column and
            // dormancy is not.
            "dormant", "derived from the calendar, never stored",
            // A COUNT OF OTHER ROWS, read-only. S-18 locks the citation channel off
            // it, and a value arriving in a save is ignored: updateFromVo does not
            // read it, and nothing could be written to if it did.
            "publishedIssueCount", "read-only, derived: PUBLISHED plus RETIRED issues of the series");

    /**
     * Every field on the wire is either a stored setting or a declared derivation.
     *
     * The failure this catches is the opposite one to the probe above: a field
     * added to the VO, wired into a form, saved by an admin, and silently
     * discarded because nothing on the entity ever receives it. The admin sees
     * their change accepted and gone on the next load.
     */
    @Test
    public void everyWireFieldIsBackedByTheEntityOrDeclaredDerived() {
        Set<String> entityFields = new java.util.LinkedHashSet<>();
        for (Field f : PublicationSeries.class.getDeclaredFields()) {
            entityFields.add(f.getName());
        }

        List<String> unexplained = new ArrayList<>();
        for (Field f : SystemPublicationSeriesVo.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()
                    || entityFields.contains(f.getName())
                    || DERIVED_ON_THE_VO.containsKey(f.getName())) {
                continue;
            }
            unexplained.add("  " + f.getName());
        }

        if (!unexplained.isEmpty()) {
            fail(unexplained.size() + " field(s) are on the wire with no entity field behind them:"
                    + System.lineSeparator() + String.join(System.lineSeparator(), unexplained)
                    + System.lineSeparator()
                    + "Either updateFromVo/toVo is missing a field an admin can edit and lose, or the "
                    + "field is derived -- in which case add it to DERIVED_ON_THE_VO with the reason.");
        }
    }

    @Test
    public void everySettableFieldSurvivesTheVoRoundTrip() throws Exception {
        PublicationSeries source = new PublicationSeries();
        List<Field> fields = settableFields();
        assertFalse(fields.isEmpty(), "reflection found no fields -- the probe would pass vacuously");

        // A DISTINCT, NON-DEFAULT value in every field. Setting a default would let a
        // dropped field pass by coincidence, which is exactly the bug being hunted.
        for (Field f : fields) {
            f.set(source, distinctValue(f, f.get(source)));
        }

        PublicationSeries target = new PublicationSeries();
        target.updateFromVo(source.toVo(SystemPublicationSeriesVo.class));

        List<String> lost = new ArrayList<>();
        for (Field f : fields) {
            Object before = f.get(source);
            Object after = f.get(target);
            if (!comparable(before).equals(comparable(after))) {
                lost.add("  " + f.getName() + ": set " + comparable(before)
                        + ", came back " + comparable(after));
            }
        }

        if (!lost.isEmpty()) {
            fail(lost.size() + " field(s) do not survive entity -> VO -> entity."
                    + System.lineSeparator() + String.join(System.lineSeparator(), lost)
                    + System.lineSeparator()
                    + "Each is a setting an admin cannot reach through the API: absent from the VO, "
                    + "or absent from updateFromVo/toVo. If a field genuinely does not belong on the "
                    + "wire, add it to RESOLVED_BY_ID with the reason -- do not delete this assertion.");
        }
    }

    /**
     * The MONTHLY/YEARLY schedule specifically, named so the failure reads plainly.
     *
     * The reflective probe above already covers these two. This one exists because
     * a reflective failure says "a field was lost" while the rule it breaks -- S-6,
     * and with it the activation of four real series -- is worth stating outright.
     */
    @Test
    public void aYearlySeriesCanCarryTheScheduleSSixDemands() {
        PublicationSeries yearly = new PublicationSeries();
        yearly.setCadence(SeriesCadence.YEARLY);
        yearly.setNominalCutoffMonth(12);
        yearly.setNominalCutoffDayOfMonth(31);
        yearly.setNominalCutoffTime("12:00");

        PublicationSeries reloaded = new PublicationSeries();
        reloaded.updateFromVo(yearly.toVo(SystemPublicationSeriesVo.class));

        assertEquals(Integer.valueOf(12), reloaded.getNominalCutoffMonth(),
                "S-6 requires a month for a YEARLY cadence; without it on the VO the series can "
                        + "never be given one and can never activate");
        assertEquals(Integer.valueOf(31), reloaded.getNominalCutoffDayOfMonth(),
                "S-6 requires a day of the month for a MONTHLY or YEARLY cadence");
    }

    /** Instance fields declared by the entity, minus the two resolved by id. */
    private static List<Field> settableFields() {
        List<Field> fields = new ArrayList<>();
        for (Field f : PublicationSeries.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()
                    || RESOLVED_BY_ID.contains(f.getName())
                    || NOT_ON_THE_WIRE.contains(f.getName())) {
                continue;
            }
            f.setAccessible(true);
            fields.add(f);
        }
        return fields;
    }

    /** A value of the field's type that differs from what it currently holds. */
    private static Object distinctValue(Field f, Object current) {
        Class<?> type = f.getType();
        String name = f.getName();

        if (type == String.class) {
            // messageSortBy is free text; the rest are ids. Any distinct string works.
            return name + "-value";
        }
        if (type == Integer.class || type == int.class) {
            // Within 1-12 so it is legal for nominalCutoffMonth as well.
            return Math.floorMod(name.hashCode(), 12) + 1;
        }
        if (type == Boolean.class || type == boolean.class) {
            return !Boolean.TRUE.equals(current);
        }
        if (type == Date.class) {
            return new Date(1_700_000_000_000L);
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            for (Object c : constants) {
                if (!c.equals(current)) {
                    return c;
                }
            }
            return constants[0];
        }
        if (type == IssueCriteriaVo.class) {
            MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
            node.setValues(new ArrayList<>(List.of("dma-nm")));
            IssueCriteriaVo criteria = new IssueCriteriaVo();
            criteria.getCriteria().add(node);
            return criteria;
        }
        if (type == Map.class) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("issn", "1397-6656");
            return params;
        }
        if (type == List.class) {
            if (name.equals("languages")) {
                return new ArrayList<>(List.of("da", "en"));
            }
            // descs round-trip through createDesc, which sets the back-reference; an
            // empty list still proves the list itself is not dropped.
            return new ArrayList<>();
        }
        throw new IllegalStateException("SeriesVoRoundTripTest has no sample value for "
                + name + " of type " + type.getName() + ". Add one -- do not skip the field, "
                + "because an unskipped field is the only reason this probe caught anything.");
    }

    /** Collections compare by content; everything else by value. */
    private static Object comparable(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return String.valueOf(value);
    }
}
