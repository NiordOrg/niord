package org.niord.core.db;

import org.hibernate.search.mapper.orm.mapping.HibernateOrmMappingConfigurationContext;
import org.hibernate.search.mapper.orm.mapping.HibernateOrmSearchMappingConfigurer;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;


/**
 *
 * @see https://github.com/quarkusio/quarkus/issues/37772
 */
@ApplicationScoped
@Unremovable
public class QuarkusHibernateOrmSearchMappingConfigurer implements HibernateOrmSearchMappingConfigurer {

    @Override
    public void configure(HibernateOrmMappingConfigurationContext context) {
        // Jandex is not available at runtime in Quarkus,
        // so Hibernate Search cannot perform classpath scanning on startup.
        context.annotationMapping()
                .discoverJandexIndexesFromAddedTypes(false)
                .buildMissingDiscoveredJandexIndexes(false);
    }
}