/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.message;

import com.vividsolutions.jts.geom.Geometry;
import org.niord.core.util.GeoJsonUtils;
import org.niord.model.PagedSearchParamsVo;
import org.niord.model.vo.MainType;
import org.niord.model.vo.Status;
import org.niord.model.vo.Type;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Defines the message search parameters
 */
@SuppressWarnings("unused")
public class MessageSearchParams extends PagedSearchParamsVo {

    public static final String DATE_FORMAT = "dd-MM-yyyy";

    String language;
    String query;
    Date from;
    Date to;
    Date updatedFrom;
    Date updatedTo;
    Set<Status> statuses = new HashSet<>();
    Set<Type> types = new HashSet<>();
    Set<MainType> mainTypes = new HashSet<>();
    Set<Integer> areaIds = new HashSet<>();
    Set<Integer> categoryIds = new HashSet<>();
    Set<String> chartNumbers = new HashSet<>();
    Set<String> atonUids = new HashSet<>();
    Set<String> tags = new HashSet<>();
    Geometry extent;
    Boolean includeGeneral; // If an extent is specified, use this to fetch messages with no geometry


    /** Returns whether or not the search requires a Lucene search */
    public boolean requiresLuceneSearch() {
        return isNotBlank(query);
    }

    /** Returns whether to sort by ID or not */
    public boolean sortById() {
        return "id".equalsIgnoreCase(sortBy);
    }

    /** Returns whether to sort by date or not */
    public boolean sortByDate() {
        return "date".equalsIgnoreCase(sortBy);
    }

    /** Returns whether to sort by area or not */
    public boolean sortByArea() {
        return "area".equalsIgnoreCase(sortBy);
    }


    /**
     * Returns a string representation of the search criteria
     * @return a string representation of the search criteria
     */
    @Override
    public String toString() {
        List<String> desc = new ArrayList<>();
        if (isNotBlank(language)) { desc.add(String.format("Language: %s", language)); }
        if (isNotBlank(query)) { desc.add(String.format("Query: '%s'", query)); }
        if (from != null) { desc.add(String.format("From: %s", new SimpleDateFormat(DATE_FORMAT).format(from))); }
        if (to != null) { desc.add(String.format("To: %s", new SimpleDateFormat(DATE_FORMAT).format(to))); }
        if (updatedFrom != null) { desc.add(String.format("Updated from: %s", new SimpleDateFormat(DATE_FORMAT).format(updatedFrom))); }
        if (updatedTo != null) { desc.add(String.format("Updated to: %s", new SimpleDateFormat(DATE_FORMAT).format(updatedTo))); }
        if (statuses.size() > 0) { desc.add(String.format("Statuses: %s", statuses)); }
        if (types.size() > 0) { desc.add(String.format("Types: %s", types)); }
        if (mainTypes.size() > 0) { desc.add(String.format("Main types: %s", mainTypes)); }
        if (areaIds.size() > 0) { desc.add(String.format("Area ID's: %s", areaIds)); }
        if (categoryIds.size() > 0) { desc.add(String.format("Category ID's: %s", categoryIds)); }
        if (chartNumbers.size() > 0) { desc.add(String.format("Chart Numbers: %s", chartNumbers)); }
        if (tags.size() > 0) { desc.add(String.format("Tags: %s", tags)); }
        if (atonUids.size() > 0) { desc.add(String.format("Aton UIDs: %s", atonUids)); }
        if (extent != null) { desc.add(String.format("Extent: '%s'", extent.toString())); }

        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public String getLanguage() {
        return language;
    }

    public MessageSearchParams language(String language) {
        this.language = language;
        return this;
    }

    public String getQuery() {
        return query;
    }

    public MessageSearchParams query(String query) {
        this.query = query;
        return this;
    }

    public Date getFrom() {
        return from;
    }

    public MessageSearchParams from(Date from) {
        this.from = from;
        return this;
    }

    public MessageSearchParams from(Long from) {
        this.from = from == null ? null : new Date(from);
        return this;
    }

    public Date getTo() {
        return to;
    }

    public MessageSearchParams to(Date to) {
        this.to = to;
        return this;
    }

    public MessageSearchParams to(Long to) {
        this.to = to == null ? null : new Date(to);
        return this;
    }

    public Date getUpdatedFrom() {
        return updatedFrom;
    }

    public MessageSearchParams updatedFrom(Date updatedFrom) {
        this.updatedFrom = updatedFrom;
        return this;
    }

    public Date getUpdatedTo() {
        return updatedTo;
    }

    public MessageSearchParams updatedTo(Date updatedTo) {
        this.updatedTo = updatedTo;
        return this;
    }

    public Set<Status> getStatuses() {
        return statuses;
    }

    public MessageSearchParams statuses(Set<Status> statuses) {
        this.statuses = toSet(statuses);
        return this;
    }

    public Set<Type> getTypes() {
        return types;
    }

    public MessageSearchParams types(Set<Type> types) {
        this.types = toSet(types);
        return this;
    }

    public Set<MainType> getMainTypes() {
        return mainTypes;
    }

    public MessageSearchParams mainTypes(Set<MainType> mainTypes) {
        this.mainTypes = toSet(mainTypes);
        return this;
    }

    public Set<Integer> getAreaIds() {
        return areaIds;
    }

    public MessageSearchParams areaIds(Set<Integer> areaIds) {
        this.areaIds = toSet(areaIds);
        return this;
    }

    public Set<Integer> getCategoryIds() {
        return categoryIds;
    }

    public MessageSearchParams categoryIds(Set<Integer> categoryIds) {
        this.categoryIds = toSet(categoryIds);
        return this;
    }

    public Set<String> getChartNumbers() {
        return chartNumbers;
    }

    public MessageSearchParams chartNumbers(Set<String> chartNumbers) {
        this.chartNumbers = toSet(chartNumbers);
        return this;
    }

    public Set<String> getTags() {
        return tags;
    }

    public MessageSearchParams tags(Set<String> tags) {
        this.tags = toSet(tags);
        return this;
    }

    public Set<String> getAtonUids() {
        return atonUids;
    }

    public MessageSearchParams atonUids(Set<String> atonUids) {
        this.atonUids = toSet(atonUids);
        return this;
    }

    public Geometry getExtent() {
        return extent;
    }

    public MessageSearchParams extent(Geometry extent) {
        this.extent = extent;
        return this;
    }

    public MessageSearchParams extent(Double minLat, Double minLon, Double maxLat, Double maxLon) {
        this.extent = GeoJsonUtils.toJtsExtent(minLat, minLon, maxLat, maxLon);
        return this;
    }

    public Boolean getIncludeGeneral() {
        return includeGeneral;
    }

    public MessageSearchParams includeGeneral(Boolean includeGeneral) {
        this.includeGeneral = includeGeneral;
        return this;
    }

}
