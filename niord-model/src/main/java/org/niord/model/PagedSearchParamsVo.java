package org.niord.model;

/**
 * Can be used as a base class for search parameters for paged search results
 */
public abstract class PagedSearchParamsVo implements IJsonSerializable {

    public enum SortOrder { ASC, DESC }

    int maxSize = Integer.MAX_VALUE;
    int page = 0;
    String sortBy;
    SortOrder sortOrder;

    /** Getters **/

    public int getMaxSize() {
        return maxSize;
    }

    public int getPage() {
        return page;
    }

    public String getSortBy() {
        return sortBy;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    /** Method-chaining setters **/

    public PagedSearchParamsVo maxSize(Integer maxSize) {
        if (maxSize != null) {
            this.maxSize = maxSize;
        }
        return this;
    }

    public PagedSearchParamsVo page(Integer page) {
        if (page != null) {
            this.page = page;
        }
        return this;
    }

    public PagedSearchParamsVo sortBy(String sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    public PagedSearchParamsVo sortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

}
