package org.niord.core.publication.series.vo;

/**
 * Per-language issue text plus what an admin needs to manage the document.
 *
 * Separate from the public shape for the same reason the issue VOs are: a public
 * reader has no use for where a file came from, and the fields that answer it
 * only make sense next to buttons that change it.
 *
 * The three fields answer three different questions the document panel asks, and
 * none of them can be derived from the others. `href` is where the document
 * actually is, which is not the stored file path -- that is a storage location
 * and 404s if fetched. `fileSource` says whether the file was generated or
 * uploaded, which decides whether "clear" means anything. `fileSourceSticky`
 * says whether the next publish will regenerate over it, which is the difference
 * between a correction that survives and one that vanishes at the least
 * predictable moment.
 */
public class SystemPublicationIssueDescVo extends PublicationIssueDescVo {

    /** Where the document is actually fetchable: the external link, or the repository URL. */
    private String href;

    /** GENERATED or UPLOADED, or absent when the language has no file. */
    private String fileSource;

    /** Whether a publish will leave this file alone rather than regenerating over it. */
    private boolean fileSourceSticky;

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getFileSource() {
        return fileSource;
    }

    public void setFileSource(String fileSource) {
        this.fileSource = fileSource;
    }

    public boolean isFileSourceSticky() {
        return fileSourceSticky;
    }

    public void setFileSourceSticky(boolean fileSourceSticky) {
        this.fileSourceSticky = fileSourceSticky;
    }
}
