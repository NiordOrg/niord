package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.model.publication.PublicationDescVo;
import org.niord.model.publication.PublicationType;
import org.niord.model.publication.PublicationVo;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An issue wearing the shape the rest of the system already speaks.
 *
 * Nothing downstream knows what an issue is. The public list, the citation
 * resolver and the two legacy citation endpoints all consume PublicationVo and
 * SystemPublicationVo, so this mapping is the whole of what makes a cut-over
 * series work in places nobody is going to rewrite.
 *
 * No database and no server: these are plain entities and a pure mapper.
 */
public class IssuePublicationMappingTest {

    // ============================================ the window, not the interval

    /**
     * publishDateFrom is the PUBLIC window, never the content interval.
     *
     * The one-period offset: an issue covering week 33 becomes publicly current
     * when week 33 CLOSES. Mapping the interval here -- the only mapping the
     * field names invite -- gives every issue the previous period's window, and
     * under the from = to = now default the newest issue is never current.
     */
    @Test
    public void theWindowIsMappedAndNotTheInterval() {
        PublicationIssue issue = issue();

        PublicationVo vo = IssuePublicationMapping.toPublicationVo(issue, "da");

        assertEquals(issue.getPublicFrom(), vo.getPublishDateFrom(),
                "publishDateFrom must be the stamped cut-off");
        assertEquals(issue.getPublicTo(), vo.getPublishDateTo());
        assertFalse(issue.getIntervalFrom().equals(vo.getPublishDateFrom()),
                "publishDateFrom equals intervalFrom, which is exactly the wrong mapping");
    }

    /** The id is the issue publicId, because that is what citations name. */
    @Test
    public void theIdIsThePublicId() {
        PublicationIssue issue = issue();
        assertEquals(issue.getPublicId(),
                IssuePublicationMapping.toPublicationVo(issue, "da").getPublicationId());
    }

    /** The language filter narrows the descriptions; no language keeps them all. */
    @Test
    public void theLanguageFilterSelectsOneDescription() {
        PublicationIssue issue = issue();

        assertEquals(1, IssuePublicationMapping.toPublicationVo(issue, "da").getDescs().size());
        assertEquals("da", IssuePublicationMapping.toPublicationVo(issue, "da")
                .getDescs().get(0).getLang());
        assertEquals(2, IssuePublicationMapping.toPublicationVo(issue, null).getDescs().size());
    }

    /** The type is derived from what the issue actually has, not stored. */
    @Test
    public void theTypeFollowsWhatTheIssueHas() {
        PublicationIssue issue = issue();
        assertEquals(PublicationType.NONE, IssuePublicationMapping.toPublicationVo(issue, "da").getType(),
                "an issue with no file and no link is NONE");

        desc(issue, "da").setFilePath("publications/efs/efs-33-2017.pdf");
        assertEquals(PublicationType.REPOSITORY,
                IssuePublicationMapping.toPublicationVo(issue, "da").getType());

        desc(issue, "da").setLink("https://example.org/efs-33-2017.pdf");
        assertEquals(PublicationType.LINK,
                IssuePublicationMapping.toPublicationVo(issue, "da").getType());
    }

    // ================================================ the citation format (P6)

    /** The ISSUE format wins: an issue given its own wording keeps it. */
    @Test
    public void theIssueFormatWinsOverTheSeriesPattern() {
        PublicationIssue issue = issue();
        issue.getSeries().createDesc("da").setMessageReferenceFormat("EfS ${week}/${year}");
        desc(issue, "da").setMessageReferenceFormat("Efterretninger, saerudgave");

        assertEquals("Efterretninger, saerudgave", IssuePublicationMapping.citationFormat(issue, "da"));
    }

    /**
     * Otherwise the SERIES pattern, with this issue's numbers substituted.
     *
     * This is what stops anybody typing "EfS 33/2017" per issue, and it is why
     * the numbers have to come from the issue rather than from the clock.
     */
    @Test
    public void theSeriesPatternIsExpandedWithTheIssueNumbers() {
        PublicationIssue issue = issue();
        issue.getSeries().createDesc("da").setMessageReferenceFormat("EfS ${week}/${year}");

        assertEquals("EfS 33/2017", IssuePublicationMapping.citationFormat(issue, "da"));
    }

    /**
     * ${parameters} survives expansion.
     *
     * It belongs to the moment of citing, not to the issue: the editor types it
     * and the citation layer substitutes it. Expanding it here would blank it,
     * and treating it as an unknown token would refuse every citation format the
     * legacy convention produces.
     */
    @Test
    public void theParametersTokenSurvives() {
        PublicationIssue issue = issue();
        issue.getSeries().createDesc("da").setMessageReferenceFormat("EfS ${week}/${year} ${parameters}");

        assertEquals("EfS 33/2017 ${parameters}", IssuePublicationMapping.citationFormat(issue, "da"));
    }

    /** The stored numbers are used, not numbers re-derived from the cut-off. */
    @Test
    public void theStoredNumbersAreUsed() {
        PublicationIssue issue = issue();
        issue.getSeries().createDesc("da").setMessageReferenceFormat("${week}/${year}");

        // Deliberately inconsistent with the cut-off instant. A published issue's
        // numbers were fixed when it was published, and re-deriving them would let
        // a later change to the series timezone renumber a citation already in
        // print.
        issue.setWeek(2);
        issue.setYear(2019);

        assertEquals("2/2019", IssuePublicationMapping.citationFormat(issue, "da"));
    }

