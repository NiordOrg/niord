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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.vo.PublicationSeriesDescVo;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S7 and S12: they exist, they are admin-only, and the copy clears exactly what
 * belongs to the row it was copied from.
 *
 * The endpoint half reads annotations rather than starting a server, the same
 * way the tier split is checked -- what these endpoints DO with a series lives in
 * the core tests, and what this pins is that they are there at all and cannot be
 * reached anonymously. Both return admin-shaped payloads: the draft carries the
 * criteria the series selects by, and the copy carries the whole system shape.
 */
public class SeriesDraftAndCopyContractTest {

    private static Method endpoint(String name) {
        for (Method m : PublicationSeriesRestService.class.getMethods()) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    // ==================================================================== S12

    /**
     * S12 is `GET /series/{seriesId}/issue-draft`, and it is a GET deliberately.
     *
     * It answers a question and writes nothing, so it is idempotent, cacheable by
     * the client's own rules, and bookmarkable from the gap row that asked it. A
     * POST would say the opposite about all three.
     */
    @Test
    public void theIssueDraftEndpointExistsAtTheContractedVerbAndPath() {
        Method draft = endpoint("issueDraft");

        assertNotNull(draft, "S12 GET /series/{seriesId}/issue-draft is missing. Three screens -- "
                + "the new-issue button, the retro-create prefill and the gap row's preview -- then "
                + "each derive the proposed interval themselves, and the day two of them disagree an "
                + "admin creates a week that overlaps the one before it");
        assertNotNull(draft.getAnnotation(GET.class), "S12 asks a question and writes nothing");
        assertEquals("/series/{seriesId}/issue-draft", draft.getAnnotation(Path.class).value());
    }

    /** It serves the criteria document, so it is admin-only like every system shape. */
    @Test
    public void theIssueDraftEndpointIsAdminGuarded() {
        Method draft = endpoint("issueDraft");
        assertNotNull(draft);

        assertFalse(draft.isAnnotationPresent(PermitAll.class),
                "the draft carries the series' criteria and its live member count");
        RolesAllowed roles = draft.getAnnotation(RolesAllowed.class);
        assertNotNull(roles, "an unguarded draft hands the criteria document to any caller");
        assertTrue(Arrays.asList(roles.value()).contains("admin"),
                "expected admin, got " + Arrays.toString(roles.value()));
    }

    /**
     * All three ways of naming the interval are reachable.
     *
     * Listed together because the failure that matters is an absent parameter
     * rather than a broken one: drop `afterPublicId` and the new-issue button has
     * to compute the chain itself, which is the duplication the endpoint exists
     * to remove.
     */
    @Test
    public void theDraftAcceptsAllThreeWaysOfNamingTheInterval() {
        Method draft = endpoint("issueDraft");
        assertNotNull(draft);

        List<String> params = new ArrayList<>();
        for (var p : draft.getParameters()) {
            var q = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
            if (q != null) {
                params.add(q.value());
            }
        }
        assertTrue(params.contains("afterPublicId"), "chaining off a named issue: " + params);
        assertTrue(params.contains("intervalFrom"), "an explicit interval start: " + params);
        assertTrue(params.contains("intervalTo"), "an explicit interval end: " + params);
    }

    // ===================================================================== S7

    @Test
    public void theCopyTemplateEndpointExistsAndIsAdminOnly() {
        Method copy = endpoint("copySeriesTemplate");

        assertNotNull(copy, "S7 GET /copy-series-template/{seriesId} is missing; an admin building a "
                + "second series like an existing one has to retype the criteria document and every "
                + "per-language pattern");
        assertNotNull(copy.getAnnotation(GET.class));
        assertEquals("/copy-series-template/{seriesId}", copy.getAnnotation(Path.class).value());
        assertFalse(copy.isAnnotationPresent(PermitAll.class));
        assertNotNull(copy.getAnnotation(RolesAllowed.class));
    }

    /**
     * The four identity fields are cleared, and the fifth is the interesting one.
     *
     * Each of these causes a distinct quiet failure if it travels: a taken
     * seriesId, an ambiguous legacy mapping, a hand-made series that an import
     * undo would delete, and a copy that already answers for the public.
     * `firstIssueStartsAt` is cleared for a different reason -- it is a fact about
     * when the ORIGINAL began, and a 2017 date on a series created today would
     * fill its first screen with nine years of missing weeks.
     */
    @Test
    public void theCopyClearsEverythingThatBelongedToTheOriginalRow() {
        SystemPublicationSeriesVo copy = PublicationSeriesRestService.copyOf(populated());

        assertNull(copy.getSeriesId(), "the id is unique and is the citation handle; the admin names "
                + "the new series");
        assertEquals("DRAFT", copy.getStatus(), "ACTIVE is what puts a series in the picker, and "
                + "activation validates");
        assertNull(copy.getLegacyTemplateId(), "a second row claiming one legacy publication makes "
                + "the import's already-imported check ambiguous");
        assertNull(copy.getImportSource(), "a hand-made series carrying it would be deleted by an "
                + "import undo");
        assertEquals("LEGACY", copy.getPublicAuthority(), "a copy must not arrive already answering "
                + "for a publication that has never published anything");
        assertNull(copy.getFirstIssueStartsAt(), "when the ORIGINAL began is not a setting");
        assertNull(copy.getCreated(), "the timestamps date the row it was copied from");
        assertNull(copy.getUpdated());
        assertEquals(Integer.valueOf(0), copy.getPublishedIssueCount(),
                "zero rather than absent: the citation channel must be editable on a series that has "
                        + "released nothing");
    }

    /**
     * And it carries the settings that took somebody an afternoon.
     *
     * This is the half that makes the endpoint worth having. A copy that cleared
     * the criteria document, the patterns or the report configuration would be a
     * blank create form with a longer route to it.
     */
    @Test
    public void theCopyCarriesTheConfiguration() {
        SystemPublicationSeriesVo copy = PublicationSeriesRestService.copyOf(populated());

        assertNotNull(copy.getCriteria(), "the criteria document is the point of copying a series");
        assertEquals("WEEKLY", copy.getCadence());
        assertEquals("PUBLISHED_IN_INTERVAL", copy.getTimeRelation());
        assertEquals("ISO_WEEK_YEAR", copy.getNumberingScheme());
        assertEquals("RELEASE_MOMENT", copy.getCutoffDefault());
        assertEquals("fm-report", copy.getReportId());
        assertEquals("1397-6656", copy.getReportParams().get("issn"));
        assertEquals(List.of("da", "en"), copy.getLanguages());
        assertEquals("cat-nm", copy.getCategoryId());

        // THE SHARING SETTINGS TRAVEL AND THE OWNER DOES NOT, and both halves are
        // deliberate. "Another annex, shared with the same desks" is a reason to
        // copy a publication; carrying the OWNER across is not -- it is a claim
        // about the original row, and copying somebody else's publication would
        // author a new one straight into their domain, which the write guard then
        // refuses on save naming a domain the admin never chose. The form seeds it
        // from the session domain instead.
        assertNull(copy.getDomainId(),
                "the copy carried the original's owner; the save would then be refused for a "
                        + "domain the admin never chose");
        assertEquals("SELECTED_DOMAINS", copy.getAvailability());
        assertEquals(List.of("dma-fa"), copy.getAvailableDomainIds());

        assertEquals(2, copy.getDescs().size(), "one row per configured language, as C5 requires");
        PublicationSeriesDescVo da = copy.getDescs().get(0);
        assertEquals("da", da.getLang());
        assertEquals("EfS uge ${week}, ${year}", da.getNameSuggestionPattern());
        assertEquals("efs-w${week-2-digits}-${year}.pdf", da.getFileNamePattern());
    }

    /** A fully configured series, as an admin would have left it. */
    private static SystemPublicationSeriesVo populated() {
        SystemPublicationSeriesVo vo = new SystemPublicationSeriesVo();
        vo.setSeriesId("weekly-ntm");
        vo.setStatus("ACTIVE");
        vo.setLegacyTemplateId("11111111-2222-3333-4444-555555555555");
        vo.setImportSource("publications.json");
        vo.setPublicAuthority("NEW");
        vo.setFirstIssueStartsAt(new Date(1_500_000_000_000L));
        vo.setCreated(new Date(1_500_000_000_000L));
        vo.setUpdated(new Date(1_700_000_000_000L));
        vo.setPublishedIssueCount(452);

        vo.setContentMode("GENERATED_FROM_QUERY");
        vo.setCadence("WEEKLY");
        vo.setTimeRelation("PUBLISHED_IN_INTERVAL");
        vo.setNumberingScheme("ISO_WEEK_YEAR");
        vo.setCutoffDefault("RELEASE_MOMENT");
        vo.setReportId("fm-report");
        vo.getReportParams().put("issn", "1397-6656");
        vo.setCategoryId("cat-nm");
        vo.setDomainId("dma-nm");
        vo.setAvailability("SELECTED_DOMAINS");
        vo.getAvailableDomainIds().add("dma-fa");
        vo.getLanguages().addAll(List.of("da", "en"));

        IssueCriteriaVo criteria = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        criteria.getCriteria().add(node);
        vo.setCriteria(criteria);

        PublicationSeriesDescVo da = new PublicationSeriesDescVo();
        da.setLang("da");
        da.setName("Efterretninger for Søfarende");
        da.setNameSuggestionPattern("EfS uge ${week}, ${year}");
        da.setFileNamePattern("efs-w${week-2-digits}-${year}.pdf");
        vo.getDescs().add(da);

        PublicationSeriesDescVo en = new PublicationSeriesDescVo();
        en.setLang("en");
        en.setName("Notices to Mariners");
        en.setNameSuggestionPattern("NtM week ${week}, ${year}");
        en.setFileNamePattern("ntm-w${week-2-digits}-${year}.pdf");
        vo.getDescs().add(en);
        return vo;
    }
}
