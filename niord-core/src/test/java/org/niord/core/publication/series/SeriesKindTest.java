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

package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A one-off holds exactly one issue, and the kind is a stored fact.
 *
 * The rule that produced the kind -- "cadence = NONE and at most one issue" --
 * is spent once, during import, on an archive whose shape is known. What these
 * assert is the consequence of storing the answer rather than recomputing it:
 * the second issue is REFUSED, instead of the publication quietly becoming a
 * series because somebody uploaded another PDF.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class SeriesKindTest {

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    EntityManager em;

    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    private PublicationSeries series(SeriesKind kind, SeriesCadence cadence) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.UPLOADED_FILE);
        s.setCadence(cadence);
        s.setKind(kind);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.NONE);
        s.setCategory(c);
        // Every publication names the desk that owns it: the column is NOT NULL and
        // S-20a refuses a save without one, so a fixture that left it out no longer
        // describes a state the system can be in.
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");
        s.createDesc("da").setName("Test publication");
        em.persist(s);
        return s;
    }

    /** The first issue of a one-off is ordinary. Nothing here forbids publishing one. */
    @Test
    @Transactional
    public void aoneOffAcceptsItsFirstIssue() {
        PublicationSeries s = series(SeriesKind.ONE_OFF, SeriesCadence.NONE);

        PublicationIssue first = lifecycle.create(s, new Date(), IntervalBoundSource.MANUAL, user());
        em.flush();

        assertNotNull(first.getId(), "a one-off could not be given its one issue");
    }

    /**
     * The second is refused, and the refusal names the way out.
     *
     * Without this the kind would decay into a description of the current row
     * count: upload a second PDF and the publication silently stops being a
     * one-off. Whether something keeps appearing is a judgement about the
     * publication, so it is made deliberately or not at all.
     */
    @Test
    @Transactional
    public void aoneOffRefusesASecondIssue() {
        PublicationSeries s = series(SeriesKind.ONE_OFF, SeriesCadence.NONE);
        lifecycle.create(s, new Date(), IntervalBoundSource.MANUAL, user());
        em.flush();

        IssueLifecycleService.TransitionRefusedException e = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> lifecycle.create(s, new Date(), IntervalBoundSource.MANUAL, user()),
                "a one-off accepted a second issue, so its kind is only a row count");

        assertEquals("SERIES_IS_ONE_OFF", e.code());
        assertTrue(e.getMessage().contains("UNSCHEDULED"),
                "the refusal should name the reclassification that makes it succeed, or the "
                        + "admin is told no without being told what to do: " + e.getMessage());
    }

    /**
     * An UNSCHEDULED series takes as many as it likes.
     *
     * This is the case the old cadence = NONE reading got wrong: eleven NCAGS
     * editions and eight ice-service notices have no cadence and are plainly a
     * series, and a guard keyed on cadence rather than kind would have refused
     * their second edition.
     */
    @Test
    @Transactional
    public void anUnscheduledSeriesTakesMany() {
        PublicationSeries s = series(SeriesKind.UNSCHEDULED, SeriesCadence.NONE);

        lifecycle.create(s, new Date(), IntervalBoundSource.MANUAL, user());
        em.flush();
        PublicationIssue second = lifecycle.create(s, new Date(), IntervalBoundSource.MANUAL, user());
        em.flush();

        assertNotNull(second.getId(),
                "an unscheduled series was refused a second edition; the guard is keyed on "
                        + "cadence rather than kind");
    }

    /** A series arrives SCHEDULED unless something says otherwise -- the column is NOT NULL. */
    @Test
    @Transactional
    public void anewSeriesDefaultsToScheduled() {
        PublicationSeries s = new PublicationSeries();

        assertEquals(SeriesKind.SCHEDULED, s.getKind(),
                "the default must be a real value; the column is NOT NULL and the create form "
                        + "does not send one");
    }
}
