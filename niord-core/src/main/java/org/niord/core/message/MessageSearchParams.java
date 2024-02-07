/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.niord.core.domain.Domain;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.util.TimeUtils;
import org.niord.core.util.WebUtils;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;
import org.niord.model.search.PagedSearchParamsVo;

import jakarta.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.niord.core.util.WebUtils.getParameterValues;

/**
 * Defines the message search parameters
 */
@SuppressWarnings("unused")
public class MessageSearchParams extends PagedSearchParamsVo {

    public static final String DATE_FORMAT = "dd-MM-yyyy";

    public enum DateType {

        /** Search by date-interval that overlaps the publish date interval **/
        PUBLISH_DATE,

        /** Search by date-interval that contains the publish-from date **/
        PUBLISH_FROM_DATE,

        /** Search by date-interval that contains the publish-to date **/
        PUBLISH_TO_DATE,

        /** Search by date-interval that overlaps the event date interval **/
        EVENT_DATE,

        /** Search by date-interval that contains the created date **/
        CREATED_DATE,

        /** Search by date-interval that contains the last-updated date **/
        UPDATED_DATE
    }

    public enum UserType { CREATED_BY, UPDATED_BY, LAST_UPDATED_BY }

    public enum CommentsType { ANY, OWN, ANY_UNACK, OWN_UNACK }

    String language;
    String query;
    String domain;
    String messageId;
    Integer referenceLevels;
    Date from;
    Date to;
    DateType dateType;
    Date updatedFrom;
    Date updatedTo;
    String username;
    UserType userType;
    CommentsType commentsType;
    Set<Status> statuses = new HashSet<>();
    Set<Type> types = new HashSet<>();
    Set<MainType> mainTypes = new HashSet<>();
    Set<String> seriesIds = new HashSet<>();
    Set<String> areaIds = new HashSet<>();
    Set<String> categoryIds = new HashSet<>();
    Set<String> chartNumbers = new HashSet<>();
    Set<String> tags = new HashSet<>();
    Set<String> publications = new HashSet<>();
    Set<String> promulgationTypes = new HashSet<>();
    String viewMode;

    Geometry extent;
    // If an extent is specified, use this to fetch messages with no geometry
    Boolean includeNoPos;


    /**
     * Returns a MessageSearchParams initialized with parameter values from a URL using "default" parameter names
     * @param domain the current domain - defines defaults for sorting
     * @param url the URL
     * @return the MessageSearchParams initialized with parameter values
     */
    public static MessageSearchParams instantiate(Domain domain, String url) {
        return instantiate(domain, WebUtils.parseParameterMap(url));
    }


    /**
     * Returns a MessageSearchParams initialized with parameter values from a request using "default" parameter names
     * @param domain the current domain - defines defaults for sorting
     * @param req the servlet request
     * @return the MessageSearchParams initialized with parameter values
     */
    public static MessageSearchParams instantiate(Domain domain, HttpServletRequest req) {
        return instantiate(domain, req.getParameterMap());
    }


