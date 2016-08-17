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

    public enum SortOrder { ASC, DESC }

    protected  int maxSize = Integer.MAX_VALUE;
    protected  int page = 0;
    protected  String sortBy;
    protected  SortOrder sortOrder;


    /** Helper method */
    public static  <T> Set<T> toSet(Set<T> arg) {
        return arg == null ? new HashSet<>() : arg;
    }

    /** Helper method */
    public static  <T, S> Set<T> toSet(Set<S> args, Function<S, T> mapper) {
        return toSet(args).stream()
                .map(mapper)
                .collect(Collectors.toSet());
    }

    /** Helper method */
    public static <T, S> Set<T> toSet(S[] args, Function<S, T> mapper) {
        if (args == null || args.length == 0) {
            return new HashSet<>();
        }
        return Arrays.stream(args)
                .map(mapper)
                .collect(Collectors.toSet());
    }

    /** Helper method */
    public static <T, S> T checkNull(S arg, Function<S, T> mapper) {
        return arg == null ? null : mapper.apply(arg);
    }

    /** Helper method */
    public static <T, S> T checkNull(S arg, T defaultValue, Function<S, T> mapper) {
        return arg == null ? defaultValue : mapper.apply(arg);
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public int getMaxSize() {
        return maxSize;
    }

    public PagedSearchParamsVo maxSize(Integer maxSize) {
        if (maxSize != null) {
            this.maxSize = maxSize;
        }
        return this;
    }

    public int getPage() {
        return page;
    }

    public PagedSearchParamsVo page(Integer page) {
        if (page != null) {
            this.page = page;
        }
        return this;
    }

    public String getSortBy() {
        return sortBy;
    }

    public PagedSearchParamsVo sortBy(String sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public PagedSearchParamsVo sortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }
}
