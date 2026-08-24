package net.innoventa.tessera.service.file;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import org.jmouse.files.OwnerReference;
import org.jmouse.files.jpa.FileBindings;
import org.jmouse.files.jpa.directory.StorageDirectories;
import org.jmouse.files.jpa.directory.StorageDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * An attachment also goes in a folder, so that there is somewhere to browse it.
 *
 * <h2>⚠️ A listener, and the route stays the library's</h2>
 *
 * <p>The alternative is a Tessera controller wrapping {@code FileController} so there is somewhere to put
 * this — which is exactly the duplication the shared file surface exists to delete, arriving by the back
 * door and looking reasonable while it does. {@link org.jmouse.files.management.FileManagementEvent} is
 * published <strong>inside the transaction</strong> for this purpose, so the binding and the file it
 * describes land together or neither does. {@link AttachmentActivityListener} is the other one.</p>
 *
 * <h2>⚠️ A second binding, never a move</h2>
 *
 * <p>The file keeps {@code ISSUE:<id>} and gains {@code DIRECTORY:<id>}. {@code refile} would replace,
 * and replacing the issue binding takes the attachment off its issue — which is where every permission
 * question about it is answered. The library's own words for this table are <em>"a document attached to
 * an issue and also filed in a directory"</em>: two places is the shape, not a workaround for it.</p>
 *
 * <h2>⚠️ Failing to file must not fail the upload</h2>
 *
 * <p>Because the two are not equally important. The bytes and the attachment are what somebody asked
 * for; the folder is where it can also be found. If the tree cannot be reached — a path too deep, a
 * project that vanished between the upload and this line — the attachment is still on the issue, still
 * visible on the issue screen, and merely absent from the Files tree until somebody re-files it. Losing
 * the upload instead would be trading the thing that matters for the thing that does not.</p>
 *
 * <p>⚠️ It is logged at WARN rather than swallowed, because a tree that is quietly missing half the
 * attachments is a screen that looks complete and is not.</p>
 */
@Component
@RequiredArgsConstructor
public class AttachmentFiling {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentFiling.class);

    private final StorageDirectories directories;
    private final FileBindings       bindings;
    private final IssueRepository    issueRepository;
    private final ProjectRepository  projectRepository;

    /**
     * File a freshly uploaded attachment into its issue's folder.
     *
     * @param uploaded what the library said happened
     */
    @EventListener
    public void file(org.jmouse.files.management.FileManagementEvent.Uploaded uploaded) {
        OwnerReference owner = uploaded.owner();

        // ⚠️ ISSUE owners only. A DIRECTORY upload — somebody dropping a file straight into a folder on
        // the Files screen — is already exactly where it belongs, and re-filing it would move it out of
        // the folder it was just put in.
        if (!AttachmentOwners.ISSUE.equals(owner.ownerType())) {
            return;
        }

        try {
            fileUnderIssue(uploaded.fileId(), owner.ownerId());
        } catch (RuntimeException notFiled) {
            LOGGER.warn("📁 '{}' is attached to issue '{}' but could not be filed into a folder — it will "
                        + "not appear on the Files screen until it is re-filed.",
                        uploaded.fileId(), owner.ownerId(), notFiled);
        }
    }

    /**
     * The folder an issue's attachments belong in, made if it is not there yet.
     *
     * <p>⚠️ Public because the backfill ({@link AttachmentBackfillStep}) files the attachments that
     * predate this listener through the same method — two ways of computing the same path is how the
     * backfill and the live path come to disagree about where an issue's files are.</p>
     *
     * @param issueId the issue
     * @return the folder, or empty when the issue or its project has gone
     */
    public Optional<StorageDirectory> folderOf(String issueId) {
        Optional<Issue> issue = issueRepository.findById(issueId);

        if (issue.isEmpty()) {
            return Optional.empty();
        }

        Optional<Project> project = projectRepository.findById(issue.get().getProjectId());

        if (project.isEmpty()) {
            return Optional.empty();
        }

        // ⚠️ Both halves are KEYS rather than identifiers, because the path is read by people. It follows
        // that the path can go stale — an issue moved between projects keeps its key and its old folder —
        // and that is accepted: no authorization is derived from a folder a person can reach by any route
        // that renames it. See FileTrees.
        return Optional.of(directories.requirePath(
                StorageDirectory.INSTALLATION,
                FileTrees.issueDirectory(project.get().getKey(), issue.get().getIssueKey())));
    }

    private void fileUnderIssue(String fileId, String issueId) {
        folderOf(issueId).ifPresent(folder ->
                bindings.bind(fileId, OwnerReference.of(OwnerReference.DIRECTORY, folder.getId())));
    }
}
