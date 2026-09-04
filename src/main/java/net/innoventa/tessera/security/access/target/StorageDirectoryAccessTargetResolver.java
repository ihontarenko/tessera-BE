package net.innoventa.tessera.security.access.target;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.security.access.Targets;
import net.innoventa.tessera.service.file.FileTrees;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.spi.AccessTargetResolver;
import org.jmouse.files.OwnerReference;
import org.jmouse.files.directory.DirectoryPath;
import org.jmouse.files.exception.DirectoryException;
import org.jmouse.files.exception.FileBindingException;
import org.jmouse.files.jpa.directory.StorageDirectories;
import org.jmouse.files.jpa.directory.StorageDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * What a folder <em>is</em>, in the only vocabulary Tessera authorizes in.
 *
 * <h2>⚠️ There is no DIRECTORY scope here, and adding one was the wrong answer</h2>
 *
 * <p>Kiwi holds grants at a folder and has a scope for it. Tessera has three scopes — {@code GLOBAL},
 * {@code PROJECT}, {@code SELF} — and a fourth would mean the scope catalogue, {@code TesseraScope}, the
 * policy document, the access screen and every grant already written, in exchange for folders whose
 * place this product can already name. So a directory is <em>translated</em> instead:</p>
 *
 * <table>
 *   <tr><th>Where it sits</th><th>What it is</th></tr>
 *   <tr><td>{@code …/issues/<PROJECT>/…}</td><td>that project — read it exactly as you read the issue</td></tr>
 *   <tr><td>{@code …/ai/…}</td><td>the installation, no place — so only a {@code GLOBAL} grant satisfies</td></tr>
 *   <tr><td>a member's own tree</td><td>that member as the owner, no place — the {@code SELF} reading</td></tr>
 * </table>
 *
 * <h2>⚠️ Reading the project out of a PATH is safe here and would not be anywhere else</h2>
 *
 * <p>A path is normally a label, and gating on a label means a rename moves who may read what. It is
 * load-bearing here only because <strong>the issues branch is machine-made from end to end</strong>: its
 * folders are minted from an issue's keys as attachments arrive, and no route offers to rename one.
 * If that ever stops being true, this class becomes a disclosure — so a rename inside the branch must be
 * refused, not merely discouraged.</p>
 *
 * <p>The alternative was a Tessera-owned table mapping folder to project. It buys immunity from renames
 * that cannot happen, and costs a row to keep in step with a tree the product does not own — a second
 * source of truth about a fact the first one already states.</p>
 *
 * <h2>⚠️ Unknown resolves to EMPTY, never to an unscoped target</h2>
 *
 * <p>An unscoped target passes every question about a place, so a folder nobody can find would be
 * readable by whoever holds the permission anywhere. Empty reads as <em>no such row</em>, which is the
 * truth about it. The only thing that may answer with a placeless target is the assistant branch, and
 * that is deliberate: it has no project, so the permission has to be held globally to reach it.</p>
 */
@Component
@RequiredArgsConstructor
public class StorageDirectoryAccessTargetResolver implements AccessTargetResolver<StorageDirectory> {

    private final StorageDirectories directories;
    private final ProjectRepository  projectRepository;

    /**
     * ⚠️ The word a policy writes for this type, declared HERE rather than on the class.
     * A library type cannot carry the annotation — `jmouse-files` has no dependency on
     * `jmouse-access` and must not grow one — and declaring every resolver's name the same way keeps
     * one rule instead of two. Same words as Innoventa's and Kiwi's: one type, one spelling.
     */
    @Override
    public String resourceName() {
        return "directory";
    }

    @Override
    public Class<StorageDirectory> resourceType() {
        return StorageDirectory.class;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessTarget> resolve(String directoryId) {
        if (directoryId == null || directoryId.isBlank()) {
            return Optional.empty();
        }

        try {
            // ⚠️ Read rather than trusted. The identifier arrives from the caller, and answering about a
            // folder without looking it up is answering about a folder that may not exist.
            return placeOf(directories.require(directoryId));
        } catch (DirectoryException missing) {
            return Optional.empty();
        }
    }

    /**
     * Where a folder is, as a target.
     *
     * <p>Also the answer for {@code owner=DIRECTORY:<id>} — see
     * {@link AttachmentOwnerAccessTargetResolver}, which delegates here rather than asking the same
     * question in a second way.</p>
     *
     * @param directory the folder
     * @return its target, or empty when it is in no tree this product knows
     */
    Optional<AccessTarget> placeOf(StorageDirectory directory) {
        DirectoryPath path = DirectoryPath.of(directory.getPath());

        if (!FileTrees.inAttachments(path)) {
            return cabinetOwnerOf(directory);
        }

        Optional<Project> project = FileTrees.projectKeyOf(path)
                .flatMap(projectRepository::findByKeyIgnoreCase);

        if (project.isPresent()) {
            return project.map(found -> Targets.project(found.getId()));
        }

        // The assistant's branch, and the two shelves above the projects — `issues` and the attachments
        // root itself. None of them is in a project, and none of them is anybody's, so the permission has
        // to be held at GLOBAL to reach them. ⚠️ That includes the root, which is what the Files screen
        // asks for first: a member with project:browse on their own projects sees the tree from the
        // project folders down, and not the shelf they sit on.
        if (FileTrees.isAssistantBranch(path) || isShelf(path)) {
            return Optional.of(AccessTarget.installation());
        }

        // Inside the issues branch but naming a project that no longer exists — a folder left behind by a
        // deleted project. Nothing may reach it, which is the safe direction and also the honest one.
        return Optional.empty();
    }

    /**
     * A member's own tree, read off the tree's owner rather than off the folder.
     *
     * <p>⚠️ <strong>The owner is the tree, not the folder.</strong> A folder six levels down in somebody's
     * cabinet answers the same as its root, which is what makes "your own files" mean the whole cabinet
     * rather than only its top.</p>
     */
    private Optional<AccessTarget> cabinetOwnerOf(StorageDirectory directory) {
        OwnerReference owner;

        try {
            owner = OwnerReference.parse(directory.getOwnerKey());
        } catch (FileBindingException notAnOwner) {
            // The installation sentinel is a bare `*` and parses as nothing — correct, because a tree
            // that is the installation's but is not the attachments tree is a tree this product did not
            // make and cannot place.
            return Optional.empty();
        }

        if (!FileTrees.OWNER_MEMBER.equals(owner.ownerType())) {
            return Optional.empty();
        }

        return Optional.of(AccessTarget.installation().withOwner(owner.ownerId()));
    }

    /**
     * Whether a folder is one of the two the projects sit on rather than one inside a project.
     *
     * <p>{@code tessera/attachments} and {@code tessera/attachments/issues}.</p>
     */
    private boolean isShelf(DirectoryPath path) {
        return path.segments().size() <= DirectoryPath.ROOT_DEPTH + 1;
    }
}
