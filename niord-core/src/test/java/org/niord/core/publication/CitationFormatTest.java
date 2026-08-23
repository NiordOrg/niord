package org.niord.core.publication;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.IssueLifecycleService;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.model.message.MessageDescVo;
import org.niord.model.message.MessageVo;
import org.niord.model.publication.PublicationDescVo;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Citing a publication into a language that has no citation format.
 *
 * The relation between a message and a publication exists ONLY as
 * {@code publication="<id>"} inside the message's stored HTML. There is no join
 * table and no endpoint that removes a citation, so whatever gets written here
 * is what the public site renders, permanently.
 *
 * What used to get written, when the publication carried no format in that
 * language, was the four characters " null" -- appended to the message's
 * publication field, rendered to the site, and invisible to the editor, who
 * typed nothing wrong.
 *
 * No database and no server.
 */
public class CitationFormatTest {

    private static final String PUBLICATION_ID = "5eab7f50-d890-42d9-8f0a-d30e078d3d5a";

    /**
     * A language with no format is skipped, and the word "null" is never written.
     *
     * Skipped rather than refused: lang == null means every language, and a
     * publication may legitimately carry a format in one language and not
     * another. Refusing the whole update would block a correct Danish citation
     * because English has no format.
     */
    @Test
    public void aLanguageWithNoFormatIsSkippedRatherThanFilledWithNull() {
        MessageVo message = message("da", "en");
        SystemPublicationVo publication = publicationWithFormatIn("da");

        PublicationUtils.updateMessagePublications(message, publication, null, null, null);

        MessageDescVo da = desc(message, "da");
        assertNotNull(da.getPublication(), "the Danish citation was not written");
        assertTrue(da.getPublication().contains("publication=\"" + PUBLICATION_ID + "\""),
                "the Danish citation lost the publication attribute, which is the ONLY thing that "
                        + "links a message to a publication");

        MessageDescVo en = desc(message, "en");
        assertNull(en.getPublication(),
                "English has no citation format, so nothing should have been written. What used to "
                        + "be written was the literal string \" null\", rendered to the public site.");
    }

    /** And it is never written when the field already had content either. */
    @Test
    public void anExistingCitationIsNotFollowedByTheWordNull() {
        MessageVo message = message("da", "en");
        desc(message, "en").setPublication("<span>Some earlier text</span>");

        PublicationUtils.updateMessagePublications(message, publicationWithFormatIn("da"), null, null, null);

        assertFalse(desc(message, "en").getPublication().contains("null"),
                "the word null was appended after the existing content");
        assertEquals("<span>Some earlier text</span>", desc(message, "en").getPublication(),
                "the English field was modified even though there was nothing to write");
    }

    /** With a format in both languages, both are written. The skip is narrow. */
    @Test
    public void bothLanguagesAreWrittenWhenBothHaveAFormat() {
        MessageVo message = message("da", "en");
        SystemPublicationVo publication = publicationWithFormatIn("da", "en");

        PublicationUtils.updateMessagePublications(message, publication, null, null, null);

        for (String lang : List.of("da", "en")) {
            assertNotNull(desc(message, lang).getPublication(), lang + " was not written");
            assertFalse(desc(message, lang).getPublication().contains("null"),
                    lang + " contains the word null");
        }
    }

    /** An internal publication writes to the internal field, and to that one only. */
    @Test
    public void anInternalPublicationWritesToTheInternalField() {
        MessageVo message = message("da");
        SystemPublicationVo publication = publicationWithFormatIn("da");
        publication.setMessagePublication(MessagePublication.INTERNAL);

        PublicationUtils.updateMessagePublications(message, publication, null, null, null);

        assertNotNull(desc(message, "da").getInternalPublication());
        assertNull(desc(message, "da").getPublication(),
                "an INTERNAL publication reached the public field; which field a citation lands in is "
                        + "exactly what messagePublication decides, and it is why that value cannot "
                        + "change once citations exist");
    }

    /**
     * Citing into a NAMED language that has no format is refused.
     *
     * Different from the all-languages case above, and deliberately so. "Cite it
     * into English" is a specific instruction; silently doing nothing would leave
     * the editor looking at an empty field having been told nothing. The refusal
     * names the language, so it is actionable.
     */
    @Test
    public void citingIntoANamedLanguageWithNoFormatIsRefused() {
        MessageVo message = message("da", "en");
        SystemPublicationVo publication = publicationWithFormatIn("da");

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> PublicationUtils.updateMessagePublications(
                                message, publication, null, null, "en"),
                        "citing into a language with no format was accepted; legacy wrote the "
                                + "literal \" null\" there");

        assertEquals("CITATION_FORMAT_MISSING", e.code());
        assertNull(desc(message, "en").getPublication(),
                "the field was written to before the refusal");
    }

    /** And citing into a named language that HAS a format still works. */
    @Test
    public void citingIntoANamedLanguageThatHasAFormatWorks() {
        MessageVo message = message("da", "en");

        PublicationUtils.updateMessagePublications(
                message, publicationWithFormatIn("da"), null, null, "da");

        assertNotNull(desc(message, "da").getPublication());
        assertNull(desc(message, "en").getPublication(),
                "a single-language citation must not touch the other language");
    }

    // ------------------------------------------------------------------ helpers

    private static MessageVo message(String... langs) {
        MessageVo message = new MessageVo();
        message.setDescs(new ArrayList<>());
        for (String lang : langs) {
            message.createDesc(lang);
        }
        return message;
    }

    private static MessageDescVo desc(MessageVo message, String lang) {
        return message.getDescs().stream()
                .filter(d -> lang.equals(d.getLang()))
                .findFirst()
                .orElseThrow();
    }

    private static SystemPublicationVo publicationWithFormatIn(String... langs) {
        SystemPublicationVo publication = new SystemPublicationVo();
        publication.setPublicationId(PUBLICATION_ID);
        publication.setMessagePublication(MessagePublication.EXTERNAL);
        publication.setDescs(new ArrayList<>());

        // Both languages always get a desc row; only the named ones get a format.
        for (String lang : List.of("da", "en")) {
            PublicationDescVo desc = new PublicationDescVo();
            desc.setLang(lang);
            desc.setTitle("Title " + lang);
            if (List.of(langs).contains(lang)) {
                desc.setMessagePublicationFormat("EfS 33/2017 ${parameters}");
                desc.setLink("rest/repo/file/publications/efs-33-2017.pdf");
            }
            publication.getDescs().add(desc);
        }
        return publication;
    }
}
