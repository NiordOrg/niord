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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Can be used as a base class for search parameters for paged search results
 */
@SuppressWarnings("unused")
public abstract class PagedSearchParamsVo implements IJsonSerializable {

    /**
     * The enum Sort order.
     */
    public enum SortOrder {/**
     * Asc sort order.
     */
    ASC,
        /**
         * Desc sort order.
         */
        DESC }

    /**
     * The Max size.
     */
    protected  int maxSize = Integer.MAX_VALUE;
    /**
     * The Page.
     */
    protected  int page = 0;
    /**
     * The Sort by.
     */
    protected  String sortBy;
    /**
     * The Sort order.
     */
    protected  SortOrder sortOrder;


    /**
     * Helper method
     * @param <T>  the type parameter
     *
     * @param arg the arg
     * @return the set
     */
    public static  <T> Set<T> toSet(Set<T> arg) {
        return arg == null ? new HashSet<>() : arg;
    }

    /**
     * Helper method
     * @param <T>  the type parameter
     *
     * @param <S>    the type parameter
     * @param args   the args
     * @param mapper the mapper
     * @return the set
     */
    public static  <T, S> Set<T> toSet(Set<S> args, Function<S, T> mapper) {
        return toSet(args).stream()
                .map(mapper)
                .collect(Collectors.toSet());
    }

    /**
     * Helper method
     * @param <T>  the type parameter
     *
     * @param <S>    the type parameter
     * @param args   the args
     * @param mapper the mapper
     * @return the set
     */
    public static <T, S> Set<T> toSet(S[] args, Function<S, T> mapper) {
        if (args == null || args.length == 0) {
            return new HashSet<>();
        }
        return Arrays.stream(args)
                .map(mapper)
                .collect(Collectors.toSet());
    }

    /**
     * Helper method
     * @param <T>  the type parameter
     *
     * @param <S>    the type parameter
     * @param arg    the arg
     * @param mapper the mapper
     * @return the t
     */
    public static <T, S> T checkNull(S arg, Function<S, T> mapper) {
        return arg == null ? null : mapper.apply(arg);
    }

    /**
     * Helper method
     * @param <T>  the type parameter
     *
     * @param <S>          the type parameter
     * @param arg          the arg
     * @param defaultValue the default value
     * @param mapper       the mapper
     * @return the t
     */
    public static <T, S> T checkNull(S arg, T defaultValue, Function<S, T> mapper) {
        return arg == null ? defaultValue : mapper.apply(arg);
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /**
     * Gets max size.
     *
     * @return the max size
     */

    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Max size paged search params vo.
     *
     * @param maxSize the max size
     * @return the paged search params vo
     */
    public PagedSearchParamsVo maxSize(Integer maxSize) {
        if (maxSize != null) {
            this.maxSize = maxSize;
        }
        return this;
    }

    /**
     * Gets page.
     *
     * @return the page
     */
    public int getPage() {
        return page;
    }

    /**
     * Page paged search params vo.
     *
     * @param page the page
     * @return the paged search params vo
     */
    public PagedSearchParamsVo page(Integer page) {
        if (page != null) {
            this.page = page;
        }
        return this;
    }

    /**
     * Gets sort by.
     *
     * @return the sort by
     */
    public String getSortBy() {
        return sortBy;
    }

    /**
     * Sort by paged search params vo.
     *
     * @param sortBy the sort by
     * @return the paged search params vo
     */
    public PagedSearchParamsVo sortBy(String sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    /**
     * Gets sort order.
     *
     * @return the sort order
     */
    public SortOrder getSortOrder() {
        return sortOrder;
    }

    /**
     * Sort order paged search params vo.
     *
     * @param sortOrder the sort order
     * @return the paged search params vo
     */
    public PagedSearchParamsVo sortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }
}