    /** No format anywhere is null, not an exception and not the word "null". */
    @Test
    public void noFormatAnywhereIsNull() {
        PublicationIssue issue = issue();
        assertNull(IssuePublicationMapping.citationFormat(issue, "da"));

        PublicationVo vo = IssuePublicationMapping.toPublicationVo(issue, "da");
        assertNull(vo.getDescs().get(0).getMessagePublicationFormat(),
                "an absent format must be absent; the legacy path wrote the four characters "
                        + "\" null\" into the message instead");
    }

    /** A pattern with an unknown token is refused rather than half-expanded. */
    @Test
    public void anUnexpandablePatternYieldsNoFormat() {
        PublicationIssue issue = issue();
        issue.getSeries().createDesc("da").setMessageReferenceFormat("EfS ${notAToken}");

        assertNull(IssuePublicationMapping.citationFormat(issue, "da"),
                "a half-expanded citation would be stored in a message and rendered; production "
                        + "already serves a PDF at Skydeomraader-%24%7Byear%7D.pdf for this reason");
    }

    // ================================================== the system shape

    /**
     * The system shape carries the three fields the citation machinery reads.
     *
     * publicationId, messagePublication -- which decides whether the citation
     * lands in the public or the internal field -- and the per-language link and
     * format. With those four, extract-message-publication and
     * update-message-publications work against an issue unchanged.
     */
    @Test
    public void theSystemShapeCarriesWhatTheCitationMachineryReads() {
        PublicationIssue issue = issue();
        issue.getSeries().setMessagePublication(MessagePublication.INTERNAL);
        issue.getSeries().createDesc("da").setMessageReferenceFormat("EfS ${week}/${year}");
        desc(issue, "da").setLink("https://example.org/efs-33-2017.pdf");

        SystemPublicationVo vo = IssuePublicationMapping.toSystemPublicationVo(issue, "da");

        assertNotNull(vo);
        assertEquals(issue.getPublicId(), vo.getPublicationId());
        assertEquals(MessagePublication.INTERNAL, vo.getMessagePublication());

        PublicationDescVo d = vo.getDescs().get(0);
        assertEquals("https://example.org/efs-33-2017.pdf", d.getLink());
        assertEquals("EfS 33/2017", d.getMessagePublicationFormat());
    }

    /** A PUBLISHED issue reads as ACTIVE; anything else reads as DRAFT. */
    @Test
    public void theStatusMapsOntoTheLegacyVocabulary() {
        PublicationIssue issue = issue();

        issue.setStatus(IssueStatus.PUBLISHED);
        assertEquals(PublicationStatus.ACTIVE,
                IssuePublicationMapping.toSystemPublicationVo(issue, "da").getStatus());

        issue.setStatus(IssueStatus.OPEN);
        assertEquals(PublicationStatus.DRAFT,
                IssuePublicationMapping.toSystemPublicationVo(issue, "da").getStatus());
    }

    /** The public shape still cannot carry the operational fields. */
    @Test
    public void thePublicShapeStaysPublic() {
        PublicationIssue issue = issue();
        PublicationVo vo = IssuePublicationMapping.toPublicationVo(issue, "da");

        assertTrue(vo.getClass() == PublicationVo.class,
                "the public list must emit the public type, not a system subclass that happens to "
                        + "serialize the extra fields too");
    }

    /** Null in, null out. The resolver relies on it for the not-found branch. */
    @Test
    public void aNullIssueMapsToNull() {
        assertNull(IssuePublicationMapping.toPublicationVo(null, "da"));
        assertNull(IssuePublicationMapping.toSystemPublicationVo(null, "da"));
        assertNull(IssuePublicationMapping.citationFormat(null, "da"));
    }

    // ------------------------------------------------------------------ fixtures

    /** Week 33 of 2017, published when that week closed. */
    private static PublicationIssue issue() {
        PublicationCategory category = new PublicationCategory();
        category.setCategoryId("efs");
        category.setPriority(10);
        category.setPublish(true);

        PublicationSeries series = new PublicationSeries();
        series.setSeriesId("dma-efs");
        series.setCategory(category);
        series.setMessagePublication(MessagePublication.EXTERNAL);
        series.setNominalCutoffTimeZone("UTC");

        PublicationIssue issue = new PublicationIssue();
        issue.setSeries(series);
        issue.setPublicId("5eab7f50-d890-42d9-8f0a-d30e078d3d5a");
        issue.setStatus(IssueStatus.PUBLISHED);
        issue.setIntervalFrom(new Date(1_502_150_400_000L));   // 2017-08-08
        issue.setIntervalTo(new Date(1_502_755_199_999L));
        issue.setCutoffStampedAt(new Date(1_502_755_200_000L)); // 2017-08-15, week 33
        issue.setPublicFrom(new Date(1_502_755_200_000L));
        issue.setPublicTo(new Date(1_503_359_999_999L));
        issue.setWeek(33);
        issue.setYear(2017);

        issue.createDesc("da").setName("Efterretninger for Soefarende 33/2017");
        issue.createDesc("en").setName("Notices to Mariners 33/2017");
        return issue;
    }

    private static PublicationIssueDesc desc(PublicationIssue issue, String lang) {
        return issue.getDescs().stream()
                .filter(d -> lang.equals(d.getLang()))
                .findFirst().orElseThrow();
    }
}
