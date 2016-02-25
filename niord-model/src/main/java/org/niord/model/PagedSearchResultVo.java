package org.niord.model;

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
 */
public class PagedSearchResultVo<T> implements IJsonSerializable {

    List<T> data = new ArrayList<>();
    long total;
    int size;

    /**
     * Paginates a content list according to the page number and size
     * @param content the list to paginate
     * @param page the page number
     * @param size the page size
     * @return the paged result
     */
    public static <T> PagedSearchResultVo<T> paginate(List<T> content, int page, int size) {
        return paginate(content, page, size, null);
    }


    /**
     * Paginates a content list according to the page number and size
     * @param content the list to paginate
     * @param page the page number
     * @param size the page size
     * @param comparator the comparator
     * @return the paged result
     */
    public static <T> PagedSearchResultVo<T> paginate(List<T> content, int page, int size, Comparator<T> comparator) {
        return paginate(content, page, size, null, comparator);
    }


    /**
     * Paginates a data list according to the page number and size
     * @param data the list to paginate
     * @param page the page number
     * @param size the page size
     * @param filter an optional filter
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
     * @return the mapped result
     */
    public <S> PagedSearchResultVo<S> map(Function<T, S> mapper) {
        Objects.requireNonNull(mapper);

        PagedSearchResultVo<S> result = new PagedSearchResultVo<>();
        result.setSize(getSize());
        result.setTotal(getTotal());
        result.setData(
                getData() == null
                ? null
                : getData().stream()
                    .map(mapper)
                    .collect(Collectors.toList()));
        return result;
    }

    public void updateSize() {
        size = data.size();
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
