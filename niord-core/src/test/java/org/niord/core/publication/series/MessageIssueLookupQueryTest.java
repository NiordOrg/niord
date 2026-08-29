/*
 * Copyright 2026 Danish Emergency Management Agency.
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

package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lookup's four queries, executed against a real schema.
 *
 * MessageIssueLookupTest covers the decision; nothing there touches SQL. These
 * queries are built from strings at call time, so a wrong field name or a stale
 * entity name is not a compile error and not a boot error either -- it is a 500
 * the first time an editor opens a message, on a panel that is supposed to be a
 * quiet footnote on the screen.
 *
 * A uid that matches nothing is the right probe: it drives every one of the four
 * queries (members, overrides, the message itself, the open issues) and asserts
 * only that they PARSE and RUN. Asserting on rows would make this a test of
 * whatever the local database happens to hold.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class MessageIssueLookupQueryTest {

    @Inject
    MessageIssueLookup lookup;

    /** Every query runs, and a uid nobody has answers empty rather than throwing. */
    @Test
    public void theQueriesRunAgainstTheSchema() {
        List<MessageIssueLookup.MessageIssue> rows =
                lookup.forMessage("no-such-message-uid", new Date());

        assertNotNull(rows);
        assertTrue(rows.isEmpty(), "a uid that matches no message is in no issue");
    }

    /**
     * A blank uid is answered without a round trip.
     *
     * The message editor mounts the panel before the message has loaded, so this
     * is the common case rather than an edge one.
     */
    @Test
    public void ablankUidIsAnsweredWithoutQuerying() {
        assertTrue(lookup.forMessage(null, new Date()).isEmpty());
        assertTrue(lookup.forMessage("  ", new Date()).isEmpty());
    }
}
