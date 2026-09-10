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

import org.niord.core.user.User;

/**
 * Who did this, written the way a colleague would recognise them.
 *
 * ONE RULE IN ONE PLACE, because three fields answer that question -- the actor
 * on a history entry, the author of a curation decision, and the person who
 * released an issue -- and they had drifted apart. Two rendered the display name
 * and the third rendered the login, so the history panel named a person in full
 * while the release line two cards above it showed an account id, about the very
 * same action.
 *
 * THE LOGIN IS THE FALLBACK, never the answer. A row that cannot say who did
 * something is worse than one that says it in whatever form the directory holds:
 * {@code User.getName()} already composes first plus last name and drops back to
 * the username when both are empty, and the blank check here covers the account
 * whose name fields hold nothing but whitespace.
 */
public final class ActorName {

    private ActorName() {
    }

    /** The display name of a person, or null where no person is recorded. */
    public static String of(User user) {
        if (user == null) {
            return null;
        }
        String name = user.getName();
        return name == null || name.isBlank() ? user.getUsername() : name;
    }
}
