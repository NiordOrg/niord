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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import org.niord.core.publication.series.vo.PublicationIssuePickerDescVo;
import org.niord.core.publication.series.vo.PublicationIssuePickerVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.publication.PublicationType;
import org.niord.model.search.PagedSearchResultVo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The publication picker, and the hydration behind a citation chip.
 *
 * Two questions, one payload. "What can I cite" is a search a logged-in editor
 * runs; "what is this thing I already cited" is a lookup by an id the caller
 * already holds, and anybody who can read the message can read that. They are
 * separated because their TIERS differ and nothing else: a search that answered
 * anonymously would let anyone walk the estate and read the names and links of
 * every open and retired issue, which is exactly the leak the redesign removes,
 * while a lookup that demanded a login would blank the citation chips on the
 * public site.
 *
 * Neither is domain-scoped by default. Where a caller DOES name a domain, the
 * narrowing is VISIBLE-FROM rather than ownership: a publication shared with that
 * domain, or shared with every domain, is citable from it even though it is
 * administered elsewhere. That is what keeps the citation-only publications -- the
 * journal number, the list of lights -- in the dialog from every desk, which is
 * what a null domain used to buy and no longer does.
 *
 * Paging happens in the DATABASE. One series holds over a thousand issues, and
 * selecting them all to hand back twenty is the shape of paging this redesign
 * exists to remove.
 */
@ApplicationScoped
public class IssuePickerService {

    /** The picker's page size when the caller names none. */
    public static final int DEFAULT_MAX_SIZE = 100;

    /**
     * How many ids one hydration call may name.
     *
     * A citation chip resolves the handful of publications one message cites, so
     * this is far above any real caller. It is a bound on the IN-list rather than
     * a product rule: an unbounded one is a query somebody can make arbitrarily
     * expensive from an anonymous endpoint.
     */
    public static final int MAX_IDS = 200;

    /**
     * What the picker shows when the caller asks for no particular status.
     *
     * Not "all three". A picker exists to offer something citable, and a RETIRED
     * issue is a publication that was deliberately withdrawn -- offering it in
     * the same list as the current week's invites a citation into something
     * nobody may read any more. It stays reachable by id, which is what the
     * retired-citation chip needs and all it needs.
     */
    public static final Set<IssueStatus> DEFAULT_STATUSES =
            Set.of(IssueStatus.PUBLISHED, IssueStatus.OPEN);

    @Inject
    EntityManager em;

    /**
     * What a picker may narrow by.
     *
     * Every field is optional and null means "do not narrow". statuses is the
     * exception: an empty set would select nothing, so the caller resolves the
     * default before it gets here and the service does not second-guess it.
     */
    public record PickerQuery(String lang,
                              String title,
                              String publicationSeriesId,
                              Set<IssueStatus> statuses,
                              MessagePublication messagePublication,
                              PublicationType type,
                              String domain,
                              int page,
                              int maxSize) {
    }

    /**
     * The picker's page.
     *
     * Returns the envelope rather than a bare list because a picker that shows
     * twenty of a thousand rows and cannot say so has no way to tell a reader
     * that the one they want is on another page.
     */
    @Transactional
    public PagedSearchResultVo<PublicationIssuePickerVo> search(PickerQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();

        if (query.publicationSeriesId() != null && !query.publicationSeriesId().isBlank()) {
            where.append(" AND s.seriesId = :seriesId");
            params.put("seriesId", query.publicationSeriesId().trim());
        }
        if (query.statuses() != null && !query.statuses().isEmpty()) {
            where.append(" AND i.status IN (:statuses)");
            params.put("statuses", query.statuses());
        }
        if (query.messagePublication() != null) {
            where.append(" AND s.messagePublication = :messagePublication");
            params.put("messagePublication", query.messagePublication());
        }
        if (query.type() != null) {
            // The published type vocabulary maps onto the content mode by a total
            // bijection, so a type filter is a content-mode filter and there is
            // nothing to store or maintain for it.
            where.append(" AND s.contentMode = :contentMode");
            params.put("contentMode", ContentMode.ofPublicationType(query.type()));
        }
        if (query.domain() != null && !query.domain().isBlank()) {
            // VISIBLE FROM the named domain: owned by it, available everywhere, or
            // sharing with it explicitly. One fragment, shared with the anonymous
            // search, because a publication the picker offers and the search
            // withholds is a citation that can be made and then not resolved.
            where.append(" AND ").append(SeriesVisibility.clause("s", "d"));
            SeriesVisibility.bind(params, query.domain());
        }
        if (query.title() != null && !query.title().isBlank()) {
            // Matched inside the requested language, in SQL rather than in memory:
            // filtering after the page is selected would return fewer rows than
            // the page size and report a total nobody could page through. EXISTS
            // rather than a join, so a match in two languages is still one row.
            where.append(" AND EXISTS (SELECT 1 FROM PublicationIssueDesc pd WHERE pd.entity = i"
                    + " AND LOWER(pd.name) LIKE :title");
            params.put("title", "%" + query.title().trim().toLowerCase(Locale.ROOT) + "%");
            if (query.lang() != null && !query.lang().isBlank()) {
                where.append(" AND pd.lang = :titleLang");
                params.put("titleLang", query.lang().trim());
            }
            where.append(")");
        }

        String from = " FROM PublicationIssue i JOIN i.series s LEFT JOIN s.domain d";

        Query count = em.createQuery("SELECT COUNT(i)" + from + where);
        params.forEach(count::setParameter);
        long total = ((Number) count.getSingleResult()).longValue();

        // Newest first, the order every issue surface in the product uses, and a
        // TOTAL order: two issues sharing a cut-off would otherwise swap places
        // between pages and a row could be shown twice or not at all.
        TypedQuery<PublicationIssue> rows = em.createQuery("SELECT i" + from + where
                + " ORDER BY COALESCE(i.cutoffStampedAt, i.intervalTo) DESC, i.publicId DESC",
                PublicationIssue.class);
        params.forEach(rows::setParameter);

        int size = query.maxSize() <= 0 ? DEFAULT_MAX_SIZE : query.maxSize();
        // In long arithmetic, then clamped. page * size overflows int at any page
        // number a client can put in a query string, and setFirstResult refuses a
        // negative offset -- so an overflow would reach a caller as a 500 for a
        // request that simply asked for a page past the end.
        long offset = (long) Math.max(0, query.page()) * size;
        rows.setFirstResult((int) Math.min(offset, Integer.MAX_VALUE));
        rows.setMaxResults(size);

        PagedSearchResultVo<PublicationIssuePickerVo> out = new PagedSearchResultVo<>();
        out.setTotal(total);
        out.setSize(size);
        out.setData(rowsOf(rows.getResultList(), query.lang()));
        return out;
    }

