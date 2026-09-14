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

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.sun.net.httpserver.HttpServer;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateMethodModelEx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.niord.core.script.NiordAppObjectWrapper;
import org.niord.core.script.pdf.HtmlToPdfRenderer;
import org.niord.core.script.pdf.ResourceFetchLog;
import org.niord.model.message.AreaDescVo;
import org.niord.model.message.AreaVo;
import org.niord.model.message.ChartVo;
import org.niord.model.message.MainType;
import org.niord.model.message.MessageDescVo;
import org.niord.model.message.MessagePartDescVo;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessagePartVo;
import org.niord.model.message.MessageVo;
import org.niord.model.message.ReferenceType;
import org.niord.model.message.ReferenceVo;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the seconds go when an annual is produced.
 *
 * A compiled annual is a thousand-odd frozen members drawn as five hundred
 * pages, twice, and the whole of that cost lands on the thread that pressed
 * Publish. The question this answers is not whether it is slow -- everyone
 * agrees it is -- but WHICH of the four things it does is slow, because the
 * answers call for opposite responses: a quadratic partition in the template is
 * a defect, and Flying Saucer laying out five hundred pages is the price of the
 * document.
 *
 * IT IS THE SHIPPED TEMPLATE, not a stand-in. The sectioning, the per-message
 * macros and the PDF pipeline are the ones a release runs; only the dictionary
 * is stood in for, because a label lookup costs the same whatever it answers,
 * and the member list is synthetic, because the corpus this repository's test
 * database carries has had its details stripped and would draw a five-hundred
 * page document as forty blank ones.
 *
 * THE IMAGE FETCH IS MEASURED AGAINST A LOOPBACK SERVER, and that is a FLOOR
 * rather than an estimate. Every map thumbnail in the document is an
 * {@code <img>} whose source is an application URL, so Flying Saucer fetches it
 * over HTTP, one at a time, on the render thread. The stand-in here answers
 * instantly over loopback with no TLS, no message lookup and no image
 * generation, where the real endpoint does all three and answers with a
 * redirect that costs a second round trip. So whatever this measures, a
 * deployment pays more.
 *
 * Not part of the normal suite: it renders a five-hundred page PDF several times
 * and takes minutes. Run it deliberately:
 *
 * <pre>
 *     mvn -pl niord-web test -Dtest=AccumulatedReportRenderTimingTest -Dniord.render.timing=true
 * </pre>
 */
@EnabledIfSystemProperty(named = "niord.render.timing", matches = "true",
        disabledReason = "a timing harness, not an assertion; see the class comment for how to run it")
public class AccumulatedReportRenderTimingTest {

    private static final String TEMPLATE = "nm-accumulated-report-pdf.ftl";

    /** A year of weeklies, at the size the largest real annual reached. */
    private static final int MESSAGES = 1272;
    private static final int GROUPS = 52;

    /**
     * The body of one notice, at the length that draws the real document's page
     * count.
     *
     * The page count is the only thing about a synthetic member that has to be
     * right, because it is what the layout is charged for. This length puts 1272
     * members on roughly 490 pages, which is where the annual sits.
     */
    private static final int DETAIL_CHARS = 900;

    /** A 1x1 PNG, which is all a fetch measurement needs the bytes to be. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    // --------------------------------------------------------------- fixture

    /**
     * Every label answers with its own key: a lookup costs the same either way.
     *
     * Except the date patterns, which the template hands straight to the
     * formatter, so a key in their place is not a label that reads oddly but a
     * render that fails.
     */
    private static class KeyText implements TemplateMethodModelEx {
        @Override
        public Object exec(List arguments) {
            if (arguments.isEmpty()) {
                return "";
            }
            String key = String.valueOf(arguments.get(0));
            return key.startsWith("msg.time.") ? "dd-MM-yyyy" : key;
        }
    }

    private static String body(int seed) {
        StringBuilder out = new StringBuilder(DETAIL_CHARS + 64);
        out.append("<p>Position 55&deg; 4").append(seed % 10)
                .append("',7N 010&deg; 3").append(seed % 10).append("',2E. ");
        while (out.length() < DETAIL_CHARS) {
            out.append("Arbejde med kabel er indstillet indtil videre, og afmaerkningen er inddraget. ");
        }
        return out.append("</p>").toString();
    }

