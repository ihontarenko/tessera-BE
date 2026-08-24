package net.innoventa.tessera.service.file;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.bootstrap.BootstrapStep;
import org.jmouse.files.OwnerReference;
import org.jmouse.files.jpa.FileBindings;
import org.jmouse.files.jpa.directory.StorageDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The attachments that predate having folders get one, once.
 *
 * <h2>⚠️ Otherwise the Files screen opens on an empty tree in an installation full of files</h2>
 *
 * <p>{@link AttachmentFiling} files attachments as they arrive, and everything uploaded before it existed
 * carries an {@code ISSUE} binding and nothing else. Those files are perfectly intact and completely
 * invisible to a screen that browses the tree — the worst kind of wrong, because it looks like a working
 * screen reporting an empty installation.</p>
 *
 * <h2>⚠️ Grouped by issue, and idempotent at both ends</h2>
 *
 * <p>One folder is made per issue rather than per file, so an issue with nine screenshots costs one path
 * walk. And both halves can be run twice safely: {@code requirePath} reads before it creates, and
 * {@code bind} on a pair that already exists is a no-op rather than a failure.</p>
 *
 * <h2>⚠️ The checksum does not name a version, so this does not re-run when the shape changes</h2>
 *
 * <p>Deliberately: the query asks for attachments with no folder, so a second run over a tidy
 * installation finds nothing and writes nothing. Something that must run again — a change to how the path
 * is built, say — is a re-file rather than a backfill, and re-filing everything from a bootstrap step is
 * how a stale path becomes a duplicated one.</p>
 */
@Component
@RequiredArgsConstructor
public class AttachmentBackfillStep implements BootstrapStep {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentBackfillStep.class);

    /**
     * Attachments that are on an issue and in no folder.
     *
     * <p>⚠️ {@code NOT EXISTS} over the same table rather than a left join, because a file may be filed
     * against several things and a join would answer once per binding.</p>
     */
    private static final String UNFILED_ATTACHMENTS = """
            SELECT binding.fileId, binding.ownerId
            FROM FileBinding binding
            WHERE binding.ownerType = :issueOwner
              AND NOT EXISTS (
                  SELECT other.fileId FROM FileBinding other
                  WHERE other.fileId = binding.fileId AND other.ownerType = :directoryOwner)
            ORDER BY binding.ownerId, binding.createdAt
            """;

    @PersistenceContext
    private EntityManager entityManager;

    private final AttachmentFiling filing;
    private final FileBindings     bindings;

    @Override
    public String key() {
        return "files:attachment-folders";
    }

    @Override
    public String checksum() {
        return FileTrees.ATTACHMENTS_ROOT + "/" + FileTrees.ISSUES_BRANCH;
    }

    @Override
    public String note() {
        return "Attachments uploaded before the tree existed, filed into their issues' folders.";
    }

    @Override
    public Outcome apply() {
        Map<String, List<String>> byIssue = unfiledByIssue();

        if (byIssue.isEmpty()) {
            return Outcome.nothing();
        }

        int filed = 0;

        for (Map.Entry<String, List<String>> entry : byIssue.entrySet()) {
            Optional<StorageDirectory> folder = filing.folderOf(entry.getKey());

            if (folder.isEmpty()) {
                // An attachment whose issue has been deleted. It keeps its binding and stays out of the
                // tree, which is the truthful answer: there is no folder it belongs in.
                LOGGER.warn("📁 {} attachment(s) name issue '{}', which is not there — left unfiled.",
                            entry.getValue().size(), entry.getKey());
                continue;
            }

            OwnerReference directory = OwnerReference.of(OwnerReference.DIRECTORY, folder.get().getId());

            for (String fileId : entry.getValue()) {
                bindings.bind(fileId, directory);
                filed++;
            }
        }

        return new Outcome(filed, "%d attachment(s) filed into %d issue folder(s)."
                .formatted(filed, byIssue.size()));
    }

    private Map<String, List<String>> unfiledByIssue() {
        Map<String, List<String>> byIssue = new LinkedHashMap<>();

        for (Object[] row : entityManager.createQuery(UNFILED_ATTACHMENTS, Object[].class)
                .setParameter("issueOwner", AttachmentOwners.ISSUE)
                .setParameter("directoryOwner", OwnerReference.DIRECTORY)
                .getResultList()) {
            byIssue.computeIfAbsent((String) row[1], issueId -> new ArrayList<>()).add((String) row[0]);
        }

        return byIssue;
    }
}
