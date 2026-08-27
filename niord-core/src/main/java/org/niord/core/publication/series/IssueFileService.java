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

        String archived = null;
        if (issue.getStatus() == IssueStatus.PUBLISHED && replacing) {
            archived = archiveExisting(issue, desc);
        }

        Path target = paths.repoRoot().resolve(issue.getRepoPath()).resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IssueLifecycleService.TransitionRefusedException("FILE_WRITE_FAILED",
                    "could not write " + target + ": " + e.getMessage());
        }

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

        desc.setFileName(fileName);
        desc.setFilePath(issue.getRepoPath() + "/" + fileName);
        desc.setFileSource(FileSource.UPLOADED);
        desc.setFileSourceSticky(true);

        IssueAuditEntry entry = audit.override(issue, actor, "FILE_UPLOADED", lang, fileName);
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
        audit.override(issue, actor, "FILE_CLEARED", lang, null);
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
        audit.override(issue, actor, cleaned == null ? "LINK_CLEARED" : "LINK_SET", lang, cleaned);
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
