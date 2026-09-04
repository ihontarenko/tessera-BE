package net.innoventa.tessera.security.access.target;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import net.innoventa.tessera.security.access.Targets;
import net.innoventa.tessera.service.file.AttachmentOwners;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.spi.AccessTargetResolver;
import org.jmouse.files.jpa.ManagedFile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where an attachment lives, in the only terms Tessera authorizes in: a project.
 *
 * <h2>⚠️ Two hops, and the second is the point</h2>
 *
 * <p>An attachment is filed against an <strong>issue</strong>, and Tessera grants nothing at an issue —
 * its places are projects. So a file's place is its issue's project, <em>derived</em> rather than stored:
 * an issue that moves between projects takes its attachments' authorization with it, and no row has to be
 * updated for that to stay true.</p>
 *
 * <p>⚠️ <strong>Tessera needs no {@code DIRECTORY} scope for this</strong>, unlike Kiwi. A file's place is
 * already answerable in the vocabulary this product has, which is why adopting the library here cost no
 * new scope, no new hierarchy and no policy change.</p>
 *
 * <p>The <strong>owner is the uploader</strong>, so a permission held at {@code SELF} still means what it
 * says about somebody's own attachments — the same reading as {@link IssueAccessTargetResolver}, where
 * the owner is whoever raised the issue rather than whoever it currently points at.</p>
 *
 * <p>⚠️ A file bound to no issue resolves to <strong>nothing</strong>, which the engine reads as
 * <em>no such row</em>. That is the only correct answer: an unscoped target would pass every question
 * about a place, and a managed file with no attachment binding is not a Tessera attachment at all.</p>
 */
@Component
public class ManagedFileAccessTargetResolver implements AccessTargetResolver<ManagedFile> {

    /**
     * One query for the whole page — the binding names the issue, the issue names the project and the
     * file names its uploader. Resolving these one at a time would put three round trips per card on the
     * security path of an issue screen.
     */
    private static final String ATTACHMENT_PLACES = """
            SELECT binding.fileId, issue.projectId, file.uploadedBy
            FROM FileBinding binding, Issue issue, ManagedFile file
            WHERE binding.ownerType = :ownerType
              AND binding.fileId IN :fileIds
              AND issue.id = binding.ownerId
              AND file.id = binding.fileId
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * ⚠️ The word a policy writes for this type, declared HERE rather than on the class.
     * A library type cannot carry the annotation — `jmouse-files` has no dependency on
     * `jmouse-access` and must not grow one — and declaring every resolver's name the same way keeps
     * one rule instead of two. Same words as Innoventa's and Kiwi's: one type, one spelling.
     */
    @Override
    public String resourceName() {
        return "file";
    }

    @Override
    public Class<ManagedFile> resourceType() {
        return ManagedFile.class;
    }

    @Override
    public Optional<AccessTarget> resolve(String fileId) {
        return Optional.ofNullable(resolveAll(List.of(fileId)).get(fileId));
    }

    @Override
    public Map<String, AccessTarget> resolveAll(List<String> fileIds) {
        List<String> wanted = fileIds.stream().filter(fileId -> fileId != null && !fileId.isBlank())
                .distinct().toList();

        if (wanted.isEmpty()) {
            return Map.of();
        }

        Map<String, AccessTarget> targets = new HashMap<>();

        for (Object[] row : entityManager.createQuery(ATTACHMENT_PLACES, Object[].class)
                .setParameter("ownerType", AttachmentOwners.ISSUE)
                .setParameter("fileIds", wanted)
                .getResultList()) {
            targets.put((String) row[0], Targets.ownedBy((String) row[1], (String) row[2]));
        }

        return targets;
    }
}
