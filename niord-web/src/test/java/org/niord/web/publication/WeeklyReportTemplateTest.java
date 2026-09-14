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
import org.junit.jupiter.api.Test;
import org.niord.model.message.MessageVo;
import org.niord.model.message.Type;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The weekly cover line: which year the volume is counted from.
 *
 * ONE CELL of one table, and it read the CLOCK. The volume is the number of
 * years the publication has run, so it is a fact about the edition and not about
 * the day somebody pressed print -- and the weekly is printed again whenever it
 * is amended. A December issue corrected in January, and any re-render of an
 * archived edition, stamped the printing year's volume on a document of another
 * year, with nothing anywhere to notice it by.
 *
 * The render injects {@code yearNumber}, the year the numbering derived, for
 * exactly this kind of arithmetic; the printed year beside it is free text and is
 * not arithmetic to do. The clock remains the answer for the one caller that has
 * no issue behind it -- the legacy print dialog renders this same template -- and
 * that fallback is asserted rather than assumed, because losing it would fail the
 * whole render rather than one cell.
 *
 * Rendered through FreeMarker rather than through the application, with the two
 * substitutions {@link AccumulatedReportTemplateTest} uses: the shared
 * {@code message-support.ftl} is stubbed, and {@code text()} answers with the key
 * it was asked for and its arguments, so the volume can be read off the document.
 */
public class WeeklyReportTemplateTest {

    private static final String TEMPLATE = "nm-report-pdf.ftl";

    /** The shared macros, stood in for: this is a test about the cover line. */
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

    /** Every label answers with its own key, and with its arguments in brackets. */
    private static class KeyText implements TemplateMethodModelEx {
        @Override
        public Object exec(List arguments) {
            if (arguments.isEmpty()) {
                return "";
            }
            StringBuilder out = new StringBuilder(String.valueOf(arguments.get(0)));
            for (int i = 1; i < arguments.size(); i++) {
                out.append(i == 1 ? "(" : ",").append(String.valueOf(arguments.get(i)));
            }
            return arguments.size() > 1 ? out.append(")").toString() : out.toString();
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

    // ------------------------------------------------------------- the fixture

    private static MessageVo message(String id, int number, Type type) {
        MessageVo m = new MessageVo();
        m.setId(id);
        m.setNumber(number);
        m.setType(type);
        return m;
    }

    /** A week of two notices: the smallest document the cover belongs to. */
    private static Map<String, Object> aWeek() {
        List<MessageVo> messages = new ArrayList<>(List.of(
                message("uid-a", 101, Type.TEMPORARY_NOTICE),
                message("uid-b", 102, Type.MISCELLANEOUS_NOTICE)));

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("messages", messages);
        model.put("areaHeadings", false);
        model.put("week", "36");
        model.put("weekTo", "");
        model.put("year", "2025");
        model.put("yearNumber", 2025);
        model.put("edition", "1");
        model.put("ISSN", "1397-999X");
        model.put("timeZone", "Europe/Copenhagen");
        return model;
    }

    /** The volume the clock would print, for the edition the clock has no year for. */
    private static int volumeOfThisYear() {
        return LocalDate.now().getYear() - 1884;
    }

    // -------------------------------------------------------------- the checks

    /**
     * THE DEFECT THIS PINS. The volume is the ISSUE's year, not the printer's.
     *
     * Asserted as the 2025 edition rendered now: the two numbers differ, so a
     * document that quietly went on counting from the clock fails here rather
     * than a year after it shipped.
     */
    @Test
    public void theVolumeIsCountedFromTheYearTheNumberingDerived() throws Exception {
        String html = render(aWeek());

        assertTrue(html.contains("pdf.volume(141)"),
                "the 2025 edition does not count its volume from 2025; a December issue amended in "
                        + "January prints the volume of the year it was printed in");
        assertFalse(volumeOfThisYear() != 141
                        && html.contains("pdf.volume(" + volumeOfThisYear() + ")"),
                "the volume was counted from the year this test is being run in");
    }

    /**
     * The printing date stays, and stays the clock's.
     *
     * It is the one part of the weekly cover that IS about the day it was made,
     * and it sits on the same line as the volume -- so a change to one of them is
     * the plausible way to lose the other.
     */
    @Test
    public void thePrintingDateIsStillTheDayTheDocumentWasMade() throws Exception {
        String html = render(aWeek());

        // Formatted the way the template formats it, in the same default locale
        // FreeMarker is configured with here.
        String today = new SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault())
                .format(new Date());
        assertTrue(html.contains(today),
                "the cover no longer says which day the document was printed on");
    }

    /**
     * With no derived year the clock still answers, and the document renders.
     *
     * The legacy print dialog renders this template for an ad-hoc selection of
     * messages with no issue behind it at all. A guard that printed nothing there
     * would empty the cell on the one caller that has only the clock to go on.
     */
    @Test
    public void anAdHocPrintWithNoIssueBehindItCountsFromTheClock() throws Exception {
        Map<String, Object> model = aWeek();
        model.remove("yearNumber");

        String html = render(model);

        assertTrue(html.contains("pdf.volume(" + volumeOfThisYear() + ")"),
                "a render with no derived year lost its volume line; the print dialog has no issue "
                        + "to take one from");
    }

    /** And a free-text year label never reaches the arithmetic. */
    @Test
    public void aFreeTextYearLabelIsPrintedAndNotComputedFrom() throws Exception {
        Map<String, Object> model = aWeek();
        model.put("year", "To tusind og femogtyve");
        model.put("week", "36+37");

        String html = render(model);

        assertTrue(html.contains("To tusind og femogtyve"),
                "the cover does not print the year the edition is called");
        assertTrue(html.contains("pdf.volume(141)"),
                "the label reached the volume arithmetic, which fails the whole render the first "
                        + "time a year is written out in words");
    }
}
