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
package org.niord.model.message;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * Represents a message series
 */
@ApiModel(value = "MessageSeries", description = "A message series")
@XmlRootElement(name = "messageSeries")
@XmlType(propOrder = {
        "seriesId", "mainType", "mrnFormat", "shortFormat", "nextMessageNumber", "numberSequenceType", "editorFields"
})
@SuppressWarnings("unused")
public class MessageSeriesVo implements IJsonSerializable {

    public enum NumberSequenceType {
        /** Associated number sequence will reset to 1 every year **/
        YEARLY,

        /** Associated number sequence will never reset **/
        CONTINUOUS,

        /** The user manually specifies the short ID and MRN **/
        MANUAL,

        /** No short ID or MRN is assigned **/
        NONE
    }

    String seriesId;
    MainType mainType;
    String mrnFormat;
    String shortFormat;
    NumberSequenceType numberSequenceType;
    Integer nextMessageNumber;
    List<String> editorFields;

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public MainType getMainType() {
        return mainType;
    }

    public void setMainType(MainType mainType) {
        this.mainType = mainType;
    }

    public String getMrnFormat() {
        return mrnFormat;
    }

    public void setMrnFormat(String mrnFormat) {
        this.mrnFormat = mrnFormat;
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
