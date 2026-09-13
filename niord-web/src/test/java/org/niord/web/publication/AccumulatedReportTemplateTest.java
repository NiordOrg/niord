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

package org.niord.web.publication;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import org.junit.jupiter.api.Test;
import org.niord.model.message.MessageVo;
import org.niord.model.message.Type;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the accumulated template does with the sections it is handed.
 *
 * THE TEMPLATE IS THE CONTRACT between the render request and the document: the
 * request says which messages a section holds and in which order, and nothing
 * else in the system can tell whether the template obeyed it. A publish that
 * printed week 12's notices under week 11's heading, or dropped the section
 * somebody added by hand, would produce a perfectly valid PDF and no failure
 * anywhere.
 *
 * It is rendered here through FreeMarker rather than through the application,
 * with two substitutions and no others: the shared {@code message-support.ftl}
 * is STUBBED -- rendering one real message needs the whole message model,
 * directives, attachments and dictionaries, and what is under test is the
 * sectioning above it, so the stub prints the ids it was given and the
 * assertions read those -- and {@code text()} answers with the key it was asked
 * for, so a heading can be checked for WHICH label it used rather than for one
 * language's wording. The template itself, and the intro stub it includes, are
 * the shipped files, loaded off the classpath.
 */
public class AccumulatedReportTemplateTest {

    private static final String TEMPLATE = "nm-accumulated-report-pdf.ftl";

    /**
     * The shared macros, stood in for.
     *
     * {@code renderMessageList} prints one line per message, in the order it was
     * handed them, which is exactly the fact every assertion below is about.
     */
    private static final String MESSAGE_SUPPORT_STUB = """
            <#macro pageSizeStyle frontPage=true></#macro>
            <#macro renderDefaultHeaderAndFooter headerText frontPage=true>
            <div class="header">${headerText}</div>
            </#macro>
            <#macro renderMessageList messages areaHeadings=true prefix="" draft=false link=false>
            <#list messages as msg>
            <div class="msg">${prefix}:${msg.id}</div>
            </#list>
            </#macro>
            """;

    /** The dictionary, stood in for: every label answers with its own key. */
    private static class KeyText implements TemplateMethodModelEx {
        @Override
        public Object exec(List arguments) {
            return arguments.isEmpty() ? "" : String.valueOf(arguments.get(0));
        }
    }

    private String render(Map<String, Object> model) throws Exception {
        StringTemplateLoader stubs = new StringTemplateLoader();
        stubs.putTemplate("message-support.ftl", MESSAGE_SUPPORT_STUB);

        TemplateLoader loader = new MultiTemplateLoader(new TemplateLoader[] {
                stubs,
                new ClassTemplateLoader(getClass().getClassLoader(), "templates/messages")
        });

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setTemplateLoader(loader);
        cfg.setObjectWrapper(new DefaultObjectWrapper(Configuration.VERSION_2_3_31));
        cfg.setLocalizedLookup(false);

        model.put("text", new KeyText());

        Template template = cfg.getTemplate(TEMPLATE);
        StringWriter out = new StringWriter();
        template.process(model, out);
        return out.toString();
    }

    // ------------------------------------------------------------ the fixture

    private static MessageVo message(String id, int number, Type type) {
        MessageVo m = new MessageVo();
        m.setId(id);
        m.setNumber(number);
        m.setType(type);
        return m;
    }

