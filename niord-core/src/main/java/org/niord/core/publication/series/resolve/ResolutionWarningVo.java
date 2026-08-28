/*
 * Copyright 2026 Danish Maritime Authority.
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

package org.niord.core.publication.series.resolve;

import java.util.List;

/**
 * One warning about a resolution, with the messages it concerns.
 *
 * Grouped by code rather than emitted per message: a curator needs to know that
 * eleven members are cancelled-but-alive, not to read eleven identical lines.
 */
public record ResolutionWarningVo(
        ResolutionWarningCode code,
        List<String> messageUids,
        int count,
        boolean acknowledgeable) {

    public static ResolutionWarningVo of(ResolutionWarningCode code, List<String> messageUids) {
        return new ResolutionWarningVo(code, List.copyOf(messageUids), messageUids.size(), code.isAcknowledgeable());
    }
}
