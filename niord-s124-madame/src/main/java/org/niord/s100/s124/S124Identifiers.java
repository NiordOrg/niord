/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.s100.s124;

/**
 * Helpers for deriving the various identifier flavours that S-124 requires.
 * <p>
 * Niord short ids such as {@code "Local Warning-120-26"} contain characters that are illegal in the identifier types
 * S-124 uses, so they have to be adapted per target:
 * <ul>
 * <li>{@code gml:id} is of XSD type {@code ID}, i.e. an {@code NCName}: no spaces and no colons.</li>
 * <li>The name space specific string of an MRN (RFC 8141) admits neither spaces nor non-ASCII characters.</li>
 * <li>The S-100 Part 17 dataset file name is built from alphanumerics only.</li>
 * </ul>
 * Every conversion is deterministic, so ids and the {@code #id} references pointing at them stay in agreement.
 */
final class S124Identifiers {

    /**
     * The IHO S-62 producer code of the Danish Maritime Authority, taken from the IHO GI Registry Producer Code
     * Register. S-124 clause 4.3.3 wants this code rather than the agency's name.
     */
    static final String PRODUCER_CODE = "DK00";

    private S124Identifiers() {
    }

    /**
     * Converts a raw identifier into a valid XML {@code NCName} so that it can be used as a {@code gml:id}.
     * <p>
     * Illegal characters - spaces and colons in particular - become {@code '-'}, and a leading character that may not
     * start an {@code NCName} is prefixed with {@code '_'}.
     *
     * @param raw
     *            the raw identifier, may be null or blank
     * @return an identifier that satisfies the {@code NCName} production
     */
    static String toNCName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "_";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            // NCName permits letters, digits, '.', '-' and '_'; everything else (space, ':', '/', ...) is folded away
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('-');
            }
        }
        // An NCName may not start with a digit, a '.' or a '-'
        char first = sb.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            sb.insert(0, '_');
        }
        return sb.toString();
    }

    /**
     * Converts a raw identifier into a segment that is legal inside the name space specific string of an MRN.
     * <p>
     * The result is lower case ASCII; anything outside {@code [a-z0-9.-_]} becomes {@code '-'}.
     *
     * @param raw
     *            the raw identifier, may be null or blank
     * @return an MRN-safe segment
     */
    static String toMrnSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        // Locale.ROOT: a Turkish default locale would otherwise map 'I' to a dotless 'ı'
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('-');
            }
        }
        return sb.toString();
    }

    /**
     * Reduces a raw identifier to the alphanumeric token that S-100 Part 17 clause 17-4.3 allows in a dataset file
     * name.
     *
     * @param raw
     *            the raw identifier, may be null or blank
     * @return an alphanumeric token, never blank
     */
    static String toFileNameToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "0";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "0" : sb.toString();
    }
}
