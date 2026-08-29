/*
 * Copyright 2026 Danish Emergency Management Agency.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.publication.series.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The divergences the replay is allowed to find.
 *
 * The mass historical replay is a DIAGNOSTIC, not a green/red gate on the
 * archive being reproducible -- three independent sweeps reached that
 * conclusion, and building it as a plain gate would be a design error. Roughly
 * 48 publications have no membership semantics at all, four annual issues are
 * the answer to no query at any instant, and ten annexes have no recoverable
 * member list. Demanding zero diffs would demand the impossible.
 *
 * So the gate is {@code actualDiffs subset-of manifest} instead. That keeps the
 * useful half -- a NEW divergence, one nobody has explained, still fails the
 * build -- while accepting the ones that were measured and understood.
 *
 * TWO RULES STOP THIS ROTTING INTO A BLANKET SUPPRESSION:
 *
 *   1. An entry that NO LONGER DIVERGES fails. Otherwise the manifest quietly
 *      accumulates permission for things that stopped happening, and the day one
 *      of them comes back nobody hears about it.
 *   2. An entry with a blank reason fails. A divergence nobody can explain in a
 *      sentence has not been understood, and recording it as expected is
 *      pretending otherwise.
 *
 * The manifest is a committed resource rather than a database table because it
 * is a set of claims about the archive that a person signed off, and those
 * belong in review alongside the code that acts on them.
 */
public final class ExpectedDiffManifest {

    /** The resource every environment loads unless a test points elsewhere. */
    public static final String DEFAULT_RESOURCE =
            "/publication-series/replay/expected-diff-manifest.json";

    /**
     * One expected divergence.
     *
     * The deltas are UIDs, not short ids: a short id is display text and is
     * reused across years, while the uid is what the frozen snapshot and the
     * resolver both key on. Comparing anything else would make the manifest
     * agree with the replay for the wrong reason.
     */
    public record Entry(String publicId, DivergenceClass divergenceClass,
                        Set<String> missing, Set<String> extra, String reason) {

        /** Does this entry account for what the replay actually found? */
        public boolean covers(ReplayReport.IssueDiff diff) {
            return publicId.equals(diff.publicId())
                    && missing.containsAll(diff.missing())
                    && extra.containsAll(diff.extra());
        }

        public boolean isEmpty() {
            return missing.isEmpty() && extra.isEmpty();
        }
    }

    private final Map<String, Entry> byPublicId;

    private ExpectedDiffManifest(Map<String, Entry> byPublicId) {
        this.byPublicId = byPublicId;
    }

    public List<Entry> entries() {
        return new ArrayList<>(byPublicId.values());
    }

    public Entry get(String publicId) {
        return byPublicId.get(publicId);
    }

    public int size() {
        return byPublicId.size();
    }

    /** Loads and validates. A malformed manifest fails loudly rather than empty. */
    public static ExpectedDiffManifest load(String resource) {
        try (InputStream in = ExpectedDiffManifest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no expected-diff manifest at " + resource
                        + ". An absent manifest is not an empty one: it would turn the replay gate "
                        + "into a pass for every divergence at once.");
            }
            return read(new ObjectMapper().readTree(in), resource);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
    }

    /**
     * Parses manifest JSON directly.
     *
     * Exists so the gate's tests can drive cases the committed manifest does not
     * contain -- an entry that stopped diverging, a blank reason -- through the
     * SAME validation production uses. A test that parsed manifests its own way
     * would be testing its own parser.
     */
    public static ExpectedDiffManifest parse(String json) {
        try {
            return read(new ObjectMapper().readTree(json), "<inline>");
        } catch (IOException e) {
            throw new IllegalStateException("cannot parse manifest json", e);
        }
    }

    private static ExpectedDiffManifest read(JsonNode root, String label) {
        JsonNode list = root.path("expectedDiffs");
        if (!list.isArray()) {
            throw new IllegalStateException(label + " has no 'expectedDiffs' array");
        }

        Map<String, Entry> out = new LinkedHashMap<>();
        for (JsonNode n : list) {
            Entry entry = parseEntry(n, label);
            if (out.put(entry.publicId(), entry) != null) {
                throw new IllegalStateException("two manifest entries for issue '"
                        + entry.publicId() + "'. One issue diverges in one way, or the reasons "
                        + "have to be merged into a single entry that says so.");
            }
        }
        return new ExpectedDiffManifest(out);
    }

    private static Entry parseEntry(JsonNode n, String resource) {
        String publicId = text(n, "publicId");
        if (publicId == null) {
            throw new IllegalStateException(resource + " has an entry with no publicId");
        }

        String reason = text(n, "reason");
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("manifest entry '" + publicId + "' has no reason. "
                    + "A divergence nobody can explain in a sentence has not been understood, and "
                    + "recording it as expected is pretending otherwise.");
        }

        String className = text(n, "divergenceClass");
        DivergenceClass divergence;
        try {
            divergence = DivergenceClass.valueOf(className);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("manifest entry '" + publicId + "' names divergenceClass '"
                    + className + "', which is not one of the measured classes. Naming a class is a "
                    + "claim about mechanism -- if none fits, add one with its own evidence.");
        }

        Entry entry = new Entry(publicId, divergence,
                uids(n, "missing"), uids(n, "extra"), reason);

        if (entry.isEmpty()) {
            throw new IllegalStateException("manifest entry '" + publicId + "' expects no missing "
                    + "and no extra members, so it permits nothing and only hides the fact that "
                    + "this issue is being watched.");
        }
        return entry;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Set<String> uids(JsonNode n, String field) {
        Set<String> out = new LinkedHashSet<>();
        JsonNode v = n.get(field);
        if (v != null && v.isArray()) {
            v.forEach(x -> out.add(x.asText()));
        }
        return out;
    }
}
