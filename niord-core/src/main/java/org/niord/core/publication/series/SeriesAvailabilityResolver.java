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
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The availability list, resolved from ids, for every route that writes one.
 *
 * THREE CALLERS AND ONE ANSWER. The series editor, the one-off editor and the
 * interchange import all take the same field from the same value object, and each
 * had its own copy of this. Two of the copies were already textually identical
 * and the third differed -- which is what a rule stated three times does. A list
 * one form accepts and another refuses is two definitions of a valid publication,
 * differing only by which screen it was typed on.
 *
 * WHAT AN ABSENT availability MEANS: NOTHING CHANGES. Not the setting and not the
 * list. Every client written before this field existed omits it, and re-deciding
 * either there would quietly re-share a publication somebody had narrowed, or
 * empty a list nobody touched. Silence is the same "unchanged" the kind and the
 * cut-off default already carry.
 *
 * WHAT A PRESENT availability MEANS: THE LIST IS THE WHOLE TRUTH. An absent or
 * empty list is then "shared with nobody", because unticking the last domain is
 * something an admin means and there has to be a way to say it. The two halves
 * travel together: a client that sends the setting sends the list it goes with.
 *
 * AN INACTIVE DOMAIN IS KEPT, and this is the one that looks wrong and is not.
 * The visible-from predicate already ignores an inactive domain, so nothing is
 * reachable through one; refusing the SAVE as well would mean that switching a
 * domain off makes every publication shared with it unsaveable -- an admin
 * editing a name in another domain entirely gets a refusal naming a domain they
 * have nothing to do with, and the only way out is to find and untick it. Worse,
 * a domain switched back on would have lost its sharing silently. The row stays,
 * costs nothing while the domain is off, and works again when it comes back.
 *
 * AN UNKNOWN DOMAIN IS REFUSED. That is a client naming something that does not
 * exist, and storing it would put a row in the join table that no screen can
 * show and no predicate can match.
 */
@ApplicationScoped
public class SeriesAvailabilityResolver {

    @Inject
    DomainService domainService;

    /**
     * Applies the value object's sharing list onto the series.
     *
     * Call AFTER the owner is resolved: the owner is stripped from the list, and
     * that can only be done once it is known.
     */
    public void apply(PublicationSeries series, SystemPublicationSeriesVo vo) {
        if (series == null || vo == null) {
            return;
        }
        // Silence is "unchanged", for BOTH halves. updateFromVo has already left
        // the stored availability alone; leaving the list alone here is the other
        // half of the same rule, and without it an old client that saves a name
        // change empties a sharing list it never mentioned.
        if (vo.getAvailability() == null || vo.getAvailability().isBlank()) {
            return;
        }

        series.getAvailableDomains().clear();
        series.getAvailableDomains().addAll(resolve(vo.getAvailableDomainIds(),
                series.getDomain() == null ? null : series.getDomain().getDomainId()));
    }

    /**
     * The ids as domains: deduplicated, without the owner, unknown ones refused.
     *
     * Separate from {@link #apply} so a caller holding ids rather than a value
     * object -- and a test -- can ask the same question.
     *
     * @param ownerDomainId the owner, dropped from the result rather than refused.
     *                      It is already the strongest form of "visible from here",
     *                      so a client that echoes back what it read, or one that
     *                      ticks its own domain, means no harm and gets the same
     *                      answer either way. Storing it would round-trip to a
     *                      different list from the one saved, because the read
     *                      filters the owner out.
     */
    public List<Domain> resolve(List<String> availableDomainIds, String ownerDomainId) {
        List<Domain> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : availableDomainIds == null ? List.<String>of() : availableDomainIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String wanted = id.trim();
            if (wanted.equals(ownerDomainId) || !seen.add(wanted)) {
                continue;
            }
            Domain domain = domainService.findByDomainId(wanted);
            if (domain == null) {
                throw new IssueLifecycleService.TransitionRefusedException("SERIES_INVALID",
                        "the publication is shared with domain '" + wanted + "', which does not exist");
            }
            // Kept even when inactive. See the class note: the predicate ignores
            // it, and refusing the save would make switching a domain off break
            // every publication that had ever been shared with it.
            out.add(domain);
        }
        return out;
    }
}
