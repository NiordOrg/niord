package org.niord.core.publication.series.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A slice of the real archive, as data a test can rebuild locally.
 *
 * WHY THIS EXISTS. Every defect in the import so far -- the content interval, the
 * unreleased cut-off, the bounds check, the double-counted release lag -- was
 * found by deploying, importing 1,077 rows, and reading the shadow diff. That is
 * a twenty-minute round trip for a one-line question, and it needs somebody to
 * push the deploy button. The interesting behaviour does not need the whole
 * estate: it needs consecutive releases of one cadenced series, with the real
 * publish dates, because the bugs live at the interval boundaries.
 *
 * WHAT IS REAL HERE. The publish dates, types and statuses are the archive's own,
 * harvested from the frozen member snapshots. So are the release times, which is
 * what makes the twenty-to-thirty-minute lag between a nominal cut-off and the
 * release that actually closed it reproduce exactly.
 *
 * WHAT IS NOT. A frozen member records the message AS IT WAS at freeze. The live
 * archive has moved on -- roughly three quarters of the members a replay reports
 * missing are now CANCELLED or EXPIRED, which is a property of time passing
 * rather than of any code. A local replay cannot show that and will look greener
 * than the real one. It is the right trade: the decay is unfixable and uniform,
 * while everything the slice does reproduce is a bug somebody can act on.
 */
public final class EstateSlice {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESOURCE = "/fixtures/legacy-estate/estate-harvest.json";

    /** One member of one issue, as the archive froze it. */
    public record Member(String uid, String shortId, String type, String status,
                         Date publishFrom, Date publishTo) {
    }

    /** One release: the interval it claims, and what it froze. */
    public record Issue(String publicId, String seriesId, Date intervalFrom, Date intervalTo,
                        Date cutoffStampedAt, Date publicFrom, Date publicTo, String status,
                        List<Member> members) {
    }

    private EstateSlice() {
    }

    /** Whether the harvest is present. Absent on a machine that has never pulled it. */
    public static boolean available() {
        try (InputStream in = EstateSlice.class.getResourceAsStream(RESOURCE)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The issues of one series, oldest release first.
     *
     * Ordered by the public window rather than by the interval, because the
     * interval is the thing under test and ordering by it would let a bug in it
     * hide by reordering its own evidence.
     */
    public static List<Issue> issuesOf(String seriesId) {
        List<Issue> out = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : issueNodes().entrySet()) {
            JsonNode n = e.getValue();
            if (!seriesId.equals(n.path("seriesId").asText(null))) {
                continue;
            }
            List<Member> members = new ArrayList<>();
            for (JsonNode m : n.path("members")) {
                members.add(new Member(
                        m.path("uid").asText(null),
                        m.path("shortId").asText(null),
                        m.path("type").asText(null),
                        m.path("status").asText(null),
                        date(m, "from"),
                        date(m, "to")));
            }
            out.add(new Issue(e.getKey(), seriesId,
                    date(n, "intervalFrom"), date(n, "intervalTo"), date(n, "cutoffStampedAt"),
                    date(n, "publicFrom"), date(n, "publicTo"),
                    n.path("status").asText(null), members));
        }
        out.sort((a, b) -> {
            long x = a.publicFrom() == null ? 0 : a.publicFrom().getTime();
            long y = b.publicFrom() == null ? 0 : b.publicFrom().getTime();
            return Long.compare(x, y);
        });
        return out;
    }

    /**
     * A series as the import actually shaped it, criteria and all.
     *
     * Harvested rather than reconstructed. Which time relation a series has, and
     * which criteria nodes, is the thing a replay is checking the consequences
     * of -- rebuilding it from a guess here would test the guess.
     */
    public record Series(String seriesId, String cadence, String timeRelation,
                         Boolean aliveAtCutoff, String contentMode, String criteriaJson) {

        public boolean inForce() {
            return "IN_FORCE_AT_CUTOFF".equals(timeRelation);
        }
    }

    /** The shape of one series, or null when the harvest does not carry it. */
    public static Series series(String seriesId) {
        JsonNode n = root().path("series").path(seriesId);
        if (n.isMissingNode()) {
            return null;
        }
        JsonNode criteria = n.path("criteria");
        return new Series(seriesId,
                n.path("cadence").asText(null),
                n.path("timeRelation").asText(null),
                n.path("aliveAtCutoff").isBoolean() ? n.path("aliveAtCutoff").asBoolean() : null,
                n.path("contentMode").asText(null),
                criteria.isObject() ? criteria.toString() : null);
    }

    /**
     * Issues whose frozen membership is identical to another issue's.
     *
     * Eight groups in the estate, two of them three ways. They are the New Year
     * turnover editions: legacy could not vary a single issue, so a throwaway
     * template was created, one edition published from it, and the message tag
     * reused -- so two or three publications point at the same members.
     *
     * It matters to any replay because two issues claiming the same content cannot
     * both be right about the period they cover, and no interval fixes that. A
     * replay should say so rather than count it as a disagreement it caused.
     */
    public static Set<String> issuesSharingAMemberSet() {
        Map<String, List<String>> byMembership = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : issueNodes().entrySet()) {
            List<String> uids = new ArrayList<>();
            for (JsonNode m : e.getValue().path("members")) {
                uids.add(m.path("uid").asText(null));
            }
            if (uids.isEmpty()) {
                continue;
            }
            java.util.Collections.sort(uids);
            byMembership.computeIfAbsent(String.join(",", uids), k -> new ArrayList<>())
                    .add(e.getKey());
        }

        Set<String> out = new java.util.LinkedHashSet<>();
        byMembership.values().stream().filter(g -> g.size() > 1).forEach(out::addAll);
        return out;
    }

    private static Map<String, JsonNode> issueNodes() {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        JsonNode issues = root().path("issues");
        issues.fieldNames().forEachRemaining(id -> out.put(id, issues.path(id)));
        return out;
    }

    private static JsonNode root() {
        try (InputStream in = EstateSlice.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing; run the harvest first");
            }
            return MAPPER.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    private static Date date(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isNumber() ? new Date(v.asLong()) : null;
    }
}
