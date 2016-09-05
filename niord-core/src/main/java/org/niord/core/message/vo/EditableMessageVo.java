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

import org.apache.commons.lang.StringUtils;
import org.niord.model.message.MessageVo;

import java.util.Map;

/**
 * Extends the {@linkplain MessageVo} class with various message attributes only relevant
 * when editing a message.
 */
@SuppressWarnings("unused")
public class EditableMessageVo extends MessageVo {

    Boolean autoTitle;
    String editRepoPath;
    Integer unackComments;
    Map<String, Boolean> editorFields;


    /**
     * Annoyingly, the TinyMCE editor used for editing the rich text description, will rewrite paths
     * attachments (i.e. used in hyperlinks and embedded images), so that "/rest/repo/file/..." becomes
     * "rest/repo/file/...". This causes problems if the description html is used outside the "/" context
     * root, as is the case when you e.g. print reports.
     * <p>
     * This function fixes the links to attachment resources.
     */
    private void fixDescriptionRepoPaths() {
        if (getDescs() != null) {
            getDescs().stream()
                    .filter(desc -> StringUtils.isNotBlank(desc.getDescription()))
                    .filter(desc -> desc.getDescription().contains("\"rest/repo/file"))
                    .forEach(desc -> desc.setDescription(desc.getDescription().replace("\"rest/repo/file", "\"/rest/repo/file")));
        }
    }

    /**
     * Should be called on a message before editing it, to point links and embedded images
     * to the temporary message repository folder used whilst editing.
     */
    public void descsToEditRepo() {
        rewriteDescs(getRepoPath(), getEditRepoPath());
    }


    /**
     * Should be called before saving an edited message, to point links and embedded images
     * back to the message repository
     */
    public void descsToMessageRepo() {
        rewriteDescs(getEditRepoPath(), getRepoPath());
        fixDescriptionRepoPaths();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Boolean isAutoTitle() {
        return autoTitle;
    }

    public void setAutoTitle(Boolean autoTitle) {
        this.autoTitle = autoTitle;
    }

    public String getEditRepoPath() {
        return editRepoPath;
    }

    public void setEditRepoPath(String editRepoPath) {
        this.editRepoPath = editRepoPath;
    }

    public Integer getUnackComments() {
        return unackComments;
    }

    public void setUnackComments(Integer unackComments) {
        this.unackComments = unackComments;
    }

    public Map<String, Boolean> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(Map<String, Boolean> editorFields) {
        this.editorFields = editorFields;
    }
}
