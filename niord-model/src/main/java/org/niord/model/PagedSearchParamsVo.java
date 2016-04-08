package org.niord.model;

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

    int maxSize = Integer.MAX_VALUE;
    int page = 0;
    String sortBy;
    SortOrder sortOrder;


    /** Helper method */
    public final <T> Set<T> toSet(Set<T> arg) {
        return arg == null ? new HashSet<>() : arg;
    }

    /** Helper method */
    protected final <T, S> Set<T> toSet(Set<S> args, Function<S, T> mapper) {
        return toSet(args).stream()
                .map(mapper)
                .collect(Collectors.toSet());
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
