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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.niord.core.publication.series.PublishChecklistService;

import java.util.ArrayList;
import java.util.List;

/**
 * The release rail as the client reads it.
 *
 * ONE definition of the wire shape, for two readers. The rail is returned both
 * by the checklist endpoint the publish dialog calls and inside the issue
 * workbench, and two hand-built maps of the same rows drift the first time a
 * field is added to one of them.
 *
 * The resolution the rail took is deliberately NOT here. It is on the checklist
 * record so that publish freezes exactly what the rail counted; it is a member
 * set of up to a thousand uids and no screen renders it.
 *
 * NOT an {@code IJsonSerializable}, and the order is written out -- see
 * {@link PublishCheckRowVo}: this shape has to serialise byte for byte as the
 * hand-built map it replaced, nulls included. {@code memberCount} is appended
 * LAST for that reason: a key added anywhere else moves every key after it.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"rows", "canPublish", "blockingCodes", "memberCount"})
public class PublishChecklistVo {

    /** All fourteen rows, in the order the rail renders them. */
    private List<PublishCheckRowVo> rows = new ArrayList<>();

    private boolean canPublish;

    /** The BLOCK rows that did not pass, which is what the publish gate refuses on. */
    private List<String> blockingCodes = new ArrayList<>();

    /**
     * How many messages this rail counted, at the cut-off it was computed for.
     *
     * THE HEADLINE NUMBER OF THE RELEASE DIALOG, and it has to travel as a number.
     * The dialog asks for a rail at the instant it is offering to stamp, and the
     * one thing it says out loud is how many messages that release would carry --
     * so without this it fell back to the count on the screen behind it, which is
     * the count for a different instant. An admin moving the cut-off back a week
     * then watched the list change and the headline stay put.
     *
     * The alternative was to read it out of the MEMBER_LIMIT row's detail line,
     * which is prose written for a person ("214 of 1000"). Parsing that is a
     * second definition of the count that breaks the day the sentence is reworded
     * or translated.
     *
     * Zero where nothing was resolved -- an uploaded or link-backed issue raises
     * no membership question at all -- which is the same number MEMBER_LIMIT
     * reports for those.
     */
    private int memberCount;

    public static PublishChecklistVo of(PublishChecklistService.Checklist checklist) {
        PublishChecklistVo vo = new PublishChecklistVo();
        List<PublishCheckRowVo> rows = new ArrayList<>();
        for (PublishChecklistService.CheckRow row : checklist.rows()) {
            rows.add(PublishCheckRowVo.of(row));
        }
        vo.setRows(rows);
        vo.setCanPublish(checklist.canPublish());
        vo.setBlockingCodes(checklist.blockingCodes());
        // Off the resolution the rail itself took, so the number the dialog shows
        // and the rows it renders beside it cannot be answers about two different
        // member sets.
        vo.setMemberCount(checklist.resolution() == null
                ? 0 : checklist.resolution().members().size());
        return vo;
    }

    public List<PublishCheckRowVo> getRows() {
        return rows;
    }

    public void setRows(List<PublishCheckRowVo> rows) {
        this.rows = rows;
    }

    public boolean isCanPublish() {
        return canPublish;
    }

    public void setCanPublish(boolean canPublish) {
        this.canPublish = canPublish;
    }

    public List<String> getBlockingCodes() {
        return blockingCodes;
    }

    public void setBlockingCodes(List<String> blockingCodes) {
        this.blockingCodes = blockingCodes;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
}
