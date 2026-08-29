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

package org.niord.core.publication.series.batch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the series import may and may not do to a series that already exists.
 *
 * Two failures, and they have opposite shapes. Nulling the category is a
 * CONSTRAINT violation raised inside a batch flush, which covers a whole chunk --
 * so one document missing an optional field killed the ten items around it, and
 * the log named a Java property rather than the file. Skipping validation is the
 * quiet one: a document could store a release mode the system cannot honour or a
 * report parameter the issue supplies, and the series looked imported until
 * somebody tried to activate it, at which point the refusal named a field nobody
 * had typed.
 *
 * WHY THIS IS A SOURCE ASSERTION. The processor is a CDI item handler over three
 * injected services and a shared database. Reaching it means standing up a batch
 * job against an estate that other work is using at the same time, to assert two
 * branches; reading the branches is the cheaper trade and it fails for the same
 * reason a behavioural test would.
 */
public class BatchSeriesImportShapeTest {

    private static final Path PROCESSOR = Paths.get("src/main/java/org/niord/core/publication/"
            + "series/batch/BatchPublicationSeriesImportProcessor.java");

    @Test
    public void anabsentCategoryDoesNotClearTheOneTheSeriesHas() throws IOException {
        String src = read();
        assertFalse(src.contains("series.updateFromVo(vo);\n        series.setCategory(category);"),
                "the category is set unconditionally. A document that omits categoryId then writes "
                        + "null into a NOT NULL column, and the violation surfaces at the batch "
                        + "flush -- taking the whole chunk with it, named after a Java property.");
        assertTrue(src.contains("if (category != null) {"),
                "the conditional that keeps an existing category is gone");
        assertTrue(src.contains("} else if (series.getCategory() == null) {"),
                "a NEW series with no category has nowhere to go and must be dropped with a "
                        + "sentence, rather than taking its neighbours down at the flush");
    }

    @Test
    public void theImportRunsTheSameHardRulesTheSavesRun() throws IOException {
        String src = read();
        assertTrue(src.contains("SeriesValidator.hardRules(series)"),
                "the import does not apply the hard rules. A draft may be incomplete; it may not "
                        + "be wrong -- and a file written against another installation is exactly "
                        + "where a wrong one comes from.");
        assertTrue(src.contains("SeriesValidator.validateForActivation(series, null)"),
                "an ACTIVE series may not be edited into incompleteness by a document either; the "
                        + "update endpoint refuses that and the import inherits nothing");
    }

    /** An imported document never activates a series, whatever it says. */
    @Test
    public void theStatusIsStillPinned() throws IOException {
        String src = read();
        assertTrue(src.contains("SeriesStatus keep = existing == null ? SeriesStatus.DRAFT"),
                "the status is no longer pinned. Activation validates against every series rule, "
                        + "and a file that could set it routes around all of them.");
        assertTrue(src.contains("series.setStatus(keep);"));
    }

    private static String read() throws IOException {
        assertTrue(Files.isRegularFile(PROCESSOR),
                "missing " + PROCESSOR + " -- this test reads source, so a move breaks it silently "
                        + "otherwise");
        return Files.readString(PROCESSOR, StandardCharsets.UTF_8);
    }
}
