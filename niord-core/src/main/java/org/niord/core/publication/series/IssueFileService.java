/*
 * Copyright 2026 Danish Maritime Authority.
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

package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

/**
 * Per-language file upload and clear (C6).
 *
 * Upload is legal on a PUBLISHED issue as well as an OPEN one, and that is the
 * design rather than an oversight: it is the post-publish correction path. A
 * wrong PDF on the public site has to be replaceable without retiring the issue
 * and republishing, because retiring changes what the record says happened.
 *
 * An upload onto a published issue archives what it replaces FIRST. C3 keeps
 * every generation indefinitely, so overwriting without archiving destroys the
 * published artefact -- and that is the one people cited.
 *
 * The sticky flag stops the next publish regenerating over a hand-uploaded
 * correction. Without it the correction survives exactly until somebody presses
 * publish again, which is the least predictable moment for it to vanish.
 */
@ApplicationScoped
public class IssueFileService extends BaseService {

    @Inject
    PublicationPathService paths;

    @Inject
    IssueAuditService audit;

    @Transactional
    public PublicationIssueDesc upload(PublicationIssue issue, String lang, String fileName,
                                       byte[] bytes, User actor) {
        PublicationIssueDesc desc = descFor(issue, lang);
        boolean replacing = desc.getFilePath() != null;
        boolean published = issue.getStatus() == IssueStatus.PUBLISHED;
        boolean inPlace = published && replacing;

        // EVERY REFUSAL HAPPENS BEFORE A BYTE IS WRITTEN.
        //
        // The write used to run first and the checks after, so a refused upload
        // had already overwritten the file on disk -- and on a published issue
        // that is the cited document, destroyed by a request the server then
        // answered with an error. @Transactional cannot undo it: the rollback
        // reverts the row and leaves the bytes.

        // D-3. One file name per issue, across ALL its languages.
        //
        // Every language writes into the SAME repoPath, so two languages sharing
        // a name are two languages sharing a file: whichever uploads second
        // overwrites the first, and the issue then serves the Danish PDF to an
        // English reader with nothing anywhere recording that it happened.
        for (PublicationIssueDesc other : issue.getDescs()) {
            if (!other.getLang().equals(lang) && fileName.equals(other.getFileName())) {
                throw new IssueLifecycleService.TransitionRefusedException("FILE_NAME_NOT_DISTINCT",
                        "'" + fileName + "' is already the file name for '" + other.getLang()
                                + "'. Both languages write into the same folder, so sharing a name "
                                + "means one silently overwrites the other.");
            }
        }

        // The published address does not move. A citation is a stored URL inside
        // message HTML, and correcting the document must not change where it
        // lives -- a replacement under a new name leaves every citation pointing
        // at the old bytes and the download link pointing at the new ones.
        if (inPlace && !fileName.equals(desc.getFileName())) {
            throw new IssueLifecycleService.TransitionRefusedException("FILE_NAME_IMMUTABLE",
                    "this issue is published and its " + lang + " document already lives at '"
                            + desc.getFileName() + "'. A correction replaces those bytes in place; "
                            + "uploading under the name '" + fileName + "' would leave every stored "
                            + "citation pointing at the old file. Retire the issue if the address "
                            + "itself has to change.");
        }

        // The target. For a published replacement it is the desc's OWN stored
        // path, not one re-derived from the repo root: an imported issue keeps
        // the legacy layout, revision segment and all, so a re-derived path would
        // write beside the cited file rather than over it.
        Path target = inPlace
                ? paths.repoRoot().resolve(desc.getFilePath())
                : paths.repoRoot().resolve(issue.getRepoPath()).resolve(fileName);

        // Archive what is about to be overwritten, and do it before the write.
        // Every generation is kept indefinitely, so overwriting without archiving
        // destroys the published artefact -- the one people cited.
        String archived = inPlace ? archiveExisting(issue, desc) : null;

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IssueLifecycleService.TransitionRefusedException("FILE_WRITE_FAILED",
                    "could not write " + target + ": " + e.getMessage());
        }

        // Who replaced what was there, and when.
        //
        // Only on a REPLACEMENT: a first upload replaces nothing, and recording
        // an actor against it would make every issue look like it had been
        // corrected once. The pair is what turns "this file is not the one that
        // was published" from something a reader infers off an archive folder
        // into something the issue itself says.
        if (replacing) {
            desc.setReplacedBy(actor);
            desc.setReplacedAt(new Date());
        }

