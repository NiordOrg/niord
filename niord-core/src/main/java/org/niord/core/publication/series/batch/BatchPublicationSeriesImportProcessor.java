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

package org.niord.core.publication.series.batch;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesService;
import org.niord.core.publication.series.SeriesAvailability;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.SeriesValidator;
import org.niord.core.publication.series.vo.SystemPublicationSeriesVo;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one imported series document into an entity to persist, or drops it.
 *
 * UPSERT BY seriesId. That is the authored external handle -- the same key the
 * citations and the import/export use -- so re-importing a file updates the
 * series it describes rather than creating a second one beside it.
 *
 * NEVER ACTIVE. An imported series arrives DRAFT whatever the file says, and an
 * existing one keeps the status it already had. Activation is a transition that
 * validates against S-1..S-20, and a file that could set it would route around
 * every one of them -- which is exactly what an import from another environment
 * would do, since the file was written by a system where those rules passed
 * against different categories, domains and reports.
 *
 * The two references are resolved by id and REFUSED when they do not exist. A
 * category decides where the series appears on the public page and a domain
 * carries the timezone its cut-offs are read in; inventing either would produce
 * a series that looks imported and is quietly wrong. Refusing one row does not
 * fail the job -- the other rows in the file are still worth importing, and the
 * log says which was dropped and why.
 */
@Dependent
@Named("batchPublicationSeriesImportProcessor")
public class BatchPublicationSeriesImportProcessor extends AbstractItemHandler {

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    PublicationCategoryService categoryService;

    @Inject
    DomainService domainService;

    @Override
    public Object processItem(Object item) {
        SystemPublicationSeriesVo vo = (SystemPublicationSeriesVo) item;

        if (vo.getSeriesId() == null || vo.getSeriesId().isBlank()) {
            getLog().warning("Skipping a series with no seriesId: it is the upsert key, so "
                    + "importing it would create a new series on every run");
            return null;
        }

        PublicationCategory category = null;
        if (vo.getCategoryId() != null && !vo.getCategoryId().isBlank()) {
            category = categoryService.findByCategoryId(vo.getCategoryId());
            if (category == null) {
                getLog().warning("Skipping series " + vo.getSeriesId() + ": no publication category '"
                        + vo.getCategoryId() + "' in this installation");
                return null;
            }
        }

        Domain domain = null;
        if (vo.getDomainId() != null && !vo.getDomainId().isBlank()) {
            domain = domainService.findByDomainId(vo.getDomainId());
            if (domain == null) {
                getLog().warning("Skipping series " + vo.getSeriesId() + ": no domain '"
                        + vo.getDomainId() + "' in this installation, and the domain carries the "
                        + "timezone the cut-offs are read in");
                return null;
            }
        }

        // The availability list, resolved the same way and refused the same way.
        // An id this installation does not have is not a domain to share with, and
        // silently dropping it would make the file mean something different here
        // from what it means where it was written.
        List<Domain> availableDomains = new ArrayList<>();
        for (String id : vo.getAvailableDomainIds()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            Domain shared = domainService.findByDomainId(id.trim());
            if (shared == null) {
                getLog().warning("Skipping series " + vo.getSeriesId() + ": it is shared with domain '"
                        + id + "', which does not exist in this installation");
                return null;
            }
            availableDomains.add(shared);
        }

        PublicationSeries existing = seriesService.findBySeriesId(vo.getSeriesId());
        PublicationSeries series = existing == null ? new PublicationSeries() : existing;

        SeriesStatus keep = existing == null ? SeriesStatus.DRAFT : existing.getStatus();
        series.updateFromVo(vo);

        // AN ABSENT categoryId LEAVES THE CATEGORY ALONE; it does not clear it.
        //
        // The column is NOT NULL, so writing null here does not drop a category:
        // it throws inside the flush, and a batch flush covers a whole chunk --
        // so one document without the field killed the ten items around it, and
        // the log named a Java property rather than the file. A document that
        // omits the field is not asking for the series to leave its category; a
        // NEW series with no category has nowhere to go at all, and that row is
        // dropped with a sentence rather than taking its neighbours down.
        if (category != null) {
            series.setCategory(category);
        } else if (series.getCategory() == null) {
            getLog().warning("Skipping series " + vo.getSeriesId() + ": no categoryId, and every "
                    + "series belongs to a category -- it decides where the series appears on the "
                    + "public page");
            return null;
        }

        // AN ABSENT domainId LEAVES THE OWNER ALONE, exactly as the category is
        // left alone and for the same reason: the column is NOT NULL, so writing
        // null does not clear an owner -- it fails inside the batch flush and takes
        // the chunk around it down. A new series with no owner has no desk at all,
        // and that row is dropped with a sentence of its own below.
        if (domain != null) {
            series.setDomain(domain);
        }
        // The sharing list, on the other hand, IS a full representation: an absent
        // one means "shared with nobody", which is a state the file has to be able
        // to express. Availability itself defaults the same way a script-created
        // series defaults, in updateFromVo's silence rule and the default below.
        series.getAvailableDomains().clear();
        series.getAvailableDomains().addAll(availableDomains);
        if (vo.getAvailability() == null && existing == null) {
            // A file written by hand, or by an older export, says nothing about
            // sharing. A generated series is the owner's; anything else is a
            // reference other desks cite, which is what it was before this field
            // existed. The editor picks the same defaults.
            series.setAvailability(SeriesAvailability.defaultFor(series.getContentMode()));
        }

        series.setStatus(keep);

        // THE SAME GATE THE REST SAVES APPLY. A draft may be incomplete; it may
        // not be wrong. Without this a file could store a release mode the system
        // cannot honour, or a report parameter the issue supplies, and the series
        // would look imported until somebody tried to activate it -- at which
        // point the error names a field nobody typed.
        List<SeriesValidator.FieldError> hard = SeriesValidator.hardRules(series);
        if (!hard.isEmpty()) {
            getLog().warning("Skipping series " + vo.getSeriesId() + ": " + hard.size()
                    + " rule(s) fail -- " + hard);
            return null;
        }

        // And an ACTIVE series may not be edited into incompleteness, exactly as
        // the update endpoint refuses. An imported document never ACTIVATES a
        // series, but it can arrive against one that is already active.
        if (keep == SeriesStatus.ACTIVE) {
            List<SeriesValidator.FieldError> errors =
                    SeriesValidator.validateForActivation(series, null);
            if (!errors.isEmpty()) {
                getLog().warning("Skipping series " + vo.getSeriesId() + ": it is ACTIVE and the "
                        + "document would leave it failing " + errors.size() + " rule(s) -- " + errors);
                return null;
            }
        }

        getLog().info((existing == null ? "Importing new series " : "Updating series ")
                + vo.getSeriesId());
        return series;
    }
}
