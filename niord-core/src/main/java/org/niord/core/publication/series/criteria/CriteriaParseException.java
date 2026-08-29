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

package org.niord.core.publication.series.criteria;

import org.niord.core.publication.series.PublicationException;

/**
 * A criteria document could not be read.
 *
 * This is unchecked and it is thrown rather than swallowed on purpose. The
 * existing JSON converters in this repo log and return null; for print settings
 * that degrades a PDF, but for criteria it degrades to "no criteria at all",
 * which is indistinguishable from a legitimately empty query and resolves very
 * differently.
 */
public class CriteriaParseException extends PublicationException {

    public CriteriaParseException(String message, Throwable cause) {
        super("CRITERIA_INVALID", message, cause);
    }
}
