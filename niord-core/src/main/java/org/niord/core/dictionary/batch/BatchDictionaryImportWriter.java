/*
 * Copyright 2017 Danish Maritime Authority.
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
package org.niord.core.dictionary.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.dictionary.Dictionary;
import org.niord.core.dictionary.DictionaryService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the dictionaries to the database
 */
@Named
public class BatchDictionaryImportWriter extends AbstractItemHandler {

    @Inject
    DictionaryService dictionaryService;


    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            Dictionary dictionary = (Dictionary) i;
            dictionaryService.importDictionary(dictionary);
        }
        getLog().info(String.format("Persisted %d dictionaries in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
