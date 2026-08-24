package net.innoventa.tessera.service.file;

import lombok.RequiredArgsConstructor;
import org.jmouse.files.jpa.directory.StorageDirectories;
import org.jmouse.files.jpa.directory.StorageDirectory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where a file an assistant kept goes, when there is no issue to hang it on.
 *
 * <h2>⚠️ This class exists for one reason: {@code @Transactional}</h2>
 *
 * <p>Making a folder is not a read. {@code StorageDirectories} renumbers the nested set in bulk
 * statements and flushes and clears the persistence context around them, and it says in its own words
 * that <em>a renumbering is only safe inside a transaction</em> — the library demarcates none, by
 * design, because that is the caller's to decide.</p>
 *
 * <p>⚠️ <strong>A tool handler is not inside one.</strong> Every other caller of {@code requirePath} in
 * this product happens to be — the bootstrap ledger opens one around a step, and {@link AttachmentFiling}
 * runs inside the upload's own — so the requirement is invisible until the first caller that is not.
 * {@code files_upload} was that caller, and the failure was not a message about transactions: it came
 * back as <em>"Operation not allowed after ResultSet closed"</em>, a JDBC sentence naming nothing about
 * what was actually missing.</p>
 *
 * <h2>⚠️ The folder is committed before the bytes are, and that is deliberate</h2>
 *
 * <p>The upload runs in its own transaction afterwards, so an upload that is refused — too large, a type
 * the policy will not take — leaves an empty folder behind. That is the cheaper of the two failures: the
 * folder is reused by the next file with the same subject, and the alternative is holding a
 * renumbering open across the whole of an upload's bytes.</p>
 */
@Component
@RequiredArgsConstructor
public class AssistantFiles {

    private final StorageDirectories directories;

    /**
     * The folder for what an assistant said this file was about, made if it is not there yet.
     *
     * <p>⚠️ The catch is the same one {@link MemberFileTrees} carries and for the same reason: two calls
     * naming the same subject at once both read nothing and both insert, and the loser's
     * unique-constraint violation means precisely that the folder now exists.</p>
     *
     * @param subject a short phrase saying what the file is about
     * @return the folder
     */
    @Transactional
    public StorageDirectory folderFor(String subject) {
        try {
            return directories.requirePath(
                    StorageDirectory.INSTALLATION, FileTrees.protocolDirectory(subject));
        } catch (DataIntegrityViolationException lostTheRace) {
            return directories.requirePath(
                    StorageDirectory.INSTALLATION, FileTrees.protocolDirectory(subject));
        }
    }
}
