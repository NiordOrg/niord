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
package org.niord.core.db;

import com.vividsolutions.jts.geom.Geometry;
import org.hibernate.jpa.criteria.CriteriaBuilderImpl;
import org.hibernate.jpa.criteria.ParameterRegistry;
import org.hibernate.jpa.criteria.Renderable;
import org.hibernate.jpa.criteria.compile.RenderingContext;
import org.hibernate.jpa.criteria.expression.LiteralExpression;
import org.hibernate.jpa.criteria.predicate.AbstractSimplePredicate;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import java.io.Serializable;

/**
 * Predicate for checking if one geometry is within another
 */
public class SpatialWithinPredicate extends AbstractSimplePredicate implements Serializable {

    private final Expression<Geometry> geom1;
    private final Expression<Geometry> geom2;

    /** Constructor */
    public SpatialWithinPredicate(CriteriaBuilder cb, Expression<Geometry> geom1, Geometry geom2) {
        this(
                cb,
                geom1,
                new LiteralExpression<>((CriteriaBuilderImpl)cb, geom2));
    }

    /** Constructor */
    public SpatialWithinPredicate(CriteriaBuilder cb, Expression<Geometry> geom1, Expression<Geometry> geom2) {
        super((CriteriaBuilderImpl)cb);
        this.geom1 = geom1;
        this.geom2 = geom2;
    }

    public Expression<Geometry> getGeom1() {
        return geom1;
    }

    public Expression<Geometry> getGeom2() {
        return geom2;
    }

    /** {@inheritDoc} */
    @Override
    public void registerParameters(ParameterRegistry registry) {
        // Unused
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public String render(boolean isNegated, RenderingContext renderingContext) {
        StringBuilder buffer = new StringBuilder();
        buffer.append(" within(")
                .append(((Renderable) getGeom1()).render(renderingContext))
                .append(", ")
                .append(((Renderable) getGeom2()).render(renderingContext))
                .append(") = true ");
        return buffer.toString();
    }
}
