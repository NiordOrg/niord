package org.niord.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the fixture corpus. Needs no database and no Quarkus -- it reads the
 * committed files and nothing else.
 *
 * The cases are matched BY NAME rather than counted. A count assertion passes
 * as soon as the right number of files exist, which is exactly the state that
 * arises when one hazard is quietly dropped and another added.
 */
public class FixtureCoverageTest {

    private static final String DIR = "/fixtures/publications/";

    /**
     * The hazard cases, each with why it is in the corpus. Every entry must have
     * a fixture file; removing a case means deleting a line here, deliberately,
     * rather than letting a file disappear unnoticed.
     */
    private static final Map<String, String> REQUIRED_CASES = new LinkedHashMap<>() {{
        put("nm-pt-w01-2026", "positive control -- exactly 123 members");
        put("nm-w01-2026", "positive control -- exactly 2 members");
        put("nm-pt-w28-2026", "NULL publishDateTo; the NULL-unsafe form collapses 165 to 47");
        put("nm-w45-2018", "explicit NEGATIVE fixture -- the resolver is expected to differ");
        put("nm-pt-w12-2024", "release-moment precision -- membership changes 4s after the cut-off");
        put("nm-780-18", "NULL publishDateFrom");
        put("nm-1116-22", "back-dated pair");
        put("nm-300-24", "type mutation");
        put("nm-466-26", "release-moment precision -- 62s");
        put("nm-473-26", "rolled-back publish, instance 1 of 3");
        put("nm-962-25", "rolled-back publish, instance 2 of 3");
        put("nm-1046-25", "rolled-back publish, instance 3 of 3");
        put("skydeomraader-2017-ed2", "supersede-moment superset");
        put("skydeomraader-2018", "the union case");
        put("skydeomraader-2026", "31-of-32 pair, first half");
        put("skydeomraader-2027", "31-of-32 pair, second half");
        put("synthetic-boundary-pair", "RI-2 -- no production message sits on a cut-off stamp");
        put("synthetic-empty-operand", "RI-6 -- an empty operand must raise, never resolve");
        put("synthetic-uid-keying", "M-1 -- the production case for this does not reproduce");
    }};

    private static byte[] read(String name) throws Exception {
        try (InputStream in = FixtureCoverageTest.class.getResourceAsStream(DIR + name)) {
            if (in == null) return null;
            return in.readAllBytes();
        }
    }

