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
 * One stored preview, described rather than served.
 *
 * The bytes are never addressed from a payload -- the document endpoint is
 * role-guarded and a URL handed out here would only open for a request carrying
 * the bearer, which a top-level navigation does not. What travels is the two
 * facts a reader needs to decide whether to open it: which language it is and
 * when it was rendered.
 *
 * {@code renderedAt} is epoch milliseconds, the same encoding every other
 * instant on these payloads uses.
 */
public record IssuePreviewVo(String lang, long renderedAt) implements IJsonSerializable {
}
