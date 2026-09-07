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

import org.niord.core.publication.series.resolve.CriteriaMissVo;
import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the criteria left out of an issue, as a sample and a count.
 *
 * The count travels beside the rows because the rows are a SAMPLE and not a
 * page. Every candidate the predicate rejected produces one of these, so a wide
 * document over a live corpus yields thousands of nested objects; fifty is
 * enough to see the shape of what is being dropped, and `missCount` is what says
 * how much of it there is. A panel showing fifty rows and calling it the answer
 * would understate an over-narrow criteria document by two orders of magnitude.
 *
 * The field names are the ones the criteria editor's probe already emits, so one
 * client type reads both.
 */
public class IssueOmissionsVo implements IJsonSerializable {

    /**
     * The contract's probe cap: a sample, not a page.
     *
     * Shared by the criteria editor's resolve-preview and by the issue
     * workbench's omissions panel, because the two answer the same question about
     * the same document. Declared once here rather than per endpoint -- two caps
     * that drift show a reader fifty rows on one screen and a different fifty on
     * the other, with only the counts to say they disagree.
     */
    public static final int PROBE_SAMPLE = 50;

    /** How many candidates were dropped in ALL, not how many rows are below. */
    private int missCount;

    private List<CriteriaMissVo> misses = new ArrayList<>();

    /** The full count, and the first {@link #PROBE_SAMPLE} of the rows behind it. */
    public static IssueOmissionsVo of(List<CriteriaMissVo> misses) {
        IssueOmissionsVo vo = new IssueOmissionsVo();
        vo.setMissCount(misses.size());
        vo.setMisses(new ArrayList<>(misses.stream().limit(PROBE_SAMPLE).toList()));
        return vo;
    }

    public int getMissCount() {
        return missCount;
    }

    public void setMissCount(int missCount) {
        this.missCount = missCount;
    }

    public List<CriteriaMissVo> getMisses() {
        return misses;
    }

    public void setMisses(List<CriteriaMissVo> misses) {
        this.misses = misses;
    }
}
