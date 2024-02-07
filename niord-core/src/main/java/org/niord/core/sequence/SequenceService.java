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
package org.niord.core.sequence;

import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Provides an interface for managing sequences
 */
@RequestScoped
@SuppressWarnings("unused")
public class SequenceService {

    @Inject
    private Logger log;

    @Inject
    protected EntityManager em;


    /**
     * Peeks the next value of the given sequence
     * @param sequence the sequence
     * @return the next value
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long peekNextValue(Sequence sequence) {
        SequenceEntity seq = em.find(SequenceEntity.class, sequence.getName());
        return seq != null ? seq.getNextValue() : sequence.initialValue();
    }


    /**
     * Resets the next value of the given sequence to the initial value
     * @param sequence the sequence
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void resetNextValue(Sequence sequence) {
        SequenceEntity seq = em.find(SequenceEntity.class, sequence.getName());

        if (seq != null) {
            seq.setNextValue(sequence.initialValue());
            em.merge(seq);
        } else {
            seq = new SequenceEntity();
            seq.setName(sequence.getName());
            seq.setNextValue(sequence.initialValue());
            em.persist(seq);
        }
    }


    /**
     * Returns and bumps the next value of the given sequence
     * @param sequence the sequence
     * @return the next value
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long nextValue(Sequence sequence) {
        long nextValue;
        SequenceEntity seq = em.find(SequenceEntity.class, sequence.getName());
        if (seq != null) {
            // Fetch and bump the next value
            nextValue = seq.nextValue();
            em.merge(seq);
        } else {
            nextValue = sequence.initialValue();
            seq = new SequenceEntity();
            seq.setName(sequence.getName());
            seq.setNextValue(nextValue + 1);
            em.persist(seq);
        }

        return nextValue;
    }
}