    private static Map<String, Object> group(String publicId, String name, Integer week,
                                             Integer year, Date cutoff, List<String> ids,
                                             boolean manual) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("publicId", publicId);
        g.put("name", name);
        g.put("week", week);
        g.put("weekTo", null);
        g.put("year", year);
        g.put("cutoff", cutoff);
        g.put("messageIds", ids);
        g.put("manual", manual);
        return g;
    }

    /**
     * Two weeks and a section added by hand, over five messages.
     *
     * The message list is in PRINTED order -- which is the order publish froze,
     * week by week, the manual include last -- and the groups partition it.
     */
    private Map<String, Object> yearOfTwoWeeks() {
        List<MessageVo> messages = new ArrayList<>(List.of(
                message("uid-11a", 101, Type.TEMPORARY_NOTICE),
                message("uid-11b", 102, Type.MISCELLANEOUS_NOTICE),
                message("uid-12a", 201, Type.TEMPORARY_NOTICE),
                message("uid-12b", 202, Type.TEMPORARY_NOTICE),
                message("uid-hand", 900, Type.TEMPORARY_NOTICE)));

        List<Map<String, Object>> groups = List.of(
                group("week-11", "NtM 11/2024", 11, 2024, new Date(1_710_000_000_000L),
                        List.of("uid-11a", "uid-11b"), false),
                group("week-12", "NtM 12/2024", 12, 2024, new Date(1_710_600_000_000L),
                        List.of("uid-12a", "uid-12b"), false),
                group(null, null, null, null, null, List.of("uid-hand"), true));

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("messages", messages);
        model.put("groups", groups);
        model.put("areaHeadings", true);
        model.put("year", "2024");
        model.put("edition", "1");
        model.put("ISSN", "1397-999X");
        model.put("timeZone", "Europe/Copenhagen");
        return model;
    }

    /**
     * The same year with a third week wedged between the two, which filed nothing.
     *
     * The document still covers it: it is one of the weeks the year was compiled
     * from, and the ordered list simply has no row that came from it.
     */
    private Map<String, Object> yearWithAWeekThatPrintedNothing() {
        List<MessageVo> messages = new ArrayList<>(List.of(
                message("uid-11a", 101, Type.TEMPORARY_NOTICE),
                message("uid-12a", 201, Type.TEMPORARY_NOTICE),
                message("uid-hand", 900, Type.TEMPORARY_NOTICE)));

        List<Map<String, Object>> groups = List.of(
                group("week-11", "NtM 11/2024", 11, 2024, new Date(1_710_000_000_000L),
                        List.of("uid-11a"), false),
                group("week-12", "NtM 12/2024", 12, 2024, new Date(1_710_600_000_000L),
                        List.of(), false),
                group("week-13", "NtM 13/2024", 13, 2024, new Date(1_711_200_000_000L),
                        List.of("uid-12a"), false),
                group(null, null, null, null, null, List.of("uid-hand"), true));

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("messages", messages);
        model.put("groups", groups);
        model.put("areaHeadings", true);
        model.put("year", "2024");
        model.put("edition", "1");
        model.put("ISSN", "1397-999X");
        model.put("timeZone", "Europe/Copenhagen");
        return model;
    }

    /** How many times a string occurs. */
    private static int occurrences(String html, String needle) {
        int n = 0;
        int at = 0;
        while ((at = html.indexOf(needle, at)) >= 0) {
            n++;
            at += needle.length();
        }
        return n;
    }

    /** The printed ids, in the order the document prints them. */
    private static List<String> printed(String html) {
        List<String> out = new ArrayList<>();
        int at = 0;
        while ((at = html.indexOf("<div class=\"msg\">", at)) >= 0) {
            int from = at + "<div class=\"msg\">".length();
            int to = html.indexOf("</div>", from);
            out.add(html.substring(from, to).trim());
            at = to;
        }
        return out;
    }

    // ------------------------------------------------------------- the checks

    /**
     * A week's members print under that week's heading, in the order it printed
     * them, and the miscellaneous half of a week stays inside that week.
     */
    @Test
    public void eachWeekPrintsUnderItsOwnHeadingInItsOwnOrder() throws Exception {
        String html = render(yearOfTwoWeeks());

        // The LAST occurrence of a name is its section heading; the first is its
        // line in the table of contents, which is a different statement.
        int week11 = html.lastIndexOf("NtM 11/2024");
        int week12 = html.lastIndexOf("NtM 12/2024");
        assertTrue(week11 >= 0, "week 11 has no heading at all");
        assertTrue(week12 > week11, "the weeks are not printed in the order they were handed over");

        int first12 = html.indexOf("uid-12a");
        int last12 = html.indexOf("uid-12b");
        assertTrue(first12 > week12, "week 12's members print before week 12's heading");
        assertTrue(last12 > first12, "week 12's members lost the order the week froze them in");

        // The announcement of week 11 belongs to week 11, not to a pooled section
        // at the end: each week is the weekly document it was, split as that
        // document was split.
        assertTrue(html.indexOf("uid-11b") < week12,
                "an announcement was lifted out of its own week into a later section");
    }

    /** Everything prints exactly once, and what came from no week comes last. */
    @Test
    public void whatWasAddedByHandIsTheLastSection() throws Exception {
        String html = render(yearOfTwoWeeks());

        assertEquals(List.of("g0:uid-11a", "g0misc:uid-11b", "g1:uid-12a", "g1:uid-12b",
                        "g2:uid-hand"),
                printed(html),
                "the document is not the two weeks in order with the manual section last");

        int hand = html.lastIndexOf("pdf.accumulated.added_by_hand");
        assertTrue(hand > 0, "the manual section is not headed as added by hand");
        assertTrue(hand > html.lastIndexOf("NtM 12/2024"),
                "the manual section is headed before the last week a week printed");
        assertTrue(html.lastIndexOf("uid-hand") > hand,
                "what was added by hand prints above the heading that says so");
    }

    /** The cover is the ISSUE's year, and no part of the document reads the clock. */
    @Test
    public void theCoverIsTheIssuesOwnYear() throws Exception {
        String html = render(yearOfTwoWeeks());

        assertTrue(html.contains("pdf.accumulated.title"), "the cover has no title");
        assertTrue(html.contains("ISSN 1397-999X"), "the cover has no ISSN");
        assertTrue(html.contains("2024"), "the cover does not carry the year the issue is of");
        assertTrue(html.contains("pdf.volume"), "the cover has no volume line");
        // The table of contents names the sections, so a thousand-notice document
        // can be navigated by week.
        assertTrue(html.indexOf("pdf.toc") < html.indexOf("NtM 11/2024"),
                "the table of contents does not precede the first week");
    }

    /**
     * A week that filed nothing is still a section, and says so.
     *
     * The sections are the record of what the year covered, not of what happened
     * to have rows: a year that quietly prints fifty-one of its fifty-two weeks
     * is a document claiming a week was never covered, and there is nothing in it
     * for a reader to notice the claim by. So the week is in the table of
     * contents, it has its own heading and its own week and cut-off line, and
     * where the tables would be there is one sentence.
     */
    @Test
    public void aweekThatPrintedNothingIsListedAndPrintedWithASentence() throws Exception {
        String html = render(yearWithAWeekThatPrintedNothing());

        // Twice: once in the contents, once as the heading of its own section.
        assertEquals(2, occurrences(html, "NtM 12/2024"),
                "the week that filed nothing is not both listed and printed");
        int listed = html.indexOf("NtM 12/2024");
        int heading = html.lastIndexOf("NtM 12/2024");
        assertTrue(listed > html.indexOf("pdf.toc"),
                "the empty week is missing from the table of contents");
        assertTrue(heading > listed, "the empty week has no heading of its own");

        int sentence = html.indexOf("pdf.accumulated.no_messages");
        assertTrue(sentence > heading,
                "the empty week's section does not say that it printed nothing");
        assertTrue(sentence < html.lastIndexOf("NtM 13/2024"),
                "the sentence falls outside the section it belongs to");
        assertEquals(1, occurrences(html, "pdf.accumulated.no_messages"),
                "a section that printed something carries the sentence too");

        // The info line is the same one a full week gets, so the reader can see
        // WHICH week printed nothing and what it was closed at.
        assertTrue(html.substring(heading, sentence).contains("pdf.accumulated.cutoff"),
                "the empty week's section has no cut-off line");
    }

    /** And the weeks that did print are untouched by it. */
    @Test
    public void theWeeksAroundAnEmptyOnePrintExactlyAsBefore() throws Exception {
        String html = render(yearWithAWeekThatPrintedNothing());

        assertEquals(List.of("g0:uid-11a", "g2:uid-12a", "g3:uid-hand"), printed(html),
                "an empty section changed what the sections that have rows print");
        assertTrue(html.lastIndexOf("pdf.accumulated.added_by_hand") > html.lastIndexOf("NtM 13/2024"),
                "what was added by hand is no longer the last section");
    }

    /**
     * A document with no sections still renders.
     *
     * The template is reachable from a preview of an issue that compiled nothing
     * and from the report editor, and a template that could only render a
     * compilation would fail there with a FreeMarker stack trace instead of a
     * document.
     */
    @Test
    public void amodelWithoutGroupsStillRendersTheFlatList() throws Exception {
        Map<String, Object> model = yearOfTwoWeeks();
        model.remove("groups");

        String html = render(model);

        assertEquals(List.of("nm:uid-11a", "nm:uid-12a", "nm:uid-12b", "nm:uid-hand",
                        "nmmisc:uid-11b"),
                printed(html),
                "without sections the document is not the ordered list, notices then "
                        + "announcements");
    }
}
