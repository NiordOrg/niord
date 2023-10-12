/*
 * Copyright 2023 GLA UK Research and Development Directive
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
package org.niord.core.aton.vo;

import org.niord.core.aton.AtonLinkType;
import org.niord.core.aton.AtonLinkTypeCategory;

/**
 * An AtoN Link Type Category VO Type.
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
public class AtonLinkTypeCategoryVo {

    // Class Variables
    private AtonLinkType linkType;
    private AtonLinkTypeCategory linkTypeCategory;
    private String description;

    /**
     * Instantiates a new Aton link type category vo.
     */
    public AtonLinkTypeCategoryVo() {

    }

    /**
     * Instantiates a new Aton link type category vo based on an existing
     * AtoN link type category enum.
     */
    public AtonLinkTypeCategoryVo(AtonLinkTypeCategory linkTypeCategory) {
        this.linkType = linkTypeCategory.getAtonLinkType();
        this.linkTypeCategory = linkTypeCategory;
        this.description = linkTypeCategory.getValue();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/
    public AtonLinkType getLinkType() {
        return linkType;
    }

    public void setLinkType(AtonLinkType linkType) {
        this.linkType = linkType;
    }

    public AtonLinkTypeCategory getLinkTypeCategory() {
        return linkTypeCategory;
    }

    public void setLinkTypeCategory(AtonLinkTypeCategory linkTypeCategory) {
        this.linkTypeCategory = linkTypeCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
