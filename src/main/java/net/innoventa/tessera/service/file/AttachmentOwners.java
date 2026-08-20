package net.innoventa.tessera.service.file;

import org.jmouse.files.OwnerReference;

/**
 * What a file is filed against in a tracker: an issue.
 *
 * <h2>⚠️ No directory, and that is the whole shape of it here</h2>
 *
 * <p>{@code jmouse-files} ships a directory tree, and Tessera does not switch it on. An attachment
 * belongs to the issue it was dropped onto — there is nothing to browse, no folders to arrange, and a
 * tree would be machinery in exchange for nothing. The library's three pieces are separable precisely so
 * this is a choice rather than a price.</p>
 */
public final class AttachmentOwners {

    /** The kind of owner an attachment is filed against. */
    public static final String ISSUE = "ISSUE";

    /**
     * Where attachment bytes are laid out.
     *
     * <p>⚠️ A storage-key namespace, not a directory path — the two are the same string in a product
     * that has directories and this one does not.</p>
     */
    public static final String NAMESPACE = "tessera/attachments";

    private AttachmentOwners() {
    }

    /**
     * The issue a file is attached to, as the library names owners.
     *
     * <p>⚠️ <strong>By identifier, never by key.</strong> {@code TES-42} is what a person pastes into a
     * chat and what an issue page’s URL carries, and it is still the wrong thing to write into a binding
     * row: the key is a rendering of where an issue was raised, and a binding has to outlive every way an
     * issue can be re-addressed. The identifier is the issue.</p>
     *
     * @param issueId the issue’s identifier
     * @return the owner reference
     */
    public static OwnerReference issue(String issueId) {
        return OwnerReference.of(ISSUE, issueId);
    }
}