    /**
     * Returns a MessageSearchParams initialized with parameter values from a request parameter map
     * using "default" parameter names
     * @param domain the current domain - defines defaults for sorting
     * @param reqParams the request parameters
     * @return the MessageSearchParams initialized with parameter values
     */
    public static MessageSearchParams instantiate(Domain domain, Map<String, String[]> reqParams) {
        MessageSearchParams params = new MessageSearchParams();
        params.language(getParameterValues(reqParams, "lang"))
                .query(getParameterValues(reqParams, "query"))
                .domain(getParameterValues(reqParams, "domain"))
                .statuses(toSet(reqParams.get("status"), Status::valueOf))
                .mainTypes(toSet(reqParams.get("mainType"), MainType::valueOf))
                .types(toSet(reqParams.get("type"), Type::valueOf))
                .seriesIds(toSet(reqParams.get("messageSeries"), Function.identity()))
                .areaIds(toSet(reqParams.get("area"), Function.identity()))
                .categoryIds(toSet(reqParams.get("category"), Function.identity()))
                .chartNumbers(toSet(reqParams.get("chart"), Function.identity()))
                .tags(toSet(reqParams.get("tag"), Function.identity()))
                .publications(toSet(reqParams.get("publication"), Function.identity()))
                .promulgationTypes(toSet(reqParams.get("promulgationType"), Function.identity()))
                .messageId(getParameterValues(reqParams, "messageId"))
                .referenceLevels(checkNull(getParameterValues(reqParams, "referenceLevels"), Integer::valueOf))
                .from((Long)checkNull(getParameterValues(reqParams, "fromDate"), Long::valueOf))
                .to((Long)checkNull(getParameterValues(reqParams, "toDate"), Long::valueOf))
                .dateType(checkNull(getParameterValues(reqParams, "dateType"), DateType::valueOf))
                .username(getParameterValues(reqParams, "username"))
                .userType(checkNull(getParameterValues(reqParams, "userType"), UserType::valueOf))
                .commentsType(checkNull(getParameterValues(reqParams, "comments"), CommentsType::valueOf))
                .viewMode(getParameterValues(reqParams, "viewMode"))

                // Extent parameters
                .extent(checkNull(getParameterValues(reqParams, "minLat"), Double::valueOf),
                        checkNull(getParameterValues(reqParams, "minLon"), Double::valueOf),
                        checkNull(getParameterValues(reqParams, "maxLat"), Double::valueOf),
                        checkNull(getParameterValues(reqParams, "maxLon"), Double::valueOf))
                .includeNoPos(checkNull(getParameterValues(reqParams, "includeNoPos"), Boolean::valueOf))

                // Standard paged search parameters
                .maxSize(checkNull(getParameterValues(reqParams, "maxSize"), 100, Integer::valueOf))
                .page(checkNull(getParameterValues(reqParams, "page"), 0, Integer::valueOf))
                .sortBy(getParameterValues(reqParams, "sortBy"))
                .sortOrder(checkNull(getParameterValues(reqParams, "sortOrder"), SortOrder::valueOf));

        // If no explicit sort order is specified, sort by domain sort order
        params.checkSortByDomain(domain);

        return params;
    }


    /**
     * If no explicit sort order is specified, sort by domain sort order
     * @param domain the domain
     * @return the search parameter
     */
    public MessageSearchParams checkSortByDomain(Domain domain) {

        if (domain != null && StringUtils.isNotBlank(domain.getMessageSortOrder())) {
            try {
                if (getSortBy() == null) {
                    sortBy(domain.getMessageSortOrder().split(" ")[0]);
                }
                if (getSortOrder() == null) {
                    sortOrder(SortOrder.valueOf(domain.getMessageSortOrder().split(" ")[1]));
                }
            } catch (Exception ignored) {
            }
        }

        // Apply defaults
        if (getSortBy() == null) {
            sortBy("AREA");
        }
        if (getSortOrder() == null) {
            sortOrder(SortOrder.ASC);
        }
        return this;
    }


    /** Returns whether or not the search requires a Lucene search */
    public boolean requiresLuceneSearch() {
        return isNotBlank(query);
    }

    /** Returns whether to sort by ID or not */
    public boolean sortById() {
        return "id".equalsIgnoreCase(sortBy);
    }

    /** Returns whether to sort by event date or not */
    public boolean sortByEventDate() {
        return "date".equalsIgnoreCase(sortBy);
    }

    /** Returns whether to sort by publish date or not */
    public boolean sortByPublishDate() {
        return "publish_date".equalsIgnoreCase(sortBy);
    }

