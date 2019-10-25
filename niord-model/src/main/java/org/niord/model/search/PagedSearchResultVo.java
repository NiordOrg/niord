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

package org.niord.model.search;

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used for pagination of search results
 *
 * @param <T> the type parameter
 */
@SuppressWarnings("unused")
public class PagedSearchResultVo<T> implements IJsonSerializable {

    /**
     * The Data.
     */
    List<T> data = new ArrayList<>();
    /**
     * The Total.
     */
    long total;
    /**
     * The Size.
     */
    int size;

    /**
     * The Description.
     */
// Optionally, specify a description - e.g. textual description of the search criteria
    String description;

    /**
     * Paginates a content list according to the page number and size specified by the search parameters
     *
     * @param <T>     the type parameter
     * @param content the list to paginate
     * @param params  the search parameters
     * @return the paged result
     */
    public static <T> PagedSearchResultVo<T> paginate(List<T> content, PagedSearchParamsVo params) {
        return paginate(content, params.getPage(), params.getMaxSize());
    }


    /**
     * Paginates a content list according to the page number and size
     *
     * @param <T>     the type parameter
     * @param content the list to paginate
     * @param page    the page number
     * @param size    the page size
     * @return the paged result
     */
    public static <T> PagedSearchResultVo<T> paginate(List<T> content, int page, int size) {
        return paginate(content, page, size, null);
    }


    /**
     * Paginates a content list according to the page number and size
     *
     * @param <T>        the type parameter
     * @param content    the list to paginate
     * @param page       the page number
     * @param size       the page size
     * @param comparator the comparator
     * @return the paged result
     */
    public static <T> PagedSearchResultVo<T> paginate(List<T> content, int page, int size, Comparator<T> comparator) {
        return paginate(content, page, size, null, comparator);
    }


    /**
     * Paginates a data list according to the page number and size
     *
     * @param <T>        the type parameter
     * @param data       the list to paginate
     * @param page       the page number
     * @param size       the page size
     * @param filter     an optional filter
     * @param comparator the comparator
     * @return the paged result
     */
    public static <T> PagedSearchResultVo<T> paginate(List<T> data, int page, int size, Predicate<T> filter, Comparator<T> comparator) {
        PagedSearchResultVo<T> result = new PagedSearchResultVo<>();
        if (data != null) {
            if (filter != null) {
                data = data.stream()
                        .filter(filter)
                        .collect(Collectors.toList());
            }
            result.setTotal(data.size());

            Stream<T> stream = data.stream();
            if (comparator != null) {
                stream = stream.sorted(comparator);
            }
            result.setData(stream
                    .skip(page * size)
                    .limit(size)
                    .collect(Collectors.toList()));
        }
        result.setSize(size);
        return result;
    }


    /**
     * Maps the data of this search result
     *
     * @param <S>    the type parameter
     * @param mapper the mapper
     * @return the mapped result
     */
    public <S> PagedSearchResultVo<S> map(Function<T, S> mapper) {
        Objects.requireNonNull(mapper);

        PagedSearchResultVo<S> result = new PagedSearchResultVo<>();
        result.setSize(getSize());
        result.setTotal(getTotal());
        result.setDescription(getDescription());
        result.setData(
                getData() == null
                ? null
                : getData().stream()
                    .map(mapper)
                    .collect(Collectors.toList()));
        return result;
    }

    /**
     * Update size paged search result vo.
     *
     * @return the paged search result vo
     */
    public PagedSearchResultVo<T> updateSize() {
        size = data.size();
        if (total < size) {
            total = size;
        }
        return this;
    }

    /**
     * Gets data.
     *
     * @return the data
     */
    public List<T> getData() {
        return data;
    }

    /**
     * Sets data.
     *
     * @param data the data
     */
    public void setData(List<T> data) {
        this.data = data;
    }

    /**
     * Gets total.
     *
     * @return the total
     */
    public long getTotal() {
        return total;
    }

    /**
     * Sets total.
     *
     * @param total the total
     */
    public void setTotal(long total) {
        this.total = total;
    }

    /**
     * Gets size.
     *
     * @return the size
     */
    public int getSize() {
        return size;
    }

    /**
     * Sets size.
     *
     * @param size the size
     */
    public void setSize(int size) {
        this.size = size;
    }

    /**
     * Gets description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets description.
     *
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
