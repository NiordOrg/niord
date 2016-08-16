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
import com.vividsolutions.jts.io.ParseException;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.message.vo.PagedSearchParamsVo;
import org.niord.model.vo.MainType;
import org.niord.model.vo.Status;
import org.niord.model.vo.Type;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Defines the message search parameters
 */
@SuppressWarnings("unused")
public class MessageSearchParams extends PagedSearchParamsVo {

    public static final String DATE_FORMAT = "dd-MM-yyyy";

    public enum DateType { PUBLISH_DATE, ACTIVE_DATE, CREATED_DATE }

    String language;
    String query;
    Boolean domain;
    String messageId;
    Integer referenceLevels;
    Date from;
    Date to;
    DateType dateType;
    Date updatedFrom;
    Date updatedTo;
    Set<Status> statuses = new HashSet<>();
    Set<Type> types = new HashSet<>();
    Set<MainType> mainTypes = new HashSet<>();
    Set<String> seriesIds = new HashSet<>();
    Set<Integer> areaIds = new HashSet<>();
    Set<Integer> categoryIds = new HashSet<>();
    Set<String> chartNumbers = new HashSet<>();
    Set<String> atonUids = new HashSet<>();
    Set<String> tags = new HashSet<>();
    String viewMode;

    Geometry extent;
    // If an extent is specified, use this to fetch messages with no geometry
    Boolean includeGeneral;

    // Print parameters
    String report;
    String pageSize;
    String pageOrientation;
    Boolean debug;


    /**
     * Returns a MessageSearchParams initialized with parameter values from a request using "default" parameter names
     * @param req the servlet request
     * @return the MessageSearchParams initialized with parameter values
     */
    public static MessageSearchParams instantiate(HttpServletRequest req) {
        MessageSearchParams params = new MessageSearchParams();
        params.language(req.getParameter("lang"))
                .query(req.getParameter("query"))
                .domain(checkNull(req.getParameter("domain"), true, Boolean::valueOf))
                .statuses(toSet(req.getParameterValues("status"), Status::valueOf))
                .mainTypes(toSet(req.getParameterValues("mainType"), MainType::valueOf))
                .types(toSet(req.getParameterValues("type"), Type::valueOf))
                .seriesIds(toSet(req.getParameterValues("messageSeries"), Function.identity()))
                .areaIds(toSet(req.getParameterValues("area"), Integer::valueOf))
                .categoryIds(toSet(req.getParameterValues("category"), Integer::valueOf))
                .chartNumbers(toSet(req.getParameterValues("chart"), Function.identity()))
                .tags(toSet(req.getParameterValues("tag"), Function.identity()))
                .messageId(req.getParameter("messageId"))
                .referenceLevels(checkNull(req.getParameter("referenceLevels"), Integer::valueOf))
                .atonUids(toSet(req.getParameterValues("aton"), Function.identity()))
                .from((Long)checkNull(req.getParameter("fromDate"), Long::valueOf))
                .to((Long)checkNull(req.getParameter("toDate"), Long::valueOf))
                .dateType(checkNull(req.getParameter("dateType"), DateType::valueOf))
                .viewMode(req.getParameter("viewMode"))

                // Extent parameters
                .extent(checkNull(req.getParameter("minLat"), Double::valueOf),
                        checkNull(req.getParameter("minLon"), Double::valueOf),
                        checkNull(req.getParameter("maxLat"), Double::valueOf),
                        checkNull(req.getParameter("maxLon"), Double::valueOf))
                .includeGeneral(checkNull(req.getParameter("includeGeneral"), Boolean::valueOf))

                // Print parameters
                .report(req.getParameter("report"))
                .pageSize(checkNull(req.getParameter("pageSize"), "A4", Function.identity()))
                .pageOrientation(checkNull(req.getParameter("pageOrientation"), "portrait", Function.identity()))
                .debug(checkNull(req.getParameter("debug"), false, Boolean::valueOf))

                // Standard paged search parameters
                .maxSize(checkNull(req.getParameter("maxSize"), 100, Integer::valueOf))
                .page(checkNull(req.getParameter("page"), 0, Integer::valueOf))
                .sortBy(req.getParameter("sortBy"))
                .sortOrder(checkNull(req.getParameter("sortOrder"), SortOrder::valueOf));
        return params;
    }


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