    @Test
    public void everyRequiredCaseHasAFixture() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> c : REQUIRED_CASES.entrySet()) {
            if (read(c.getKey() + ".json") == null) {
                missing.add(c.getKey() + "  (" + c.getValue() + ")");
            }
        }
        if (!missing.isEmpty()) {
            fail("no fixture file for " + missing.size() + " required case(s):\n  " + String.join("\n  ", missing));
        }
    }

    /**
     * The drift guard, enforced by the build rather than by remembering to
     * re-run the capture script. Any edit to a committed fixture -- including a
     * hand-edit of a synthetic one -- turns this red.
     */
    @Test
    public void everyFixtureMatchesItsRecordedHash() throws Exception {
        byte[] manifest = read("hashes.txt");
        assertNotNull(manifest, "hashes.txt is missing");

        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        int checked = 0;
        for (String line : new String(manifest, StandardCharsets.UTF_8).split("\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.trim().split("\s+", 2);
            assertEquals(2, parts.length, "malformed manifest line: " + line);

            byte[] content = read(parts[1]);
            assertNotNull(content, "manifest lists " + parts[1] + " but the file is not there");

            String actual = HexFormat.of().formatHex(sha.digest(content));
            assertEquals(parts[0], actual,
                    parts[1] + " does not match its recorded hash. Re-run the capture script and review the "
                            + "diff deliberately -- do not relax this check.");
            checked++;
        }
        assertEquals(REQUIRED_CASES.size() <= checked, true,
                "manifest covers " + checked + " files, fewer than the " + REQUIRED_CASES.size() + " required cases");
    }

    @Test
    public void syntheticFixturesSaySo() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (String name : REQUIRED_CASES.keySet()) {
            byte[] content = read(name + ".json");
            if (content == null) continue; // reported by the coverage test
            JsonNode root = mapper.readTree(content);
            boolean flagged = root.path("synthetic").asBoolean(false);
            if (name.startsWith("synthetic-")) {
                assertTrue(flagged, name + " is authored but not marked \"synthetic\": true");
                assertTrue(root.hasNonNull("why"), name + " must record WHY it could not be captured");
            } else {
                assertTrue(!flagged, name + " is captured from a live system but claims to be synthetic");
            }
        }
    }

    /** Captured member sets must agree with the count recorded beside them. */
    @Test
    public void capturedCountsAgreeWithTheirMembers() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (String name : REQUIRED_CASES.keySet()) {
            byte[] content = read(name + ".json");
            if (content == null || name.startsWith("synthetic-")) continue;
            JsonNode root = mapper.readTree(content);
            if (!root.has("members")) continue; // single-message fixtures carry no member list

            int declared = root.has("messageCount") ? root.get("messageCount").asInt() : root.path("memberCount").asInt(-1);
            if (declared < 0) fail(name + " has members but records no count to check them against");
            assertEquals(declared, root.get("members").size(),
                    name + " records " + declared + " members but carries " + root.get("members").size());
        }
    }

    // ---------------------------------------------------------------- manifest

    private JsonNode manifest() throws Exception {
        byte[] raw = read("manifest.json");
        assertNotNull(raw, "manifest.json is missing");
        return new ObjectMapper().readTree(raw);
    }

    /**
     * The blocklist is data, not quietly-absent files. Every entry states why the
     * recorded set cannot be trusted as an oracle, so the decision stays
     * reviewable rather than looking like an oversight.
     */
    @Test
    public void blocklistedOraclesAreDeclaredWithAReason() throws Exception {
        JsonNode blocklist = manifest().path("blocklist");
        assertTrue(blocklist.isArray() && blocklist.size() > 0, "manifest carries no blocklist");

        for (JsonNode entry : blocklist) {
            String tag = entry.path("tag").asText("");
            assertTrue(!tag.isBlank(), "blocklist entry with no tag name");

            String reason = entry.path("reason").asText("");
            assertTrue(!reason.isBlank(), tag + " is blocklisted with no reason");

            // "Blocklisted" means not an oracle. It does not always mean "not
            // captured": the union-over-window annuals are captured on purpose,
            // because asserting the resolver DIFFERS from them is itself a test.
            if (!entry.path("fixture").asBoolean(false)) {
                assertTrue(read(tag + ".json") == null,
                        tag + " is blocklisted with fixture:false but a fixture file exists. Either it is an "
                                + "oracle or it is not -- do not leave it ambiguous.");
            }
        }
    }

    /** An unlocked tag is not an oracle, and the manifest is where that stays provable. */
    @Test
    public void everyCapturedTagWasLocked() throws Exception {
        JsonNode tags = manifest().path("tags");
        assertTrue(tags.isArray() && tags.size() > 0, "manifest carries no tag rows");
        for (JsonNode tag : tags) {
            assertTrue(tag.path("locked").asBoolean(false),
                    tag.path("tag").asText() + " was captured from an UNLOCKED tag; its membership can still move");
            assertTrue(tag.hasNonNull("tagId"), tag.path("tag").asText() + " has no resolved tagId recorded");
            assertTrue(tag.hasNonNull("created"), tag.path("tag").asText() + " has no created stamp recorded");
        }
    }

    /**
     * The recorded messageCount must equal what the fixture actually carries.
     * This is the same guard the capture script applies at fetch time, held
     * after the fact so a hand-edited file cannot slip past it.
     */
    @Test
    public void manifestCountsAgreeWithTheCapturedFiles() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (JsonNode tag : manifest().path("tags")) {
            String name = tag.path("tag").asText();
            byte[] content = read(tag.path("fixture").asText());
            assertNotNull(content, "manifest lists a fixture for " + name + " that is not there");

            int members = mapper.readTree(content).path("members").size();
            assertEquals(tag.path("messageCount").asInt(), members,
                    name + ": manifest records messageCount " + tag.path("messageCount").asInt()
                            + " but the fixture carries " + members + " members");
        }
    }
}
