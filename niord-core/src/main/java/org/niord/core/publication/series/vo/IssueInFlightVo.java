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

package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

/**
 * The release or amend that is running on this issue right now.
 *
 * Two fields, because a screen that has to say "somebody is publishing this"
 * needs exactly two things: WHICH action, so the sentence names the button that
 * is already pressed rather than a generic wait, and SINCE WHEN, so a reader can
 * see the work is progressing instead of watching a spinner with no age. A render
 * of a compiled annual runs for tens of seconds and the second reader arrives
 * partway through it.
 *
 * The action is a string rather than an enum for the reason the column is: the
 * vocabulary is the set of actions worth guarding, and a client renders one of
 * two known values and falls back to a neutral sentence for anything else.
 *
 * The instant is epoch milliseconds, like every other instant this API emits --
 * see IssueWorkbenchVo.viewedAt. Boxed, because this object only exists at all
 * while there IS work in flight, and a primitive would let a half-filled one
 * report work that began in 1970.
 */
public class IssueInFlightVo implements IJsonSerializable {

    /** PUBLISH or AMEND. */
    private String action;

    /** When that work began, epoch milliseconds. */
    private Long since;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getSince() {
        return since;
    }

    public void setSince(Long since) {
        this.since = since;
    }
}
