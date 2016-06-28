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
package org.niord.core.db;

import org.apache.commons.lang.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helps initializing the criteria builders and queries and
 * helps building the list of "where" predicates
 * for a Criteria API query
 */
@SuppressWarnings("unused")
public class CriteriaHelper<T> {

    private static final Pattern MATCH_TEXT_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    CriteriaBuilder cb;
    CriteriaQuery<T> q;
    List<Predicate> where = new LinkedList<>();

    /**
     * Initializes a CriteriaBuilder and CriteriaQuery with a Tuple query.
     * @param em the entity manager
     * @return the newly instantiated criteria builder
     */
    public static CriteriaHelper<Tuple> initWithTupleQuery(EntityManager em) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> tupleQuery = builder.createTupleQuery();
        return new CriteriaHelper<>(builder, tupleQuery);
    }

    /**
     * Initializes a CriteriaBuilder and CriteriaQuery with a the given result class.
     * @param em the entity manager
     * @param resultClass the result class
     * @return the newly instantiated criteria builder
     */
    public static <T> CriteriaHelper<T> initWithQuery(EntityManager em, Class<T> resultClass) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(resultClass);
        return new CriteriaHelper<>(builder, query);
    }


    /**
     * Constructor
     *
     * @param cb the criteria builder
     * @param q the criteria query
     */
    public CriteriaHelper(CriteriaBuilder cb, CriteriaQuery<T> q) {
        this.cb = cb;
        this.q = q;
    }


    /** Returns the associated criteria builder */
    public CriteriaBuilder getCriteriaBuilder() {
        return cb;
    }


    /** Returns the associated criteria query */
    public CriteriaQuery<T> getCriteriaQuery() {
        return q;
    }


    /**
     * Adds a predicate to the list
     * @param predicate the predicate to add
     */
    public CriteriaHelper<T> add(Predicate predicate) {
        where.add(predicate);
        return this;
    }


    /**
     * If value is defined, matches the attribute with the value
     *
     * @param attr the attribute
     * @param value the value to match
     */
    public <V> CriteriaHelper<T> equals(Expression<V> attr, V value) {
        if (value != null) {
            where.add(cb.equal(attr, value));
        }
        return this;
    }

    /**
     * If value is defined, substring-matches the attribute with the value.
     *
     * @param attr the attribute
     * @param value the value to substring-match
     */
    public CriteriaHelper<T> like(Expression<String> attr, String value) {
        if (StringUtils.isNotBlank(value)) {
            where.add(cb.like(cb.lower(attr), "%" + value.toLowerCase() + "%"));
        }
        return this;
    }

    /**
     * If value is defined, matches the attribute with the value.
     *
     * @param attr the attribute
     * @param value the value to substring-match
     */
    public CriteriaHelper<T> startsWith(Expression<String> attr, String value) {
        if (StringUtils.isNotBlank(value)) {
            where.add(cb.like(cb.lower(attr), value.toLowerCase() + "%"));
        }
        return this;
    }

    /**
     * If values is defined, matches the attribute with any of the values.
     * If values is undefined (null or empty) this predicate yields false.
     *
     * @param attr the attribute
     * @param values the values to match
     */
    public <V> CriteriaHelper<T> in(Expression<V> attr, Collection<V> values) {
        if (values != null && values.size() > 0) {
            where.add(attr.in(values));
        } else {
            where.add(cb.disjunction()); // Always false
        }
        return this;
    }

    /**
     * If value1 is defined the attribute must be greater than or equal to this value.
     * If value2 is defined the attribute must be less than or equal to this value.
     *
     * @param attr the attribute
     * @param value1 the first value
     * @param value2 the second value
     */
    public <V extends Comparable<? super V>> CriteriaHelper<T> between(Expression<V> attr, V value1, V value2) {
        if (value1 != null) {
            where.add(cb.greaterThanOrEqualTo(attr, value1));
        }
        if (value2 != null) {
            where.add(cb.lessThanOrEqualTo(attr, value2));
        }
        return this;
    }

    /**
     * Performs a range check that cater with possibly open-ended attribute intervals and possibly open-ended
     * filter interval.
     *
     * @param attr1 the first attribute
     * @param attr2 the second attribute
     * @param value1 the first value
     * @param value2 the second value
     */
    public <V extends Comparable<? super V>> CriteriaHelper<T> overlaps(Expression<V> attr1, Expression<V> attr2, V value1, V value2) {

        if (value1 != null) {
            where.add(cb.or(cb.greaterThanOrEqualTo(attr2, value1), cb.isNull(attr2)));
        }
        if (value2 != null) {
            where.add(cb.or(cb.lessThanOrEqualTo(attr1, value2), cb.isNull(attr1)));
        }
        return this;
    }

    /**
     * If value is defined, attempts a pseudo google-style free text search of the value.
     * Supported format:
     * <ul>
     *     <li>ab cd: Matches either of the values.</li>
     *     <li>"ab cd": Quoted matches must be exact.</li>
     *     <li>+ab: Must contain value.</li>
     *     <li>-ab: Must not contain value.</li>
     *     <li>ab*: Must match the given wildcard value pattern.</li>
     * </ul>
     *
     * @param attr the attribute
     * @param value the free-text match
     */
    public CriteriaHelper<T> matchText(Expression<String> attr, String value) {
        if (StringUtils.isNotBlank(value)) {
            Predicate predicate = null;
            Matcher m = MATCH_TEXT_PATTERN.matcher(value);
            while (m.find()) {
                String quotedText = m.group(1);
                String plainText = m.group(2);

                if (quotedText != null) {
                    Predicate p = cb.like(cb.lower(attr), "%" + quotedText.toLowerCase() + "%");
                    predicate = predicate == null ? p : cb.or(predicate, p);

                } else if (plainText != null) {
                    plainText = plainText.replace('*', '%');
                    if (plainText.startsWith("-")) {
                        Predicate p = cb.not(cb.like(cb.lower(attr), "%" + plainText.substring(1).toLowerCase() + "%"));
                        predicate = predicate == null ? p : cb.and(predicate, p);
                    } else if (plainText.startsWith("+")) {
                        Predicate p = cb.like(cb.lower(attr), "%" + plainText.substring(1).toLowerCase() + "%");
                        predicate = predicate == null ? p : cb.and(predicate, p);
                    } else if (!plainText.isEmpty()) {
                        Predicate p = cb.like(cb.lower(attr), "%" + plainText.toLowerCase() + "%");
                        predicate = predicate == null ? p : cb.or(predicate, p);
                    }
                }
            }
            if (predicate != null) {
                where.add(predicate);
            }
        }
        return this;
    }


    /**
     * Returns the collected list of predicates
     * @return the collected list of predicates
     */
    public Predicate[] where() {
        return where.toArray(new Predicate[where.size()]);
    }
}
