package net.innoventa.tessera.service.file;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.bootstrap.BootstrapStep;
import org.jmouse.files.jpa.directory.StorageDirectories;
import org.jmouse.files.jpa.directory.StorageDirectory;
import org.springframework.stereotype.Component;

/**
 * Makes {@code tessera/attachments} exist at the first start, rather than at the first upload.
 *
 * <h2>⚠️ Why a fresh installation cannot be left to make it lazily</h2>
 *
 * <p>Kiwi wrote this down after meeting it: a root made on the way through an upload is a closed loop.
 * Uploading needs a folder; a grant has to name a folder that exists; and the only thing that made one
 * was an upload. Every screen works, the folder picker is empty, and the empty state reads
 * <em>there are no folders</em> when the truth is <em>nothing has ever created one</em>.</p>
 *
 * <p>Tessera is one step further from that trap than Kiwi was — its attachment folders are minted per
 * issue as files arrive — but the <strong>root</strong> is still what the Files screen asks for first,
 * and a screen that answers "no folders" on a new installation is the same lie.</p>
 *
 * <h2>⚠️ The personal trees are NOT seeded here, and cannot be</h2>
 *
 * <p>{@link FileTrees#LIBRARY_ROOT} is one root <em>per member</em>, keyed by {@code owner_key}. A
 * startup loop over every member would be a migration that runs on every boot and grows with the
 * installation. So a member's own cabinet is made when the member is — see {@code MemberService} — and
 * this step makes it for whoever already existed before that code did.</p>
 *
 * <h2>⚠️ A step rather than a runner, and the checksum is the path</h2>
 *
 * <p>{@code requireRoot} is idempotent and this could have been three lines in an
 * {@code ApplicationRunner}. The ledger earns the class: <em>was this installation's root made by us,
 * and when</em> is what somebody asks the day a path looks wrong, and a runner leaves nothing behind to
 * answer with.</p>
 *
 * <p>Folding the path into the checksum means that changing it re-runs this step — which creates the
 * <strong>new</strong> root beside the old one rather than moving anything. A root cannot move: its path
 * is the storage namespace of every key already written beneath it. Visible and recoverable, instead of
 * silent.</p>
 */
@Component
@RequiredArgsConstructor
public class FileRootSeedStep implements BootstrapStep {

    private final StorageDirectories directories;
    private final MemberFileTrees    memberTrees;

    @Override
    public String key() {
        return "files:roots";
    }

    @Override
    public String checksum() {
        return FileTrees.ATTACHMENTS_ROOT + "|" + FileTrees.LIBRARY_ROOT;
    }

    @Override
    public String note() {
        return "Tessera's file roots — the attachments tree, and a cabinet for every member who "
               + "predates having one.";
    }

    @Override
    public Outcome apply() {
        StorageDirectory attachments = directories.requireRoot(FileTrees.ATTACHMENTS_ROOT);
        int              cabinets    = memberTrees.provisionExisting();

        return new Outcome(1 + cabinets, "Root '%s' is %s; %d member cabinet(s) made."
                .formatted(attachments.getPath(), attachments.getId(), cabinets));
    }
}
