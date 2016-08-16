package org.niord.core.message.vo;

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
