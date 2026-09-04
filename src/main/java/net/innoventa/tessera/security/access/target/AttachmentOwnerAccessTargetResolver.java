package net.innoventa.tessera.security.access.target;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import net.innoventa.tessera.security.access.Targets;
import net.innoventa.tessera.service.file.AttachmentOwners;
import net.innoventa.tessera.service.file.FileTrees;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.spi.AccessTargetResolver;
import org.jmouse.files.exception.FileBindingException;
import org.jmouse.files.OwnerReference;
import org.jmouse.files.jpa.directory.StorageDirectory;
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
 * <h2>⚠️ {@code owner} means TWO different things, and that is why there are four cases</h2>
 *
 * <p>This class used to refuse everything that was not an {@code ISSUE}, and said so — that the refusal
 * was what kept the library's tree out of this product by accident as well as by configuration. The tree
 * is switched on since TSSR-0103, and with it the parameter carries two questions rather than one:
 * <em>what is this file filed against</em> on the file routes, and <em>whose tree is being listed</em> on
 * the roots route. Both name the parameter {@code owner}, so both arrive here.</p>
 *
 * <ul>
 *   <li>{@code ISSUE:<id>} — the issue's project, as it always did;</li>
 *   <li>{@code DIRECTORY:<id>} — whatever that folder is, asked of
 *       {@link StorageDirectoryAccessTargetResolver} rather than worked out a second way here. Listing a
 *       folder is the Files screen's every call;</li>
 *   <li>{@code MEMBER:<id>} — <strong>a tree's owner, not a file's.</strong> Somebody's own cabinet,
 *       answered as an owner with no place: the {@code @SELF} reading. ⚠️ Missing this case does not
 *       look like a refusal — the roots call 404s and the screen draws an empty tree, which reads as
 *       <em>you have no folders</em>;</li>
 *   <li>⚠️ {@code *} — the library's <strong>installation sentinel</strong>, which is
 *       {@code DirectoryController.roots}'s default and is <em>not</em> a {@code KIND:id} pair. It has to
 *       be answered explicitly: a product whose resolver only parses owner references answers nothing for
 *       it, and the whole tree is then refused on the screen's first call (JMF-48). Answering it is a
 *       decision rather than a formality — it says this installation HAS a tree of its own and that
 *       listing its roots is a question about the installation, so a {@code GLOBAL} grant is what
 *       reaches it.</li>
 * </ul>
 *
 * <p>⚠️ Anything else still resolves to nothing rather than to an unscoped target, which would pass every
 * question about a place.</p>
 */
@Component
public class AttachmentOwnerAccessTargetResolver implements AccessTargetResolver<OwnerReference> {

    private static final String ISSUE_PLACE =
            "SELECT issue.projectId, issue.reporterMemberId FROM Issue issue WHERE issue.id = :issueId";

    @PersistenceContext
    private EntityManager entityManager;

    /** Where a folder is — one question, asked of the one class that answers it. */
    private final StorageDirectoryAccessTargetResolver directoryTargets;

    public AttachmentOwnerAccessTargetResolver(StorageDirectoryAccessTargetResolver directoryTargets) {
        this.directoryTargets = directoryTargets;
    }

    /**
     * ⚠️ The word a policy writes for this type, declared HERE rather than on the class.
     * A library type cannot carry the annotation — `jmouse-files` has no dependency on
     * `jmouse-access` and must not grow one — and declaring every resolver's name the same way keeps
     * one rule instead of two. Same words as Innoventa's and Kiwi's: one type, one spelling.
     */
    @Override
    public String resourceName() {
        return "file_owner";
    }

    @Override
    public Class<OwnerReference> resourceType() {
        return OwnerReference.class;
    }

    @Override
    public Optional<AccessTarget> resolve(String owner) {
        if (owner == null || owner.isBlank()) {
            return Optional.empty();
        }

        // ⚠️ Before parsing, because it does not parse. The sentinel is a bare asterisk by design — there
        // is nothing to identify — and it means the tree that belongs to the installation rather than to
        // anybody in it.
        if (StorageDirectory.INSTALLATION.equals(owner.trim())) {
            return Optional.of(AccessTarget.installation());
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

        if (OwnerReference.DIRECTORY.equals(reference.ownerType())) {
            // Asked rather than answered here: a folder's place is one question with one answer, and two
            // classes working it out independently is how the two come to disagree.
            return directoryTargets.resolve(reference.ownerId());
        }

        // ⚠️ A TREE's owner, not a file's, and the difference is why this case is easy to miss. `owner`
        // means two different things on two routes: what a file is filed against (`ISSUE:`, `DIRECTORY:`)
        // and WHOSE TREE is being listed (`MEMBER:`, `*`). Both arrive at this one resolver because both
        // routes name the same parameter, and without this branch a member asking for their own cabinet
        // is refused as "no such thing" — which the interface draws as an empty tree, not as a refusal.
        if (FileTrees.OWNER_MEMBER.equals(reference.ownerType())) {
            return Optional.of(AccessTarget.installation().withOwner(reference.ownerId()));
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
