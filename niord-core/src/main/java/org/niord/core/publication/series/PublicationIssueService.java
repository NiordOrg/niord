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

package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import org.niord.core.service.BaseService;

import java.util.List;

/** Persistence for publication issues. Kept as thin as the series service, and for the same reason. */
@ApplicationScoped
@SuppressWarnings("unused")
public class PublicationIssueService extends BaseService {

    /** Looks an issue up by the public id minted at create and immutable for life. */
    public PublicationIssue findByPublicId(String publicId) {
        return em.createQuery("SELECT i FROM PublicationIssue i WHERE i.publicId = :publicId",
                        PublicationIssue.class)
                .setParameter("publicId", publicId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public List<PublicationIssue> findBySeries(PublicationSeries series) {
        return em.createQuery("SELECT i FROM PublicationIssue i WHERE i.series = :series ORDER BY i.id",
                        PublicationIssue.class)
                .setParameter("series", series)
                .getResultList();
    }

    /**
     * Writes an issue, new or existing.
     *
     * Both entry points go through saveEntity rather than choosing persist or
     * merge here. That choice lives in exactly one place in the codebase, keyed
     * off whether the entity has an id, and a service that decides it again is a
     * second answer to a question with one right one -- the failure being a
     * detached-but-persisted entity handed to create, which persist rejects and
     * the shared helper simply merges. The two methods stay separate because
     * each carries its own rule; the persistence call is not that rule.
     */
    public PublicationIssue create(PublicationIssue issue) {
        removeBlankDescs(issue);
        return saveEntity(issue);
    }

    public PublicationIssue update(PublicationIssue issue) {
        removeBlankDescs(issue);
        return saveEntity(issue);
    }

    private void removeBlankDescs(PublicationIssue issue) {
        if (issue.getDescs() != null) {
            issue.getDescs().removeIf(d -> !d.descDefined());
        }
    }
}