        if (!inPlace) {
            desc.setFileName(fileName);
            desc.setFilePath(issue.getRepoPath() + "/" + fileName);
        }
        desc.setFileSource(FileSource.UPLOADED);
        desc.setFileSourceSticky(true);

        // A hand replacement of a released document is its own action. Reading
        // "FILE_UPLOADED" against a published issue tells nobody whether a
        // document appeared or a cited one was overwritten, and those are
        // different events to anyone reading the trail afterwards.
        IssueAuditEntry entry = audit.override(issue, actor,
                inPlace ? AuditAction.FILE_REPLACED_MANUALLY : AuditAction.FILE_UPLOADED, lang, desc.getFileName());
        if (archived != null) {
            entry.setArchivePath(archived);
        }
        em.merge(desc);
        return desc;
    }

    /**
     * Clears an uploaded file, returning the language to generated content.
     *
     * Only while the issue is OPEN. Clearing a published file would leave a dead
     * link where a cited document used to be; the way to fix a published file is
     * to upload a replacement over it.
     */
    @Transactional
    public PublicationIssueDesc clear(PublicationIssue issue, String lang, User actor) {
        if (issue.getStatus() != IssueStatus.OPEN) {
            throw new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_OPEN",
                    "clearing a published file would leave a dead link where a cited document was; "
                            + "upload a replacement instead");
        }
        PublicationIssueDesc desc = descFor(issue, lang);
        desc.setFileName(null);
        desc.setFilePath(null);
        desc.setFileSource(null);
        desc.setFileSourceSticky(false);
        audit.override(issue, actor, AuditAction.FILE_CLEARED, lang, null);
        return em.merge(desc);
    }

    /**
     * Points a language at an external document, or stops pointing at one.
     *
     * The other half of C6. A hosted publication carries a file; an external one
     * carries a link, and an EXTERNAL_LINK issue with no link is a publication
     * that resolves to nothing -- which was the state every such issue was stuck
     * in, because nothing could set it.
     *
     * Editable on a PUBLISHED issue, for the same reason an upload is: a wrong
     * address on the public site has to be correctable without retiring the issue,
     * since retiring changes what the record says happened. Clearing is allowed
     * there too, unlike clearing a file -- a file that is cleared leaves a dead
     * repository link where a cited document was, while a link that is cleared
     * leaves nothing at all, which is the honest state for a publication whose
     * external host took the document down.
     *
     * Blank is normalised to null. An empty string is not an address, and a desc
     * holding one reports itself as a LINK publication that resolves nowhere.
     */
    @Transactional
    public PublicationIssueDesc setLink(PublicationIssue issue, String lang, String link, User actor) {
        PublicationIssueDesc desc = descFor(issue, lang);
        String cleaned = link == null || link.isBlank() ? null : link.trim();

        desc.setLink(cleaned);
        audit.override(issue, actor, cleaned == null ? AuditAction.LINK_CLEARED : AuditAction.LINK_SET, lang, cleaned);
        return em.merge(desc);
    }

    private String archiveExisting(PublicationIssue issue, PublicationIssueDesc desc) {
        Path existing = paths.repoRoot().resolve(desc.getFilePath());
        if (!Files.exists(existing)) {
            return null;
        }
        Path target = paths.archivePathFor(issue.getPublicId(), desc.getLang(),
                desc.getFileName() == null ? "publication.pdf" : desc.getFileName(),
                new Date().getTime());
        try {
            Files.createDirectories(target.getParent());
            Files.copy(existing, target);
            return target.toString();
        } catch (IOException e) {
            throw new IssuePublishService.ArchiveFailedException(
                    "could not archive the published file before replacing it: " + existing, e);
        }
    }

    private PublicationIssueDesc descFor(PublicationIssue issue, String lang) {
        return issue.getDescs().stream()
                .filter(d -> lang.equals(d.getLang()))
                .findFirst()
                .orElseThrow(() -> new IssueLifecycleService.TransitionRefusedException("NO_SUCH_LANGUAGE",
                        "the issue has no " + lang + " desc row; the series may not be configured for it"));
    }
}
