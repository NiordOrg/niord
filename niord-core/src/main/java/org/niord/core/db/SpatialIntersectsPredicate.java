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

import jakarta.persistence.criteria.Expression;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.sql.SqmTranslator;
import org.hibernate.query.sqm.tree.SqmCopyContext;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.query.sqm.tree.expression.SqmFunction;
import org.hibernate.query.sqm.tree.predicate.AbstractNegatableSqmPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmBooleanExpressionPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmNegatablePredicate;
import org.hibernate.query.sqm.tree.predicate.SqmNullnessPredicate;
import org.locationtech.jts.geom.Geometry;

import java.io.Serializable;
import java.util.Optional;

/**
 * Predicate for checking if one geometry intersects another
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
public class SpatialIntersectsPredicate extends AbstractNegatableSqmPredicate implements Serializable {

    // Class Variables
    private final Expression<Geometry> geom1Exp;
    private final Expression<Geometry> geom2Exp;
    private final SqmFunction<Boolean> function;

    /** Constructor */
    public SpatialIntersectsPredicate(NodeBuilder nodeBuilder, Expression<Geometry> geom1Exp, Geometry geom2, boolean negated) {
        super(negated, nodeBuilder);
        this.geom1Exp = geom1Exp;
        this.geom2Exp = nodeBuilder.literal(geom2);
        function = nodeBuilder.function("intersects", Boolean.class, new Expression[]{this.getGeom1Exp(), this.getGeom2Exp()});
    }

    /** Constructor */
    public SpatialIntersectsPredicate(NodeBuilder nodeBuilder, Expression<Geometry> geom1Exp, Expression<Geometry> geom2Exp, boolean negated) {
        super(negated, nodeBuilder);
        this.geom1Exp = geom1Exp;
        this.geom2Exp = geom2Exp;
        function = nodeBuilder.function("intersects", Boolean.class, new Expression[]{this.getGeom1Exp(), this.getGeom2Exp()});
    }

    /**
     * Returns the first geometry as an SQM expression if it is valid.
     *
     * @return the first geometry as an SQM expression
     */
    public SqmExpression<Geometry> getGeom1Exp() {
        return Optional.ofNullable(this.geom1Exp)
                .filter(SqmExpression.class::isInstance)
                .map(SqmExpression.class::cast)
                .orElse(null);
    }

    /**
     * Returns the second geometry as an SQM expression if it is valid.
     *
     * @return the second geometry as an SQM expression
     */
    public SqmExpression<Geometry> getGeom2Exp() {
        return Optional.ofNullable(this.geom2Exp)
                .filter(SqmExpression.class::isInstance)
                .map(SqmExpression.class::cast)
                .orElse(null);
    }

    /** {@inheritDoc} */
    @Override
    public SpatialIntersectsPredicate copy(SqmCopyContext context) {
        final SpatialIntersectsPredicate existing = context.getCopy(this);
        if (existing != null) return existing;
        final SpatialIntersectsPredicate predicate = context.registerCopy(
                this,
                new SpatialIntersectsPredicate(
                        nodeBuilder(),
                        this.getGeom1Exp().copy(context),
                        this.getGeom2Exp().copy(context),
                        isNegated()
                )
        );
        copyTo(predicate, context);
        return predicate;
    }

    /** {@inheritDoc} */
    @Override
    public <T> T accept(SemanticQueryWalker<T> walker) {
        if (walker instanceof SqmTranslator<?>) {
            Dialect dialect = ((SqmTranslator<?>) walker).getCreationContext().getSessionFactory().getJdbcServices().getDialect();
            if (dialect instanceof HSQLDialect) {
                return walker.visitIsNullPredicate(new SqmNullnessPredicate(function,
                        !isNegated(),
                        nodeBuilder()
                ));
            } else {
                return walker.visitBooleanExpressionPredicate(new SqmBooleanExpressionPredicate(
                        function,
                        isNegated(),
                        nodeBuilder()
                ));
            }
        }
        return function.accept(walker);
    }

    /** {@inheritDoc} */
    @Override
    public void appendHqlString(StringBuilder sb) {
        function.appendHqlString(sb);
    }

    /** {@inheritDoc} */
    @Override
    protected SqmNegatablePredicate createNegatedNode() {
        return new SpatialIntersectsPredicate(
                nodeBuilder(),
                this.getGeom1Exp(),
                this.getGeom2Exp(),
                !isNegated()
        );
    }
}
