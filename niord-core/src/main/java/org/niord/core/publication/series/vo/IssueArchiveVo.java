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

import java.util.ArrayList;
import java.util.List;

/**
 * What one audit entry preserved before it overwrote something.
 *
 * Present on the entries that archive -- an amend, a release that wrote over
 * existing bytes, and a hand replacement of a released document -- and absent on
 * every other entry, so a history panel can tell at a glance which lines have a
 * superseded document behind them.
 *
 * It carries the entry's own id, which is the one surrogate id on this surface
 * and is here for a single reason: the stream endpoint addresses an archive by
 * the event that wrote it, and no other identifier distinguishes two amendments
 * of the same issue in the same language.
 */
public class IssueArchiveVo implements IJsonSerializable {

    private Integer auditEntryId;

    private List<IssueArchiveFileVo> files = new ArrayList<>();

    public Integer getAuditEntryId() {
        return auditEntryId;
    }

    public void setAuditEntryId(Integer auditEntryId) {
        this.auditEntryId = auditEntryId;
    }

    public List<IssueArchiveFileVo> getFiles() {
        return files;
    }

    public void setFiles(List<IssueArchiveFileVo> files) {
        this.files = files;
    }
}
