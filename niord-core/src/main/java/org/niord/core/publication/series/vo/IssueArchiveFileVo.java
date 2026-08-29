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
 * One superseded document an audit entry preserved, named by its language.
 *
 * The pair {audit entry, language} is the whole address: the stream endpoint
 * takes exactly those two and resolves the location itself. The location is
 * deliberately absent -- it is a filesystem path outside the served repository
 * root, and a client that were handed one would either try to fetch it directly
 * or store it, and both are wrong about what the archive is.
 */
public class IssueArchiveFileVo implements IJsonSerializable {

    private String lang;

    /**
     * The document's own name, as it was published.
     *
     * NOT the name it is stored under. Every generation of a language's file is
     * kept, so the stored name carries a stamp to keep two of them apart, and
     * showing that to a reader would name a file nobody ever downloaded.
     */
    private String fileName;

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
