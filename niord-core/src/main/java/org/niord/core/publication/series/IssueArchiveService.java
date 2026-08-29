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

package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.niord.core.publication.series.vo.IssueArchiveFileVo;
import org.niord.core.publication.series.vo.IssueArchiveVo;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reading back a superseded publication file.
 *
 * The writers keep every generation of every language indefinitely, and until
 * something can hand one back the archive is a folder nobody can open. This is
 * the only route to those bytes: they live outside the served repository root
 * precisely so that no anonymous repository read can reach a withdrawn edition,
 * which means the price is that a deliberate, gated read has to exist.
 *
 * AN ARCHIVE IS ADDRESSED BY THE EVENT THAT WROTE IT, not by a path. An issue
 * can be amended many times and each amendment supersedes the same language's
 * file, so "the archived Danish document of this issue" names several files; the
 * audit entry is what distinguishes them, and it is also the row that says who
 * caused the supersession and why. Taking a path from the caller instead would
 * be handing the filesystem to the request.
 */
@ApplicationScoped
public class IssueArchiveService extends BaseService {

    private static final Logger log = LoggerFactory.getLogger(IssueArchiveService.class);

    /** The refusal for everything this resolver cannot reach, whatever the reason. */
    public static final String NOT_FOUND = "ARCHIVE_ENTRY_NOT_FOUND";

    @Inject
    PublicationPathService paths;

    /**
     * One archived file: where it is, and what it was called when it was public.
     *
     * The stored name and the published name are different strings and both are
     * needed -- one to open the file, the other to offer it under the name it had
     * when somebody cited it.
     */
    public record ArchivedFile(int auditEntryId, String lang, String fileName, Path path, long sizeBytes) {
    }

    /**
     * The archive of one audit entry, in the shape the history panel reads.
     *
     * Null when the entry preserved nothing, so an entry with no archive carries
     * no empty object that a client would have to distinguish from a real one.
     */
    public static IssueArchiveVo archiveOf(IssueAuditEntry entry) {
        List<Record> records = recordsOf(entry.getArchivePath());
        if (records.isEmpty()) {
            return null;
        }
        IssueArchiveVo vo = new IssueArchiveVo();
        vo.setAuditEntryId(entry.getId());
        for (Record r : records) {
            IssueArchiveFileVo file = new IssueArchiveFileVo();
            file.setLang(r.lang());
            file.setFileName(r.fileName());
            vo.getFiles().add(file);
        }
        return vo;
    }

    /**
     * The archived file of one language, for one audit entry of one issue.
     *
     * EVERY REFUSAL IS THE SAME REFUSAL. An entry that belongs to another issue,
     * an entry that archived nothing, a language the entry did not archive, a
     * stored location that escapes the archive root, and a file that is no longer
     * on disk all answer ARCHIVE_ENTRY_NOT_FOUND. Distinguishing them would let a
     * caller who may read one issue's history establish, one request at a time,
     * which audit ids exist elsewhere and what languages they hold -- and there is
     * nothing an admin does differently on hearing which of the five it was.
     *
     * In particular an entry of ANOTHER issue is not a 403. A refusal that says
     * "not yours" has already confirmed the row exists.
     */
    public ArchivedFile resolve(PublicationIssue issue, Integer auditEntryId, String lang) {
        if (issue == null || auditEntryId == null || lang == null || lang.isBlank()) {
            throw refusal(auditEntryId, lang);
        }
        IssueAuditEntry entry = em.find(IssueAuditEntry.class, auditEntryId);
        if (entry == null || entry.getIssue() == null
                || !entry.getIssue().getId().equals(issue.getId())) {
            throw refusal(auditEntryId, lang);
        }

        Record match = null;
        for (Record r : recordsOf(entry.getArchivePath())) {
            if (lang.equals(r.lang())) {
                match = r;
                break;
            }
        }
        if (match == null) {
            throw refusal(auditEntryId, lang);
        }

        // The stored location is data, and data written years ago by a version of
        // the writer that no longer runs. It is re-checked against the root here
        // rather than trusted, because this is the one place it is turned back
        // into a file handle -- a row carrying ../../ would otherwise stream
        // whatever the application account can read.
        Path root = paths.archiveRoot().toAbsolutePath().normalize();
        Path file;
        try {
            file = Paths.get(match.location()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            log.warn("Audit entry {} names an unusable archive location '{}'",
                    auditEntryId, match.location());
            throw refusal(auditEntryId, lang);
        }
        if (!file.startsWith(root)) {
            log.warn("Audit entry {} names the archive location {}, which is outside the archive "
                    + "root {}; refusing to serve it", auditEntryId, file, root);
            throw refusal(auditEntryId, lang);
        }
        if (!Files.isRegularFile(file)) {
            // The entry says bytes were preserved and they are not there. Worth a
            // line in the log even though the caller is told nothing: an archive
            // that has lost a file is an operational fact, and the trail that
            // recorded the supersession is now the only evidence it happened.
            log.warn("Audit entry {} records an archived {} document at {}, which is not on disk",
                    auditEntryId, lang, file);
            throw refusal(auditEntryId, lang);
        }

        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            log.warn("Could not read the size of the archived document {}", file, e);
            throw refusal(auditEntryId, lang);
        }
        return new ArchivedFile(entry.getId(), match.lang(), match.fileName(), file, size);
    }

    // ------------------------------------------------------------- the column

    /**
     * One entry of the archive column: where the file is, and what it was called.
     *
     * The column holds the locations the writers produced, comma separated,
     * because a single action archives one file per language. Language and
     * published name are read back off the layout the writers use rather than
     * stored twice -- two records of the same fact drift, and the layout is
     * asserted by the path service that produces it.
     */
    private record Record(String lang, String fileName, String location) {
    }

    private static List<Record> recordsOf(String archivePath) {
        List<Record> out = new ArrayList<>();
        if (archivePath == null || archivePath.isBlank()) {
            return out;
        }
        for (String raw : archivePath.split(",")) {
            String location = raw.trim();
            if (location.isEmpty()) {
                continue;
            }
            Path path;
            try {
                path = Paths.get(location);
            } catch (InvalidPathException e) {
                continue;
            }
            Path name = path.getFileName();
            Path folder = path.getParent();
            if (name == null || folder == null || folder.getFileName() == null) {
                continue;
            }
            out.add(new Record(folder.getFileName().toString(),
                    publishedName(name.toString()), location));
        }
        return out;
    }

    /**
     * The name the document had when it was public.
     *
     * Every generation of a language's file is kept side by side, so the writer
     * prefixes the instant it archived at to keep two of them apart. That prefix
     * is bookkeeping: offering a reader "1755424218000-EfS-Uge-27-2026.pdf" names
     * a file that was never published under that name.
     */
    private static String publishedName(String storedName) {
        int dash = storedName.indexOf('-');
        if (dash <= 0 || dash == storedName.length() - 1) {
            return storedName;
        }
        String prefix = storedName.substring(0, dash);
        for (int i = 0; i < prefix.length(); i++) {
            if (!Character.isDigit(prefix.charAt(i))) {
                return storedName;
            }
        }
        return storedName.substring(dash + 1);
    }

    private static IssueLifecycleService.TransitionRefusedException refusal(Integer auditEntryId, String lang) {
        return new IssueLifecycleService.TransitionRefusedException(NOT_FOUND,
                "this issue has no archived " + lang + " document on audit entry " + auditEntryId);
    }
}
