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

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.IssueCriterionVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.criteria.MessageTypeCriterionVo;
import org.niord.core.publication.series.resolve.MessageFacts;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether one open issue would contain one message.
 *
 * No database and no Quarkus: the decision takes an issue, a message's facts and
 * a curator override, and every one of those is constructible here. That matters
 * because this is the sentence the message editor puts on screen -- "indgar i EfS
 * uge 29 (aben)" -- and a wrong one tells an editor not to amend a message that
 * is in fact about to go out unamended.
 *
 * What is NOT re-tested here is the membership rule itself. MembershipPredicate
 * owns that and has the captured corpus behind it; duplicating a thinner version
 * of those assertions would create a second place for the rule to be written
 * down, which is how the two drift apart. These tests pin the layer above it:
 * override precedence, and which series shapes are asked at all.
 */
public class MessageIssueLookupTest {

    private static final long HOUR = 3600_000L;

    // ------------------------------------------------------------- builders

    private static IssueCriteriaVo criteria(IssueCriterionVo... nodes) {
        IssueCriteriaVo d = new IssueCriteriaVo();
        d.setCriteria(new ArrayList<>(List.of(nodes)));
        return d;
    }

    private static MessageSeriesCriterionVo messageSeries(String... ids) {
        MessageSeriesCriterionVo n = new MessageSeriesCriterionVo();
        n.setValues(new ArrayList<>(List.of(ids)));
        return n;
    }

    private static MessageTypeCriterionVo types(String... names) {
        MessageTypeCriterionVo n = new MessageTypeCriterionVo();
        n.setValues(new ArrayList<>(List.of(names)));
        return n;
    }

    /** A live, query-backed weekly series selecting one message series. */
    private static PublicationSeries queryBackedSeries() {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("dma-efs");
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCriteria(criteria(messageSeries("dma-nm")));
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(Boolean.FALSE);
        return s;
    }