    /** Converts en extent defined by max and min lat-lons into a JTS geometry */
    public MessageSearchParams extent(Double minLat, Double minLon, Double maxLat, Double maxLon) {
        this.extent = JtsConverter.toJtsExtent(minLat, minLon, maxLat, maxLon);
        return this;
    }

    /** Converts en extent defined by a WKT definition into a JTS geometry */
    public MessageSearchParams extent(String wkt) {
        try {
            this.extent = JtsConverter.wktToJts(wkt);
        } catch (ParseException ignored) {
        }
        return this;
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
        if (domain != null) { desc.add(String.format("Domain: %b", domain)); }
        if (isNotBlank(messageId)) { desc.add(String.format("Ref. message: %s", messageId)); }
        if (referenceLevels != null) { desc.add(String.format("Ref. levels: %d", referenceLevels)); }
        if (from != null) { desc.add(String.format("From: %s", new SimpleDateFormat(DATE_FORMAT).format(from))); }
        if (to != null) { desc.add(String.format("To: %s", new SimpleDateFormat(DATE_FORMAT).format(to))); }
        if (dateType != null) { desc.add(String.format("Date Type: %s", dateType)); }
        if (updatedFrom != null) { desc.add(String.format("Updated from: %s", new SimpleDateFormat(DATE_FORMAT).format(updatedFrom))); }
        if (updatedTo != null) { desc.add(String.format("Updated to: %s", new SimpleDateFormat(DATE_FORMAT).format(updatedTo))); }
        if (statuses.size() > 0) { desc.add(String.format("Statuses: %s", statuses)); }
        if (types.size() > 0) { desc.add(String.format("Types: %s", types)); }
        if (mainTypes.size() > 0) { desc.add(String.format("Main types: %s", mainTypes)); }
        if (seriesIds.size() > 0) { desc.add(String.format("Series ID's: %s", seriesIds)); }
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


    public Boolean getDomain() {
        return domain;
    }

    public MessageSearchParams domain(Boolean domain) {
        this.domain = domain;
        return this;
    }

    public String getMessageId() {
        return messageId;
    }

    public MessageSearchParams messageId(String messageId) {
        this.messageId = messageId;
        return this;
    }

    public Integer getReferenceLevels() {
        return referenceLevels;
    }

    public MessageSearchParams referenceLevels(Integer referenceLevels) {
        this.referenceLevels = referenceLevels;
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

    public DateType getDateType() {
        return dateType;
    }

    public MessageSearchParams dateType(DateType dateType) {
        this.dateType = dateType;
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

    public Set<String> getSeriesIds() {
        return seriesIds;
    }

    public MessageSearchParams seriesIds(Set<String> seriesIds) {
        this.seriesIds = toSet(seriesIds);
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

    public Boolean getIncludeGeneral() {
        return includeGeneral;
    }

    public MessageSearchParams includeGeneral(Boolean includeGeneral) {
        this.includeGeneral = includeGeneral;
        return this;
    }

    public String getReport() {
        return report;
    }

    public MessageSearchParams report(String report) {
        this.report = report;
        return this;
    }

    public String getPageSize() {
        return pageSize;
    }

    public MessageSearchParams pageSize(String pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public String getPageOrientation() {
        return pageOrientation;
    }

    public MessageSearchParams pageOrientation(String pageOrientation) {
        this.pageOrientation = pageOrientation;
        return this;
    }

    public Boolean getDebug() {
        return debug;
    }

    public MessageSearchParams debug(Boolean debug) {
        this.debug = debug;
        return this;
    }

    public String getViewMode() {
        return viewMode;
    }

    public MessageSearchParams viewMode(String viewMode) {
        this.viewMode = viewMode;
        return this;
    }
}
