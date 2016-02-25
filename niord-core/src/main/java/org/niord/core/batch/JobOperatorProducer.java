package org.niord.core.batch;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * Produces a batch JobOperator for CDI injection
 */
@SuppressWarnings("unused")
public class JobOperatorProducer {

    @Produces
    public JobOperator getJobOperator(InjectionPoint ip) {
        return BatchRuntime.getJobOperator();
    }
}
