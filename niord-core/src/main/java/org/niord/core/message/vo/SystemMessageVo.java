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
import org.niord.core.message.Message;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.repo.IRepoBackedVo;
import org.niord.core.util.UidUtils;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessagePartVo;
import org.niord.model.message.MessageVo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extends the {@linkplain MessageVo} model with system-specific fields and attributes.
 */
@SuppressWarnings("unused")
public class SystemMessageVo extends MessageVo implements IRepoBackedVo {

    int revision;
    Boolean autoTitle;
    String thumbnailPath;
    String repoPath;
    String editRepoPath;
    Integer unackComments;
    Boolean separatePage;
    List<BaseMessagePromulgationVo> promulgations;
    Map<String, Boolean> editorFields;


    /** Assigns a new ID to the message **/
    public void assignNewId() {
        setId(UidUtils.newUid());
        repoPath = UidUtils.uidToHashedFolderPath(Message.MESSAGE_REPO_FOLDER, getId());
    }


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


    /**
     * Returns the promulgation with the given type, or null if not found
     * @param clz the class the promulgation will be cast to
     * @param typeId the promulgation type
     * @return the promulgation with the given type, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <P extends BaseMessagePromulgationVo> P promulgation(Class<P> clz, String typeId) {
        if (promulgations == null || typeId == null) {
            return null;
        }
        return (P) promulgations.stream()
                .filter(p -> p.getType().getTypeId().equals(typeId) && clz.isAssignableFrom(p.getClass()))
                .findFirst()
                .orElse(null);
    }


    /**
     * Returns the promulgation with the given type, or null if not found
     * @param typeId the promulgation type
     * @return the promulgation with the given type, or null if not found
     */
    public BaseMessagePromulgationVo promulgation(String typeId) {
        if (promulgations == null || typeId == null) {
            return null;
        }
        return promulgations.stream()
                .filter(p -> p.getType().getTypeId().equals(typeId))
                .findFirst()
                .orElse(null);
    }


    /** Creates the list of promulgations if it does not exist **/
    public List<BaseMessagePromulgationVo> checkCreatePromulgations() {
        if (promulgations == null) {
            promulgations = new ArrayList<>();
        }
        return promulgations;
    }


    /** Returns the message parts of the given type **/
    public List<MessagePartVo> parts(MessagePartType type) {
        if (getParts() == null) {
            return Collections.emptyList();
        }
        return getParts().stream()
                .filter(p -> p.getType() == type)
                .collect(Collectors.toList());
    }


    /** Returns the message part of the given type or null if it does not exist **/
    public MessagePartVo part(MessagePartType type) {
        return parts(type).stream()
                .findFirst()
                .orElse(null);
    }


    /** Returns the message part of the given type or null if it does not exist **/
    public MessagePartVo part(String type) {
        return part(MessagePartType.valueOf(type));
    }


    /** Returns - and potentially creates - a message part of the given type **/
    public MessagePartVo checkCreatePart(MessagePartType type) {
        return checkCreatePart(type, 0);
    }


    /** Returns - and potentially creates - a message part of the given type **/
    public MessagePartVo checkCreatePart(MessagePartType type, int index) {
        MessagePartVo part = part(type);
        if (part == null) {
            part = new MessagePartVo();
            part.setType(type);
            checkCreateParts().add(index, part);
        }
        return part;
    }


    /**
     * Ensures that description records exists for all the given languages
     * @param languages the language ensure description records exist for
     */
    public void checkCreateDescs(String[] languages) {
        Arrays.stream(languages).forEach(language ->
                localizableStream().forEach(l -> l.checkCreateDesc(language)));
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
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

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    @Override
    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }

    @Override
    public String getEditRepoPath() {
        return editRepoPath;
    }

    @Override
    public void setEditRepoPath(String editRepoPath) {
        this.editRepoPath = editRepoPath;
    }

    public Integer getUnackComments() {
        return unackComments;
    }

    public void setUnackComments(Integer unackComments) {
        this.unackComments = unackComments;
    }

    public Boolean getSeparatePage() {
        return separatePage;
    }

    public void setSeparatePage(Boolean separatePage) {
        this.separatePage = separatePage;
    }

    public Map<String, Boolean> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(Map<String, Boolean> editorFields) {
        this.editorFields = editorFields;
    }

    public List<BaseMessagePromulgationVo> getPromulgations() {
        return promulgations;
    }

    public void setPromulgations(List<BaseMessagePromulgationVo> promulgations) {
        this.promulgations = promulgations;
    }
}
