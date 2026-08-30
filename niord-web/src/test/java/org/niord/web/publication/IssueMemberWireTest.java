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

package org.niord.web.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.QueryParam;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.vo.IssueMemberVo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a member row reaches a screen: named, and named in a language.
 *
 * Two halves of one contract, and each is invisible from the other. The address
 * has to ACCEPT a language -- a member list is read by people working in Danish
 * and in English and the row has to say the same thing to both -- and the row has
 * to CARRY the name once it gets there.
 *
 * No database and no server. This is a shape, and shape tests that need MySQL
 * are shape tests that stop running; what the title resolves TO, per language and
 * for a message that is gone, is asserted in core where a database exists.
 */
public class IssueMemberWireTest {

    /**
     * The member list takes the language as a query parameter.
     *
     * Asserted by reflection rather than by calling it, because the failure this
     * guards against is somebody dropping the parameter while the method still
     * compiles and still answers -- with every row in whatever language the
     * fallback lands on, on every screen, with nothing to show that the request
     * asked for something else.
     */
    @Test
    public void theMemberListAcceptsTheLanguageItShouldTitleItsRowsIn() throws Exception {
        Method members = PublicationIssueRestService.class
                .getMethod("members", String.class, String.class);

        String queried = null;
        for (Annotation[] annotations : members.getParameterAnnotations()) {
            for (Annotation a : annotations) {
                if (a instanceof QueryParam q) {
                    queried = q.value();
                }
            }
        }
        assertEquals("lang", queried,
                "the member list no longer accepts the language its rows are titled in");
    }

    /**
     * The row carries the title on the wire.
     *
     * Against the serialized JSON rather than the getter: the getter is what the
     * test would assert either way, and the thing that actually breaks a screen is
     * a field that never leaves the server.
     */
    @Test
    public void theRowCarriesItsTitle() throws Exception {
        IssueMemberVo vo = new IssueMemberVo();
        vo.setMessageUid("d3f1c0a2-0000-4000-8000-000000000001");
        vo.setFrozenShortId("NM-114-25");
        vo.setTitle("Hals Barre. Fyr slukket.");

        String json = new ObjectMapper().writeValueAsString(vo);

        assertTrue(json.contains("\"title\""), "the member row does not put its title on the wire");
        assertTrue(json.contains("Hals Barre. Fyr slukket."), "the title is empty on the wire");
        assertTrue(json.contains("NM-114-25"),
                "the short id is what the title is read beside, not a replacement for it");
    }
}
