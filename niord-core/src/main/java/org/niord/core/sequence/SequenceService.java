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
package org.niord.core.sequence;

import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.EntityManager;

/**
 * Provides an interface for managing sequences
 */
@Stateless
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
    public long peekNextValue(Sequence sequence) {
        SequenceEntity seq = em.find(SequenceEntity.class, sequence.getName());
        return seq != null ? seq.getNextValue() : sequence.initialValue();
    }


    /**
     * Resets the next value of the given sequence to the initial value
     * @param sequence the sequence
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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
