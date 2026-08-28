/*
 * Copyright 2026 Danish Maritime Authority.
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

package org.niord.core.publication.series.legacy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.series.IntervalBoundSource;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;

import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An annual issue's cut-off is the boundary its public window names, and the
 * moment somebody released it is a different fact.
 *
 * "EfS A 2018" was edited on 4 January 2019; taken as the cut-off that placed a
 * list of what was in force on 1 January 2018 a year later, and the shadow diff
 * resolved it there. "Akkumuleret EfS 2003" is what was published during 2003
 * and was loaded in December 2016, thirteen years after its content period
 * closed. The row's last-write stamp is the publication moment where it is
 * credible, and never the cut-off.
 *
 * Driven from the captured estate, by legacy publicationId.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class AnnualCutoffTest {

    private static final String EFS_A_2018 = "0b040947-d602-4d05-bea9-7a46ca271afe";
    private static final String EFS_A_2024 = "39cda106-d6c3-48c8-84f6-5bfec9c4e410";
    private static final String EFS_A_2017_SECOND_EDITION = "35046715-ad41-4654-a1fe-f3a1f87cf724";
    private static final String FIRING_2022_EDITED_NEXT_YEAR = "76bee094-4959-4a0e-bae0-b55ae80a9e17";
    private static final String FIRING_2022_EDITED_IN_YEAR = "19546efb-8f21-42e1-a124-02bd1901b6e6";
    private static final String FIRING_2027_OPEN = "46c4ed07-17a7-4afc-87f9-c78d266c4805";
    private static final String ACCUMULATED_2003 = "0b97ec80-1b8c-4dca-aa80-1bb5d8c71a63";
    private static final String ACCUMULATED_2018_OPEN_ENDED = "2b27a139-f1dc-4a90-b416-c86364ba6456";

    @Inject
    LegacyImportService importService;

    private LegacyImportService.Plan plan;

    private PublicationIssue issue(String id) {
        if (plan == null) {
            plan = importService.planFrom(LegacyEstateFixture.templates(), LegacyEstateFixture.publications());
        }
        PublicationIssue issue = plan.issues().get(id);
        assertNotNull(issue, id + " is not in the plan");
        return issue;
    }

    /** An in-force annual is decided where its window OPENS, however late it was edited. */
    @Test
    public void anInForceAnnualIsCutOffWhereItsWindowOpens() {
        PublicationIssue efsA2018 = issue(EFS_A_2018);
        assertEquals(efsA2018.getPublicFrom(), efsA2018.getCutoffStampedAt(),
                "EfS A 2018 was in force from 1 January 2018; its 2019 edit is not its cut-off");
        assertEquals(CutoffRecovery.PUBLIC_WINDOW, efsA2018.getCutoffSource());
        assertEquals(efsA2018.getPublicFrom(), efsA2018.getIntervalTo());
        assertNull(efsA2018.getIntervalFrom(), "an in-force list has no lower bound");

        PublicationIssue efsA2024 = issue(EFS_A_2024);
        assertEquals(efsA2024.getPublicFrom(), efsA2024.getCutoffStampedAt());

        // A mid-year edition is decided at its seam, not on 1 January.
        PublicationIssue efsA2017b = issue(EFS_A_2017_SECOND_EDITION);
        assertEquals(new Date(1488889914000L), efsA2017b.getCutoffStampedAt(),
                "the second 2017 edition took effect on 7 March");
    }

    /** The publication moment is kept apart, and only where the stamp is credible. */
    @Test
    public void thePublicationMomentIsTheLastWriteOnlyWhenItFallsInsideTheWindow() {
        assertEquals(new Date(1514875432000L), issue(EFS_A_2018).getPublishedAt(),
                "edited on 2 January 2018, inside the 2018 window: that is when it went out");
        assertEquals(new Date(1704260530000L), issue(EFS_A_2024).getPublishedAt());

        assertEquals(new Date(1643809937000L), issue(FIRING_2022_EDITED_IN_YEAR).getPublishedAt(),
                "edited in February 2022, inside its own window");
        assertNull(issue(FIRING_2022_EDITED_NEXT_YEAR).getPublishedAt(),
                "edited in January 2023, after its window closed: not a release moment, so unknown");
        assertEquals(new Date(1641363763000L), issue(FIRING_2022_EDITED_NEXT_YEAR).getCutoffStampedAt(),
                "and the cut-off is still 5 January 2022, where the window opens");
    }

    /** An accumulated annual describes the year its window names, and is decided where it CLOSES. */
    @Test
    public void anAccumulatedAnnualCoversItsWindowAndIsCutOffWhereItCloses() {
        PublicationIssue acc2003 = issue(ACCUMULATED_2003);
        assertEquals(new Date(1041379200000L), acc2003.getIntervalFrom(), "1 January 2003");
        assertEquals(new Date(1072911540000L), acc2003.getIntervalTo(), "the legacy end, 31 December 2003");
        assertEquals(IntervalBoundSource.NOMINAL, acc2003.getIntervalFromSource());
        assertEquals(IntervalBoundSource.NOMINAL, acc2003.getIntervalToSource());
        assertEquals(acc2003.getIntervalTo(), acc2003.getCutoffStampedAt());
        assertEquals(CutoffRecovery.PUBLIC_WINDOW, acc2003.getCutoffSource());
        assertNull(acc2003.getPublishedAt(), "loaded in December 2016, thirteen years after: not a release moment");
    }

    /** Legacy left the 2018 accumulation open; it closes at the end of its own year, in the series' zone. */
    @Test
    public void anOpenEndedAccumulatedAnnualClosesAtTheEndOfItsYear() {
        PublicationIssue acc2018 = issue(ACCUMULATED_2018_OPEN_ENDED);
        assertNotNull(acc2018.getIntervalTo(), "an open legacy end date is closed at the end of the year");
        ZonedDateTime end = acc2018.getIntervalTo().toInstant().atZone(acc2018.getSeries().cutoffZone());
        assertEquals(2018, end.getYear());
        assertEquals(12, end.getMonthValue());
        assertEquals(31, end.getDayOfMonth());
        assertEquals(23, end.getHour());
        assertEquals(59, end.getMinute());
        assertEquals(IntervalBoundSource.RECOVERED, acc2018.getIntervalToSource(),
                "an invented year-end is flagged as reconstructed, unlike one the archive recorded");
        assertEquals(acc2018.getIntervalTo(), acc2018.getCutoffStampedAt());
        assertNull(acc2018.getPublishedAt(), "edited in 2021: not a release moment");
    }

    /** A never-released annual has no cut-off and no publication moment. */
    @Test
    public void anUnreleasedAnnualHasNeither() {
        PublicationIssue firing2027 = issue(FIRING_2027_OPEN);
        assertEquals(IssueStatus.OPEN, firing2027.getStatus());
        assertNull(firing2027.getCutoffStampedAt());
        assertEquals(CutoffRecovery.NOT_RELEASED, firing2027.getCutoffSource());
        assertNull(firing2027.getPublishedAt());
    }
}