    private static MessageVo message(int i) {
        MessageVo m = new MessageVo();
        m.setId("uid-" + i);
        m.setShortId("NM-" + (100 + i));
        m.setNumber(100 + i);
        m.setMainType(MainType.NM);
        m.setType(i % 11 == 0 ? Type.MISCELLANEOUS_NOTICE : Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        m.setPublishDateFrom(new Date(1_700_000_000_000L + i * 3_600_000L));

        AreaVo parent = area(1000 + (i % 7), "Danmark " + (i % 7), null);
        m.checkCreateAreas().add(area(2000 + (i % 23), "Farvand " + (i % 23), parent));

        for (int c = 0; c < 2; c++) {
            ChartVo chart = new ChartVo();
            chart.setChartNumber(String.valueOf(100 + (i + c) % 60));
            chart.setInternationalNumber(1300 + (i + c) % 60);
            m.checkCreateCharts().add(chart);
        }

        ReferenceVo ref = new ReferenceVo();
        ref.setMessageId("NM-" + (50 + i % 40));
        ref.setType(ReferenceType.REFERENCE);
        m.checkCreateReferences().add(ref);

        MessageDescVo desc = new MessageDescVo();
        desc.setLang("da");
        desc.setTitle("Farvand " + (i % 23) + ". Kabelarbejde. Afmaerkning inddraget. " + i);
        desc.setSource("Soefartsstyrelsen");
        m.checkCreateDescs().add(desc);

        MessagePartVo part = new MessagePartVo();
        part.setType(MessagePartType.DETAILS);
        MessagePartDescVo partDesc = new MessagePartDescVo();
        partDesc.setLang("da");
        partDesc.setSubject("Kabelarbejde " + i);
        partDesc.setDetails(body(i));
        part.checkCreateDescs().add(partDesc);
        m.checkCreateParts().add(part);

        return m;
    }

    private static AreaVo area(int id, String name, AreaVo parent) {
        AreaVo area = new AreaVo();
        area.setId(id);
        area.setParent(parent);
        AreaDescVo desc = new AreaDescVo();
        desc.setLang("da");
        desc.setName(name);
        area.checkCreateDescs().add(desc);
        return area;
    }

    /**
     * The model a release hands the template.
     *
     * {@code sections} of zero is the SAME document drawn without sections -- one
     * flat list, which is what every regime but the compiled one produces and
     * what a preview of an unsourced issue produces. It renders exactly the same
     * members, so the difference between the two is what the sectioning costs and
     * nothing else.
     */
    private static Map<String, Object> annual(boolean mapThumbnails, boolean preGrouped,
                                              int members, int sections) {
        List<MessageVo> messages = new ArrayList<>(members);
        for (int i = 0; i < members; i++) {
            messages.add(message(i));
        }

        List<Map<String, Object>> groups = sections == 0 ? null : new ArrayList<>(sections);
        if (groups != null) {
            int per = members / sections;
            for (int g = 0; g < sections; g++) {
                int from = g * per;
                int to = g == sections - 1 ? members : from + per;
                List<String> ids = new ArrayList<>();
                List<MessageVo> inSection = new ArrayList<>();
                for (int i = from; i < to; i++) {
                    ids.add("uid-" + i);
                    inSection.add(messages.get(i));
                }
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("publicId", "week-" + (g + 1));
                group.put("name", "EfS " + (g + 1) + "/2025");
                group.put("week", g + 1);
                group.put("weekTo", null);
                group.put("year", 2025);
                group.put("cutoff", new Date(1_700_000_000_000L + g * 604_800_000L));
                group.put("messageIds", ids);
                group.put("manual", false);
                if (preGrouped) {
                    group.put("messages", inSection);
                }
                groups.add(group);
            }
        }

        Set<String> separatePage = new LinkedHashSet<>();
        for (int i = 0; i < members; i += 97) {
            separatePage.add("uid-" + i);
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("messages", messages);
        if (groups != null) {
            model.put("groups", groups);
        }
        model.put("areaHeadings", true);
        model.put("separatePageIds", separatePage);
        model.put("mapThumbnails", mapThumbnails);
        model.put("language", "da");
        model.put("languages", new String[] { "da" });
        model.put("draft", false);
        model.put("link", false);
        model.put("frontPage", true);
        model.put("pageSize", "A4");
        model.put("pageOrientation", "portrait");
        model.put("year", "2025");
        model.put("yearNumber", 2025);
        model.put("edition", "1");
        model.put("ISSN", "1397-999X");
        model.put("timeZone", "Europe/Copenhagen");
        model.put("baseUri", "");
        model.put("executionMode", "test");
        model.put("country", "DK");
        return model;
    }

    // ----------------------------------------------------------- the phases

    private String toHtml(Map<String, Object> model) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setTemplateLoader(new ClassTemplateLoader(getClass().getClassLoader(), "templates/messages"));
        cfg.setObjectWrapper(new NiordAppObjectWrapper(cfg.getIncompatibleImprovements()));
        cfg.setLocalizedLookup(false);

        model.put("text", new KeyText());

        Template template = cfg.getTemplate(TEMPLATE);
        java.io.StringWriter out = new java.io.StringWriter(1 << 22);
        template.process(model, out);
        return out.toString();
    }

    /** What the last toPdf spent fetching what the document points at. */
    private ResourceFetchLog fetches;

    private byte[] toPdf(String html, String baseUri) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 22);
        HtmlToPdfRenderer renderer = HtmlToPdfRenderer.newBuilder()
                .baseUri(baseUri)
                .html(html)
                .pdf(bytes)
                .build();
        renderer.render();
        fetches = renderer.getFetches();
        return bytes.toByteArray();
    }

    /**
     * A stand-in for the application's own image endpoints, on loopback.
     *
     * It answers every request with the same tiny PNG and counts the calls, so
     * what the delta measures is the per-image round trip a render pays and
     * nothing about what the real endpoint does to produce one.
     */
    private static HttpServer imageServer(AtomicInteger calls) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, PNG.length);
            exchange.getResponseBody().write(PNG);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static long millis(long from) {
        return (System.nanoTime() - from) / 1_000_000L;
    }

    private static void line(String what, long ms, Object detail) {
        System.out.println("[render-timing] " + what + ": " + ms + " ms" + (detail == null ? "" : " (" + detail + ")"));
    }

    // ------------------------------------------------------------- the run

    /**
     * Every phase, in one JVM, in one order, after a warm-up.
     *
     * One method rather than several, because these are numbers to be compared
     * with each other: run as separate tests they differ by how much of FreeMarker
     * and Flying Saucer the JIT had already compiled when each one started, and
     * the last one measured looks the fastest whatever it does.
     */
    @Test
    public void everyPhaseAtAnnualSize() throws Exception {
        // Warm-up, at a tenth of the size, so the numbers below are of the code
        // rather than of the compiler.
        toPdf(toHtml(annual(false, true, MESSAGES / 10, GROUPS / 10)), "");

        long t0 = System.nanoTime();
        String grouped = toHtml(annual(false, true, MESSAGES, GROUPS));
        long groupedMs = millis(t0);

        long t1 = System.nanoTime();
        String scanned = toHtml(annual(false, false, MESSAGES, GROUPS));
        long scannedMs = millis(t1);

        long t2 = System.nanoTime();
        String flat = toHtml(annual(false, false, MESSAGES, 0));
        long flatMs = millis(t2);

        long t3 = System.nanoTime();
        byte[] pdf = toPdf(grouped, "");
        long pdfMs = millis(t3);

        line("template -> html, " + GROUPS + " sections handed their members", groupedMs,
                grouped.length() / 1024 + " KB");
        line("template -> html, " + GROUPS + " sections named by id only", scannedMs,
                scanned.length() / 1024 + " KB");
        line("template -> html, one flat list", flatMs, flat.length() / 1024 + " KB");
        line("html -> pdf", pdfMs, pages(pdf) + " pages, " + pdf.length / 1024 + " KB");
        line("TOTAL one language", groupedMs + pdfMs, MESSAGES + " members");

        // The thumbnails, against a server that answers instantly: the floor on
        // what fetching them costs, before the real endpoint has done any work.
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = imageServer(calls);
        try {
            String withImages = toHtml(annual(true, true, MESSAGES, GROUPS));
            long t4 = System.nanoTime();
            byte[] illustrated = toPdf(withImages, "http://127.0.0.1:" + server.getAddress().getPort());
            long illustratedMs = millis(t4);
            line("html -> pdf, thumbnails on", illustratedMs,
                    calls.get() + " HTTP requests answered, " + fetches.getCount() + " fetches costing "
                            + fetches.getMillis() + " ms, " + pages(illustrated) + " pages");
        } finally {
            server.stop(0);
        }

        assertTrue(pages(pdf) > 0, "the timing run produced no document at all");
        // The whole of the section change is a performance change, which is only
        // true while the two ways of finding a section's members draw the same
        // document -- at this size rather than at the fixture size the always-on
        // suite can afford.
        assertEquals(scanned, grouped,
                "handing a section its members drew a different document than finding them by id");
    }

    private static int pages(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        int pages = reader.getNumberOfPages();
        reader.close();
        return pages;
    }

    /** The extracted text of a rendered document, for a before-and-after comparison. */
    static String textOf(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        StringBuilder out = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            out.append(PdfTextExtractor.getTextFromPage(reader, page)).append('\n');
        }
        reader.close();
        return out.toString();
    }

    /**
     * The document this harness renders, written out where it can be looked at.
     *
     * Off by default. A before-and-after comparison needs the two documents side
     * by side, and a PDF is not something to assert about in a log line.
     */
    @Test
    @EnabledIfSystemProperty(named = "niord.render.timing.write", matches = "true",
            disabledReason = "writes a PDF next to the build output; ask for it explicitly")
    public void writeTheDocument() throws Exception {
        byte[] pdf = toPdf(toHtml(annual(false, true, MESSAGES, GROUPS)), "");
        java.nio.file.Path target = java.nio.file.Path.of("target", "render-timing-annual.pdf");
        java.nio.file.Files.write(target, pdf);
        java.nio.file.Files.writeString(target.resolveSibling("render-timing-annual.txt"), textOf(pdf));
        line("written", 0, target.toAbsolutePath() + ", " + pages(pdf) + " pages");
    }
}
