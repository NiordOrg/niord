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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extends the {@linkplain MessageVo} model with system-specific fields and attributes.
 */
@SuppressWarnings("unused")
public class SystemMessageVo extends MessageVo {

    int revision;
    Boolean autoTitle;
    Boolean autoPublication;
    List<MessagePublicationVo> publications;
    String thumbnailPath;
    String repoPath;
    String editRepoPath;
    Integer unackComments;
    Map<String, Boolean> editorFields;


    /** {@inheritDoc} **/
    @Override
    public void rewriteRepoPath(String repoPath1, String repoPath2) {
        super.rewriteRepoPath(repoPath1, repoPath2);

        if (StringUtils.isNotBlank(repoPath1) && StringUtils.isNotBlank(repoPath2) && thumbnailPath != null) {
            thumbnailPath = thumbnailPath.replace(repoPath1, repoPath2);
        }
    }


    /**
     * Annoyingly, the TinyMCE editor used for editing the rich text description, will rewrite paths to
     * attachments (i.e. used in hyperlinks and embedded images), so that "/rest/repo/file/..." becomes
     * "rest/repo/file/...". This causes problems if the description html is used outside the "/" context
     * root, as is the case when you e.g. print reports.
     * <p>
     * This function fixes the links to attachment resources.
     */
    private void fixDescriptionRepoPaths() {
        if (getParts() != null) {
            getParts().stream()
                    .filter(p -> p.getDescs() != null)
                    .flatMap(p -> p.getDescs().stream())
                    .filter(desc -> StringUtils.isNotBlank(desc.getDetails()))
                    .filter(desc -> desc.getDetails().contains("\"rest/repo/file"))
                    .forEach(desc -> desc.setDetails(desc.getDetails().replace("\"rest/repo/file", "\"/rest/repo/file")));
        }
    }

    /**
     * Should be called on a message before editing it, to point links and embedded images
     * to the temporary message repository folder used whilst editing.
     */
    public void toEditRepo() {
        rewriteRepoPath(getRepoPath(), getEditRepoPath());
    }


    /**
     * Should be called before saving an edited message, to point links and embedded images
     * back to the message repository
     */
    public void toMessageRepo() {
        rewriteRepoPath(getEditRepoPath(), getRepoPath());
        fixDescriptionRepoPaths();
    }


    /** {@inheritDoc} **/
    @Override
    public void sort(String language) {
        super.sort(language);
        if (StringUtils.isNotBlank(language)) {
            if (publications != null) {
                publications.stream()
                    .filter(p -> p.getPublication() != null)
                    .forEach(p -> p.getPublication().sortDescs(language));
            }
        }
    }


    public List<MessagePublicationVo> checkCreatePublications() {
        if (publications == null) {
            publications = new ArrayList<>();
        }
        return publications;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public Boolean isAutoTitle() {
        return autoTitle;
    }

    public void setAutoTitle(Boolean autoTitle) {
        this.autoTitle = autoTitle;
    }

    public Boolean isAutoPublication() {
        return autoPublication;
    }

    public void setAutoPublication(Boolean autoPublication) {
        this.autoPublication = autoPublication;
    }

    public List<MessagePublicationVo> getPublications() {
        return publications;
    }

    public void setPublications(List<MessagePublicationVo> publications) {
        this.publications = publications;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
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
