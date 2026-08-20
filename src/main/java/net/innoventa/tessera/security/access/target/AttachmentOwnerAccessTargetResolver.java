package net.innoventa.tessera.security.access.target;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import net.innoventa.tessera.security.access.Targets;
import net.innoventa.tessera.service.file.AttachmentOwners;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.spi.AccessTargetResolver;
import org.jmouse.files.exception.FileBindingException;
import org.jmouse.files.OwnerReference;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Where the thing a file is <em>about to be</em> filed against lives.
 *
 * <h2>⚠️ Listing and uploading name an owner, not a file</h2>
 *
 * <p>{@link ManagedFileAccessTargetResolver} answers about a file that exists. Listing an issue's
 * attachments and uploading a new one cannot: the first has no file yet in the general case and the
 * second has none by definition. Both name the <strong>owner</strong> instead — {@code ISSUE:<id>} in a
 * request parameter — so the engine needs this second reading of the same question.</p>
 *
 * <p>⚠️ <strong>An owner of any other kind resolves to nothing</strong>, deliberately. Tessera files
 * attachments against issues and against nothing else; a request naming {@code DIRECTORY:x} is refused as
 * <em>no such thing</em> rather than falling through to a target that would pass every question about a
 * place. That refusal is also what keeps the library's tree out of this product by accident as well as by
 * configuration.</p>
 */
@Component
public class AttachmentOwnerAccessTargetResolver implements AccessTargetResolver<OwnerReference> {

    private static final String ISSUE_PLACE =
            "SELECT issue.projectId, issue.reporterMemberId FROM Issue issue WHERE issue.id = :issueId";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Class<OwnerReference> resourceType() {
        return OwnerReference.class;
    }

    @Override
    public Optional<AccessTarget> resolve(String owner) {
        if (owner == null || owner.isBlank()) {
            return Optional.empty();
        }

        OwnerReference reference;

        try {
            reference = OwnerReference.parse(owner);
        } catch (FileBindingException malformed) {
            // ⚠️ Caught rather than propagated: this runs on the authorization path, where an exception
            // becomes a 500 for what is plainly a bad request. Empty is the honest answer — an owner
            // nobody can parse names nothing — and the engine turns it into the same refusal an unknown
            // issue gets, which is what a caller should see either way.
            return Optional.empty();
        }

        if (!AttachmentOwners.ISSUE.equals(reference.ownerType())) {
            return Optional.empty();
        }

        return entityManager.createQuery(ISSUE_PLACE, Object[].class)
                .setParameter("issueId", reference.ownerId())
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .map(row -> Targets.ownedBy((String) row[0], (String) row[1]));
    }
}
