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

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;
import org.niord.core.user.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Every actor field names the person, and none of them names the login.
 *
 * THREE FIELDS ANSWERED ONE QUESTION AND TWO OF THEM AGREED. The history panel
 * and a curation decision both rendered the display name; the release line
 * rendered the username, so the same release read as a person's full name in one
 * card and as an account id in the card above it. The mapping had no test in
 * either direction, which is why it drifted and stayed drifted.
 */
public class ActorNameTest {

    private static final String LOGIN = "jdoe";
    private static final String DISPLAY_NAME = "Jane Doe";

    private static User user(String username, String firstName, String lastName) {
        User u = new User();
        u.setUsername(username);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        return u;
    }

    private static User named() {
        return user(LOGIN, "Jane", "Doe");
    }

    private static User nameless() {
        return user(LOGIN, null, null);
    }

    @Test
    public void apersonIsNamedByTheirName() {
        assertEquals(DISPLAY_NAME, ActorName.of(named()));
    }

    /**
     * A directory row with no name renders the login rather than nothing.
     *
     * The whole point of the field is to say who did something, so an empty
     * answer is worse than an ugly one.
     */
    @Test
    public void anaccountWithNoNameFallsBackToItsLogin() {
        assertEquals(LOGIN, ActorName.of(nameless()));
        assertEquals(LOGIN, ActorName.of(user(LOGIN, "   ", " ")));
    }

    @Test
    public void nopersonIsNoName() {
        assertNull(ActorName.of(null));
    }

    /** The release line on the wire: the name, not the login. */
    @Test
    public void thepublishedByOnTheWireIsTheName() {
        PublicationIssue issue = new PublicationIssue();
        issue.setPublishedBy(named());

        SystemPublicationIssueVo vo = issue.toVo(SystemPublicationIssueVo.class);

        assertEquals(DISPLAY_NAME, vo.getPublishedBy(),
                "the release line named the login; the history panel below it names the person, "
                        + "and both describe the same release");
    }

    @Test
    public void thepublishedByFallsBackToTheLoginForANamelessAccount() {
        PublicationIssue issue = new PublicationIssue();
        issue.setPublishedBy(nameless());

        assertEquals(LOGIN, issue.toVo(SystemPublicationIssueVo.class).getPublishedBy());
    }

    /** An unattended release has no actor at all, and says so rather than guessing. */
    @Test
    public void anunattendedReleaseNamesNobody() {
        assertNull(new PublicationIssue().toVo(SystemPublicationIssueVo.class).getPublishedBy());
    }

    /** The history entry, on the same rule and from the same helper. */
    @Test
    public void thehistoryEntryNamesThePersonToo() {
        IssueAuditEntry entry = new IssueAuditEntry();
        entry.setAction(AuditAction.PUBLISHED);
        entry.setActorKind(ActorKind.USER);
        entry.setUser(named());

        assertEquals(DISPLAY_NAME, entry.toVo().getActorLabel());
    }

    /** And an entry with no person keeps the free-text label the machine wrote. */
    @Test
    public void amachineEntryKeepsItsOwnLabel() {
        IssueAuditEntry entry = new IssueAuditEntry();
        entry.setAction(AuditAction.IMPORTED);
        entry.setActorKind(ActorKind.IMPORT);
        entry.setActorLabel("legacy import");

        assertEquals("legacy import", entry.toVo().getActorLabel());
    }
}
