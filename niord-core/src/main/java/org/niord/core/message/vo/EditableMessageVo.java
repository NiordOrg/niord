/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.message.vo;

import org.apache.commons.lang.StringUtils;
import org.niord.model.message.MessageVo;

/**
 * Extends the {@linkplain MessageVo} class with various message attributes only relevant
 * when editing a message.
 */
public class EditableMessageVo extends MessageVo {

    Boolean autoTitle;
    String editRepoPath;


    /**
     * Rewrites the rich text description from one repository path to another.
     * This happens when a message is edited and its associated repository folder gets
     * copied to a temporary folder until the message is saved.
     * <p>
     * The repository paths may occur in e.g. embedded images and links.
     */
    private void rewriteDescs(String repoPath1, String repoPath2) {
        if (getDescs() != null && StringUtils.isNotBlank(repoPath1) && StringUtils.isNotBlank(repoPath2)) {
            getDescs().forEach(desc -> {
                if (desc.getDescription() != null && desc.getDescription().contains(repoPath1)) {
                    desc.setDescription(desc.getDescription().replace(repoPath1, repoPath2));
                }
            });
        }
    }

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
}