    /** Returns whether to sort by follow-up date or not */
    public boolean sortByFollowUpDate() {
        return "follow_up_date".equalsIgnoreCase(sortBy);
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
     * Adjusts the from/to dates so that the date interval is from the beginning of the from-date
     * to the end of the to-date in the given time zone
     * @param timeZone the time zone
     */
    public void adjustDateInterval(TimeZone timeZone) {
        from = TimeUtils.resetTime(from, timeZone);
        to = TimeUtils.endOfDay(to, timeZone);
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
        if (domain != null) { desc.add(String.format("Domain: %s", domain)); }
        if (isNotBlank(messageId)) { desc.add(String.format("Ref. message: %s", messageId)); }
        if (referenceLevels != null) { desc.add(String.format("Ref. levels: %d", referenceLevels)); }
        if (from != null) { desc.add(String.format("From: %s", new SimpleDateFormat(DATE_FORMAT).format(from))); }
        if (to != null) { desc.add(String.format("To: %s", new SimpleDateFormat(DATE_FORMAT).format(to))); }
        if (dateType != null) { desc.add(String.format("Date Type: %s", dateType)); }
        if (updatedFrom != null) { desc.add(String.format("Updated from: %s", new SimpleDateFormat(DATE_FORMAT).format(updatedFrom))); }
        if (updatedTo != null) { desc.add(String.format("Updated to: %s", new SimpleDateFormat(DATE_FORMAT).format(updatedTo))); }
        if (username != null) { desc.add(String.format("User: %s", username)); }
        if (userType != null) { desc.add(String.format("User type: %s", userType)); }
        if (commentsType != null) { desc.add(String.format("Comments: %s", commentsType)); }
        if (statuses.size() > 0) { desc.add(String.format("Statuses: %s", statuses)); }
        if (types.size() > 0) { desc.add(String.format("Types: %s", types)); }
        if (mainTypes.size() > 0) { desc.add(String.format("Main types: %s", mainTypes)); }
        if (seriesIds.size() > 0) { desc.add(String.format("Series ID's: %s", seriesIds)); }
        if (areaIds.size() > 0) { desc.add(String.format("Area ID's: %s", areaIds)); }
        if (categoryIds.size() > 0) { desc.add(String.format("Category ID's: %s", categoryIds)); }
        if (chartNumbers.size() > 0) { desc.add(String.format("Chart Numbers: %s", chartNumbers)); }
        if (tags.size() > 0) { desc.add(String.format("Tags: %s", tags)); }
        if (publications.size() > 0) { desc.add(String.format("Publications: %s", publications)); }
        if (promulgationTypes.size() > 0) { desc.add(String.format("Promulgation Types: %s", promulgationTypes)); }
        if (extent != null) { desc.add(String.format("Extent: '%s'", extent.toString())); }

        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /***/

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


    public String getDomain() {
        return domain;
    }

    public MessageSearchParams domain(String domain) {
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

    public MessageSearchParams to(Long to) {
        this.to = to == null ? null : new Date(to);
        return this;
    }

    public DateType getDateType() {
        return dateType;
    }

    public MessageSearchParams dateType(DateType dateType) {
        this.dateType = dateType;
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

    public String getUsername() {
        return username;
    }

    public MessageSearchParams username(String username) {
        this.username = username;
        return this;
    }

    public UserType getUserType() {
        return userType;
    }

    public MessageSearchParams userType(UserType userType) {
        this.userType = userType;
        return this;
    }

    public CommentsType getCommentsType() {
        return commentsType;
    }

    public MessageSearchParams commentsType(CommentsType commentsType) {
        this.commentsType = commentsType;
        return this;
    }

    public Set<Status> getStatuses() {
        return statuses;
    }

    public MessageSearchParams statuses(Set<Status> statuses) {
        this.statuses = toSet(statuses);
        return this;
    }

    public MessageSearchParams statuses(Status... statuses) {
        this.statuses = toSet(statuses, Function.identity());
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

    public Set<String> getAreaIds() {
        return areaIds;
    }

    public MessageSearchParams areaIds(Set<String> areaIds) {
        this.areaIds = toSet(areaIds);
        return this;
    }

    public Set<String> getCategoryIds() {
        return categoryIds;
    }

    public MessageSearchParams categoryIds(Set<String> categoryIds) {
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

    public Set<String> getPublications() {
        return publications;
    }

    public MessageSearchParams publications(Set<String> publications) {
        this.publications = toSet(publications);
        return this;
    }

    public Set<String> getPromulgationTypes() {
        return promulgationTypes;
    }

    public MessageSearchParams promulgationTypes(Set<String> promulgationTypes) {
        this.promulgationTypes = promulgationTypes;
        return this;
    }

    public Geometry getExtent() {
        return extent;
    }

    public MessageSearchParams extent(Geometry extent) {
        this.extent = extent;
        return this;
    }

    public Boolean getIncludeNoPos() {
        return includeNoPos;
    }

    public MessageSearchParams includeNoPos(Boolean includeNoPos) {
        this.includeNoPos = includeNoPos;
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