    /**
     * The rows for ids the caller already holds.
     *
     * NO status narrowing, deliberately. This resolves what a message already
     * cites, and a citation into an issue that was later retired must still
     * render its title -- narrowing to published would blank exactly the chips
     * that most need explaining. Unknown ids are omitted rather than refused, for
     * the same reason: one dead citation in a message must not fail the whole
     * lookup and blank the four beside it.
     */
    @Transactional
    public List<PublicationIssuePickerVo> byIds(Collection<String> publicIds, String lang) {
        Set<String> ids = new LinkedHashSet<>();
        for (String id : publicIds == null ? List.<String>of() : publicIds) {
            if (id != null && !id.isBlank()) {
                ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_IDS) {
            throw new IssueLifecycleService.TransitionRefusedException("TOO_MANY_IDS",
                    "at most " + MAX_IDS + " publication ids can be hydrated in one request; "
                            + ids.size() + " were named");
        }

        List<PublicationIssue> found = em.createQuery(
                        "SELECT i FROM PublicationIssue i LEFT JOIN FETCH i.series"
                                + " WHERE i.publicId IN (:ids)", PublicationIssue.class)
                .setParameter("ids", ids)
                .getResultList();

        // Returned in the order they were ASKED for. A chip row renders beside the
        // citation it belongs to, and re-ordering the answer makes the caller
        // rebuild an index the server already had.
        Map<String, PublicationIssue> byId = new LinkedHashMap<>();
        for (PublicationIssue issue : found) {
            byId.put(issue.getPublicId(), issue);
        }
        List<PublicationIssue> ordered = new ArrayList<>();
        for (String id : ids) {
            PublicationIssue issue = byId.get(id);
            if (issue != null) {
                ordered.add(issue);
            }
        }
        return rowsOf(ordered, lang);
    }

    // ------------------------------------------------------------------ mapping

    private static List<PublicationIssuePickerVo> rowsOf(List<PublicationIssue> issues, String lang) {
        List<PublicationIssuePickerVo> out = new ArrayList<>();
        for (PublicationIssue issue : issues) {
            out.add(rowOf(issue, lang));
        }
        return out;
    }

    /**
     * One issue as a picker row.
     *
     * Static and entity-in, VO-out, so the shape can be pinned without a
     * database: what this payload does NOT carry is the whole reason it exists,
     * and a field added by accident is invisible in every behavioural test.
     */
    public static PublicationIssuePickerVo rowOf(PublicationIssue issue, String lang) {
        PublicationSeries series = issue.getSeries();

        PublicationIssuePickerVo vo = new PublicationIssuePickerVo();
        vo.setPublicId(issue.getPublicId());
        vo.setPublicationSeriesId(series == null ? null : series.getSeriesId());
        // Every language the series is named in, not just the requested one: the
        // picker groups rows under the publication and the heading has to read as
        // a name rather than as the slug the id is. Unfiltered because the caller
        // resolves its own fallback -- a series named only in Danish should still
        // head its group when the editor is working in English.
        if (series != null && series.getDescs() != null) {
            for (PublicationSeriesDesc sd : series.getDescs()) {
                if (sd.getLang() != null && sd.getName() != null && !sd.getName().isBlank()) {
                    vo.getSeriesNames().put(sd.getLang(), sd.getName());
                }
            }
        }
        vo.setStatus(issue.getStatus() == null ? null : issue.getStatus().name());
        vo.setPublicFrom(issue.getPublicFrom());
        vo.setType(ContentMode.publicationTypeOf(series == null ? null : series.getContentMode()));
        vo.setMessagePublication(series == null ? null : series.getMessagePublication());
        vo.setLanguageSpecific(series == null || series.isLanguageSpecific());

        for (PublicationIssueDesc desc : issue.getDescs() == null
                ? List.<PublicationIssueDesc>of() : issue.getDescs()) {
            if (lang != null && !lang.isBlank() && !lang.equals(desc.getLang())) {
                continue;
            }
            PublicationIssuePickerDescVo dv = new PublicationIssuePickerDescVo();
            dv.setLang(desc.getLang());
            dv.setTitle(desc.getName());
            // The same link the public payload emits: a stored external link
            // verbatim, and the repository URL for a file we host. The bare
            // storage path is not fetchable, so emitting it would give every
            // natively published issue a chip that 404s.
            dv.setLink(IssuePublicationMapping.linkOf(desc));
            dv.setMessagePublicationFormat(IssuePublicationMapping.citationFormat(issue, desc.getLang()));
            vo.getDescs().add(dv);
        }
        return vo;
    }

}
