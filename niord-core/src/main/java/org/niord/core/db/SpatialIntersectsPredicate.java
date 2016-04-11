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
 * Predicate for checking if one geometry intersects another
 */
public class SpatialIntersectsPredicate extends AbstractSimplePredicate implements Serializable {

    private final Expression<Geometry> geom1;
    private final Expression<Geometry> geom2;

    /** Constructor */
    public SpatialIntersectsPredicate(CriteriaBuilder cb, Expression<Geometry> geom1, Geometry geom2) {
        this(
                cb,
                geom1,
                new LiteralExpression<>((CriteriaBuilderImpl)cb, geom2));
    }

    /** Constructor */
    public SpatialIntersectsPredicate(CriteriaBuilder cb, Expression<Geometry> geom1, Expression<Geometry> geom2) {
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
        buffer.append(" intersects(")
                .append(((Renderable) getGeom1()).render(renderingContext))
                .append(", ")
                .append(((Renderable) getGeom2()).render(renderingContext))
                .append(") = true ");
        return buffer.toString();
    }
}
