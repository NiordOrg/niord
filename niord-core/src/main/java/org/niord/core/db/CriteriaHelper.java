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

/**
 * Helps initializing the criteria builders and queries and
 * helps building the list of "where" predicates
 * for a Criteria API query
 */
public class CriteriaHelper<T> {

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
     * Returns the collected list of predicates
     * @return the collected list of predicates
     */
    public Predicate[] where() {
        return where.toArray(new Predicate[where.size()]);
    }
}
