/*
 * Copyright 2016 Danish Maritime Authority.
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

package org.niord.core.message.vo;

import org.niord.model.message.MessageSeriesVo;
import org.niord.model.message.Type;

import java.util.List;
import java.util.Set;

/**
 * Extends the {@linkplain MessageSeriesVo} model with system-specific fields and attributes.
 */
@SuppressWarnings("unused")
public class SystemMessageSeriesVo extends MessageSeriesVo {

    public enum NumberSequenceType {
        /** Associated number sequence will reset to 1 every year **/
        YEARLY,

        /** Associated number sequence will never reset **/
        CONTINUOUS,

        /** The user manually specifies the short ID **/
        MANUAL,

        /** No short ID is assigned **/
        NONE
    }

    // If defined, can be limit which sub-types be handled
    Set<Type> types;
    String shortFormat;
    NumberSequenceType numberSequenceType;
    Integer nextMessageNumber;
    List<String> editorFields;

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Set<Type> getTypes() {
        return types;
    }

    public void setTypes(Set<Type> types) {
        this.types = types;
    }

    public String getShortFormat() {
        return shortFormat;
    }

    public void setShortFormat(String shortFormat) {
        this.shortFormat = shortFormat;
    }

    public NumberSequenceType getNumberSequenceType() {
        return numberSequenceType;
    }

    public void setNumberSequenceType(NumberSequenceType numberSequenceType) {
        this.numberSequenceType = numberSequenceType;
    }

    public Integer getNextMessageNumber() {
        return nextMessageNumber;
    }

    public void setNextMessageNumber(Integer nextMessageNumber) {
        this.nextMessageNumber = nextMessageNumber;
    }

    public List<String> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(List<String> editorFields) {
        this.editorFields = editorFields;
    }
}
