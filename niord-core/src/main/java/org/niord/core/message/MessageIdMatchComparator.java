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

package org.niord.core.message;

import org.niord.core.domain.Domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Comparator used when resolving a message based on a message ID.
 * If a list of message matches the same message ID, this comparator will induce an order which ensures that:
 * <ul>
 *     <li>The message of the current domain gets preferred</li>
 *     <li>A message with a status of published has higher priority than a deleted message, and so forth.</li>
 * </ul>
 */
public class MessageIdMatchComparator implements Comparator<Message> {
    final Set<String> messageSeries = new HashSet<>();

    /**
     * Constructor
     **/
    public MessageIdMatchComparator(Domain domain) {
        if (domain != null) {
            domain.getMessageSeries()
                    .forEach(ms -> messageSeries.add(ms.getSeriesId()));
        }
    }

    /**
     * Returns a measure of importance of a message based on the message status
     **/
    private int getStatusSortOrder(Message m) {
        if (m == null || m.getStatus() == null) {
            return 0;
        }
        switch (m.getStatus()) {
            case PUBLISHED:
                return 4;
            case EXPIRED:
            case CANCELLED:
                return 3;
            case DRAFT:
            case VERIFIED:
                return 2;
            case IMPORTED:
                return 1;
            case DELETED:
                return 0;
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public int compare(Message m1, Message m2) {
        if (m1 == m2) {
            return 0;
        }
        if (m1 == null) {
            return -1;
        } else if (m2 == null) {
            return 1;
        }
        boolean curDomain1 = m1.getMessageSeries() != null && messageSeries.contains(m1.getMessageSeries().getSeriesId());
        boolean curDomain2 = m2.getMessageSeries() != null && messageSeries.contains(m2.getMessageSeries().getSeriesId());
        if (curDomain1 && !curDomain2) {
            return -1;
        } else if (!curDomain1 && curDomain2) {
            return 1;
        }
        return getStatusSortOrder(m2) - getStatusSortOrder(m1);
    }
}
