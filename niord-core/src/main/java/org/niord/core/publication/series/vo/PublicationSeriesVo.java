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

package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The PUBLIC face of a series.
 *
 * Everything here is safe for an anonymous caller. The split is by class rather
 * than by a runtime flag on one class, because a flag has to be checked at every
 * serialisation point and the one that forgets leaks the whole object -- whereas
 * a field that is not on this type cannot be serialised from it at all.
 *
 * That is the same reasoning the existing publication VOs use, and this follows
 * them deliberately rather than inventing a second pattern.
 */
public class PublicationSeriesVo implements IJsonSerializable {

    private String seriesId;
    private Date created;
    private Date updated;
    private String categoryId;
    private Integer sortOrder;
    private List<PublicationSeriesDescVo> descs = new ArrayList<>();

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public List<PublicationSeriesDescVo> getDescs() {
        return descs;
    }

    public void setDescs(List<PublicationSeriesDescVo> descs) {
        this.descs = descs;
    }
}
