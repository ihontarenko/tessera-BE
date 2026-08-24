package net.innoventa.tessera.service.file;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.security.Roles;
import net.innoventa.tessera.security.access.Targets;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.files.directory.DirectoryPath;
import org.jmouse.files.jpa.directory.StorageDirectories;
import org.jmouse.files.jpa.directory.StorageDirectory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Every member's own file cabinet — the second tree, the one that is theirs to arrange.
 *
 * <h2>⚠️ A root per member, not a branch of a shared one</h2>
 *
 * <p>{@code storage_directories} is keyed {@code (owner_key, path)}, so every member's cabinet is a
 * <em>root</em> at the same path {@link FileTrees#LIBRARY_ROOT} under a different owner. Innoventa's
 * file cabinet is the identical shape, and the reason is authorization rather than tidiness: a personal
 * folder that were a branch of the installation's tree would need a grant written at it before anybody
 * could put anything in it, per person, for ever. As a root of their own it needs none — the folder's
 * owner <em>is</em> the answer, on the axis that already exists.</p>
 *
 * <h2>⚠️ Made when the member is, never on the hot path</h2>
 *
 * <p>{@code MemberService.resolveMember} runs on every authenticated request, and only its
 * <em>provisioning</em> branch calls this. Calling it on every resolve would be an extra indexed read
 * per request to discover, almost always, that nothing needed doing. Whoever predates this code is
 * caught by {@link FileRootSeedStep} instead — the two together cover everybody, once each.</p>
 */
@Component
@RequiredArgsConstructor
public class MemberFileTrees {

    private final StorageDirectories   directories;
    private final MemberRepository     memberRepository;
    private final AccessAdministration access;

    /**
     * This member's cabinet, made if it is not there yet, and theirs to open.
     *
     * @param member whose
     * @return their root
     */
    public StorageDirectory cabinetOf(Member member) {
        StorageDirectory cabinet = rootOf(member);

        // ⚠️ The folder and the grant are one act, and neither is any use alone: a cabinet nobody holds
        // `file:read @SELF` over is a folder its owner cannot open, and a grant over a folder that does
        // not exist matches nothing for ever. `assign` is idempotent and answers whether it did anything,
        // so calling it beside every requireRoot is the cheap way to keep the two in step.
        access.assign(member.getId(), Roles.MEMBER_CABINET, Targets.selfScope(), "BOOTSTRAP",
                      "member-file-trees", null);

        return cabinet;
    }

    /**
     * ⚠️ <strong>The catch is the point of this method.</strong> {@code requireRoot} reads and then
     * inserts, and nothing between the two stops another request doing the same — so the loser of that
     * race sees a unique-constraint violation, which means precisely that the cabinet it wanted now
     * exists. Re-reading is the answer; locking would make provisioning wait on a row written once in a
     * member's lifetime.
     */
    private StorageDirectory rootOf(Member member) {
        try {
            return directories.requireRoot(FileTrees.member(member.getId()), FileTrees.LIBRARY_ROOT);
        } catch (DataIntegrityViolationException lostTheRace) {
            return directories.requireRoot(FileTrees.member(member.getId()), FileTrees.LIBRARY_ROOT);
        }
    }

    /**
     * Cabinets for every member who already existed before they were a thing.
     *
     * <p>⚠️ Called once, from the bootstrap ledger. It is a loop over the members table, which is what
     * makes it a recorded step rather than something the application does at every start.</p>
     *
     * @return how many were made
     */
    public int provisionExisting() {
        DirectoryPath library = DirectoryPath.of(FileTrees.LIBRARY_ROOT);
        int           made    = 0;

        for (Member member : memberRepository.findAll()) {
            // ⚠️ Asked before making, so the count is honest. requireRoot alone is idempotent and would
            // report the whole members table as "made" on a second run — a summary in the ledger that
            // says nine cabinets were created when none were is the sort of line somebody later builds
            // a wrong conclusion on.
            // ⚠️ Whoever already has a cabinet is left alone, grant included. This step backfills people
            // who predate the feature; re-asserting the grant for everybody would hand it back to anybody
            // an administrator had deliberately taken it from, which is the one thing a grant on a screen
            // must not do.
            if (directories.find(FileTrees.member(member.getId()).toString(), library).isEmpty()) {
                cabinetOf(member);
                made++;
            }
        }

        return made;
    }
}