    /** An annex series: members are named by hand, no query selects them. */
    private static PublicationSeries curatedSeries() {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("ncags-annex");
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.NONE);
        return s;
    }

    private static PublicationIssue openIssue(PublicationSeries series, Date intervalFrom) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(series);
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(intervalFrom);
        return i;
    }

    private static MessageFacts published(Date at) {
        return new MessageFacts("uid-1", at, null, Status.PUBLISHED, Type.TEMPORARY_NOTICE, "dma-nm");
    }

    // ------------------------------------------------------- the query branch

    /**
     * The baseline: a message the issue's query selects, inside its window.
     *
     * Everything below is a departure from this case, so it has to hold first --
     * a lookup that answered false for everything would pass most of the negative
     * assertions in this file.
     */
    @Test
    public void aselectedMessageInsideTheWindowIsReported() {
        Date now = new Date();
        PublicationIssue issue = openIssue(queryBackedSeries(), new Date(now.getTime() - 48 * HOUR));

        assertTrue(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() - HOUR)),
                null, now));
    }

    /**
     * A message published after the moment asked about is not in the issue yet.
     *
     * The window closes at the proposed cut-off, and "now" is what the message
     * editor asks with. A future-dated message reported as a member would be the
     * feature stating a membership that no cut-off has yet produced.
     */
    @Test
    public void amessagePublishedAfterTheCutoffIsNotYetAMember() {
        Date now = new Date();
        PublicationIssue issue = openIssue(queryBackedSeries(), new Date(now.getTime() - 48 * HOUR));

        assertFalse(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() + HOUR)),
                null, now));
    }

    /** A message the series' criteria do not select is not in it. */
    @Test
    public void amessageOutsideTheCriteriaIsNotAMember() {
        Date now = new Date();
        PublicationSeries series = queryBackedSeries();
        series.setCriteria(criteria(messageSeries("dma-nm"), types("PRELIMINARY_NOTICE")));
        PublicationIssue issue = openIssue(series, new Date(now.getTime() - 48 * HOUR));

        assertFalse(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() - HOUR)),
                null, now),
                "a TEMPORARY_NOTICE is not selected by a PRELIMINARY_NOTICE-only series");
    }

    // ---------------------------------------------------------- the overrides

    /**
     * An EXCLUDE beats the query.
     *
     * A curator who removed a message from an issue has made a decision the query
     * does not know about. Reporting it as a member anyway would show an editor a
     * membership that publishing will not produce -- and it is the one direction
     * where being wrong is silent, because the issue looks right until it comes
     * out without the message in it.
     */
    @Test
    public void anExcludeOverrideBeatsTheQuery() {
        Date now = new Date();
        PublicationIssue issue = openIssue(queryBackedSeries(), new Date(now.getTime() - 48 * HOUR));

        assertFalse(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() - HOUR)),
                OverrideKind.EXCLUDE, now));
    }

    /**
     * An INCLUDE constitutes membership on its own, with no query involved.
     *
     * This is how the annexes work: a series with no criteria at all whose issues
     * name their contents. Requiring a query to agree would report every one of
     * those memberships as absent, and they are exactly the ones an editor cannot
     * discover any other way -- no query explains them.
     */
    @Test
    public void anIncludeOverrideMakesAMemberOfACriteriaLessIssue() {
        Date now = new Date();
        PublicationIssue issue = openIssue(curatedSeries(), null);

        assertTrue(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() - HOUR)),
                OverrideKind.INCLUDE, now));
    }

    /** An INCLUDE also overrides a query that would have rejected the message. */
    @Test
    public void anIncludeOverrideBeatsARejectingQuery() {
        Date now = new Date();
        PublicationIssue issue = openIssue(queryBackedSeries(), new Date(now.getTime() - 48 * HOUR));

        assertTrue(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() + HOUR)),
                OverrideKind.INCLUDE, now));
    }

    /** With no override, a series that selects nothing by query reports nothing. */
    @Test
    public void acuratedIssueWithNoOverrideIsNotJoinedByAQuery() {
        Date now = new Date();
        PublicationIssue issue = openIssue(curatedSeries(), new Date(now.getTime() - 48 * HOUR));

        assertFalse(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() - HOUR)),
                null, now),
                "a series whose contents are named by hand must not gain members from a query");
    }

    // -------------------------------------------------------------- the edges

    /**
     * A message that is gone is not reported as being anywhere.
     *
     * Reached when the uid resolves to no message at all -- a stale link, or a
     * message deleted since. There are no facts to decide on, and false is the
     * only answer that is not invented.
     */
    @Test
    public void amissingMessageIsInNoIssue() {
        Date now = new Date();
        PublicationIssue issue = openIssue(queryBackedSeries(), new Date(now.getTime() - 48 * HOUR));

        assertFalse(MessageIssueLookup.wouldContain(issue, null, null, now));
    }

    /**
     * An issue with no interval start is not resolved against a query.
     *
     * PUBLISHED_IN_INTERVAL needs a lower bound and there is none. Substituting
     * one would silently widen the issue's window to the beginning of the corpus.
     */
    @Test
    public void anIssueWithNoIntervalStartIsNotGuessedAt() {
        Date now = new Date();
        PublicationIssue issue = openIssue(queryBackedSeries(), null);

        assertFalse(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() - HOUR)),
                null, now));
    }

    /** A query-backed series with no criteria document has nothing to resolve. */
    @Test
    public void aqueryBackedSeriesWithNoCriteriaReportsNothing() {
        Date now = new Date();
        PublicationSeries series = queryBackedSeries();
        series.setCriteria(null);
        PublicationIssue issue = openIssue(series, new Date(now.getTime() - 48 * HOUR));

        assertFalse(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() - HOUR)),
                null, now));
    }

    /**
     * A criteria document that cannot resolve is not a membership answer.
     *
     * A blank operand would narrow to nothing, and the resolver refuses it rather
     * than quietly emptying the issue. Here that refusal must not become "yes":
     * the series validation surface reports the broken document, and the message
     * detail stays silent instead of asserting a membership nobody can compute.
     */
    @Test
    public void anUnresolvableCriteriaDocumentIsNotAMembershipAnswer() {
        Date now = new Date();
        PublicationSeries series = queryBackedSeries();
        series.setCriteria(criteria(messageSeries("")));
        PublicationIssue issue = openIssue(series, new Date(now.getTime() - 48 * HOUR));

        assertFalse(MessageIssueLookup.wouldContain(issue, published(new Date(now.getTime() - HOUR)),
                null, now));
    }
}
