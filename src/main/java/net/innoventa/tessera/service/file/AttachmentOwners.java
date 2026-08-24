package net.innoventa.tessera.service.file;

import org.jmouse.files.OwnerReference;

/**
 * What a file is filed against in a tracker: an issue, and — since TSSR-0103 — the folder it also sits in.
 *
 * <h2>⚠️ An attachment is filed TWICE, and neither binding is redundant</h2>
 *
 * <p>This class used to say the opposite: that Tessera left the library's directory tree switched off
 * because an attachment belongs to the issue it was dropped onto and there was nothing to browse. That
 * was true while there was no Files screen. There is one now, and it browses a tree — so an attachment
 * carries {@code ISSUE:<id>} <em>and</em> {@code DIRECTORY:<id>}, which is the case {@code file_bindings}
 * exists as a table for rather than a workaround for its absence.</p>
 *
 * <p>⚠️ <strong>The issue binding is still the load-bearing one.</strong> Every permission question
 * about an attachment is answered through it — the folder is organisation for people. Re-filing an
 * attachment into a different folder therefore moves nothing about who may read it, and that is
 * deliberate: see {@link FileTrees}.</p>
 */
public final class AttachmentOwners {

    /** The kind of owner an attachment is filed against. */
    public static final String ISSUE = "ISSUE";

    /**
     * Where attachment bytes are laid out.
     *
     * <p>⚠️ <strong>The same string as the tree's root, and that is the mechanism rather than a
     * coincidence.</strong> A root's path is handed to the storage key as its namespace, so this
     * constant and {@link FileTrees#ATTACHMENTS_ROOT} must never be allowed to become two values —
     * hence one of them, referenced.</p>
     *
     * <p>⚠️ It is also why switching the tree on moved no bytes: the namespace was already this, and
     * folders deeper than a root contribute nothing to a key.</p>
     */
    public static final String NAMESPACE = FileTrees.ATTACHMENTS_ROOT;

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
