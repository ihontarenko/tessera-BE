package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.project.RekeyProjectRequest;
import net.innoventa.tessera.dto.project.RekeyProjectResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Changing a project's key, and every issue key with it.
 *
 * <h2>⚠️ The one edit that changes an identifier other people are holding</h2>
 *
 * <p>Everything else on the settings screen changes what this installation does next. This changes what
 * a link written last year points at. {@code TSSR-42} lives in browser bookmarks, in WiQi pages, in
 * Innoventa pages, in commit messages, in `.tessera/` mirror files and in whatever anybody pasted into
 * a chat — none of which this service can reach, and none of which it pretends to. It rewrites what is
 * in this database and the screen says plainly what it has just broken.
 *
 * <h2>⚠️ A prefix is SWAPPED, never re-rendered</h2>
 *
 * <p>The obvious implementation — run each issue's sequence back through its key strategy with the new
 * project key — is wrong, and wrong in a way that only shows up on the projects least able to notice.
 * {@code PatternIssueKeyStrategy} renders date tokens from <em>now</em>, so a project on
 * {@code DATE_PREFIXED} would come out of a rekey with every issue restamped to this year:
 * {@code OPS-2024-7} becomes {@code TRK-2026-7}, and the year an issue was raised is gone from the only
 * place it was written down.
 *
 * <p>So the rewrite is textual and minimal: {@code ${key}} is a plain substitution, so where a pattern
 * opens with it the project key <em>is</em> the first characters of every key it minted. Swap those and
 * nothing else can have moved.
 *
 * <h2>⚠️ Each key is judged on itself, never on the project's current pattern</h2>
 *
 * <p>The tempting shortcut is to read {@code keyStrategy} once and decide from the pattern. It is
 * wrong, because <strong>a project's keys are not all minted the same way</strong>: the format is
 * editable and existing keys are never regenerated (the settings screen says so), so a project that was
 * {@code PREFIXED_SEQUENCE} and is now {@code DATE_PREFIXED} holds both shapes. A rule read from the
 * current pattern would then be applied to keys the current pattern never produced.
 *
 * <p>So every key answers for itself, and there are three answers:
 *
 * <ul>
 *   <li><strong>It starts with the old key</strong> — every shipped format, and nearly every custom
 *       one. Prefix swap.
 *   <li><strong>It does not contain the old key at all</strong> — legal; only the sequence is
 *       compulsory in a pattern. There is nothing to rewrite and it is left exactly as it is.
 *   <li><strong>It contains the old key somewhere further in</strong> — the whole rekey is refused. The
 *       offset differs from key to key (a year is four characters, a padded sequence is however many
 *       the slot said), and a rekey that guessed would corrupt keys rather than fail.
 * </ul>
 *
 * <p>Reading the stored key rather than the pattern also means a project running a genuinely
 * algorithmic strategy — a {@code @Component} rather than one of the shipped formats, which ADR-0003
 * allows — needs no special case. Whatever minted the key, the key is still text.
 *
 * <h2>The sequence is never touched</h2>
 *
 * <p>ADR-0003 stored the raw number beside the string for exactly this day, and the counter is not
 * reset. {@code TSSR-42} becomes {@code TRK-42}: the same issue, the same position in the project's
 * history, addressed differently.
 */
@Service
@RequiredArgsConstructor
public class ProjectRekeyService {

    private final ProjectRepository projectRepository;
    private final IssueRepository   issueRepository;
    private final ProjectService    projectService;
    private final MemberService     memberService;

    /**
     * Renames the project's key and rewrites every issue key under it.
     *
     * <p>One transaction: a half-rekeyed project — some issues on the new prefix, some on the old — is
     * the one outcome nothing downstream could make sense of.
     */
    @Transactional
    public RekeyProjectResponse rekey(Jwt jwt, String projectId, RekeyProjectRequest request) {
        Member  member      = memberService.resolveMember(jwt);
        Project project     = projectService.requireProject(projectId);
        String  previousKey = project.getKey();
        String  newKey      = request.key();

        requireConfirmation(request.confirmation(), previousKey);

        if (previousKey.equals(newKey)) {
            throw new BusinessRuleViolationException(
                "'" + newKey + "' is already this project's key.");
        }

        if (projectRepository.existsByKey(newKey)) {
            throw new BusinessRuleViolationException("Project key already in use: " + newKey);
        }

        List<Issue> rewritten = rewriteIssueKeys(project, previousKey, newKey);

        project.setKey(newKey);

        return new RekeyProjectResponse(
            projectService.get(member, projectId), previousKey, rewritten.size());
    }

    /**
     * ⚠️ <strong>The current key, typed back, and checked here rather than only in the browser.</strong>
     * A danger zone whose only guard is a dialog is one HTTP call away from not being one.
     */
    private void requireConfirmation(String confirmation, String currentKey) {
        if (!currentKey.equals(confirmation.trim())) {
            throw new BusinessRuleViolationException(
                "To change this project's key, type its current key — " + currentKey + " — to confirm.");
        }
    }

    /**
     * Every issue in the project, on the new prefix.
     *
     * <p>⚠️ <strong>Archived issues are included, and that is not an oversight.</strong> An archived
     * issue is still addressable and still linked to from everywhere a live one is; leaving it behind
     * would give one project two prefixes, which is the state this whole method exists to avoid.
     */
    private List<Issue> rewriteIssueKeys(Project project, String previousKey, String newKey) {
        List<Issue>  issues    = issueRepository.findByProjectIdOrderByRankAsc(project.getId());
        List<Issue>  rewritten = new ArrayList<>(issues.size());
        List<String> minted    = new ArrayList<>(issues.size());

        for (Issue issue : issues) {
            String existing = issue.getIssueKey();

            if (!existing.startsWith(previousKey)) {
                requireKeyIsAbsent(existing, previousKey);
                continue;
            }

            rewritten.add(issue);
            minted.add(newKey + existing.substring(previousKey.length()));
        }

        requireNoCollisions(project.getId(), minted);

        for (int index = 0; index < rewritten.size(); index++) {
            rewritten.get(index).setIssueKey(minted.get(index));
        }

        return rewritten;
    }

    /**
     * ⚠️ <strong>A key holding the old project key anywhere but the front stops the whole rekey.</strong>
     *
     * <p>Leaving it alone would be worse than failing: the project would come out of this with two
     * prefixes in circulation and no screen saying so, which is exactly the state a rekey exists to
     * avoid. The alternative — finding the old key inside and swapping it there — is a guess, because
     * nothing says the first occurrence is the one the pattern put there.
     */
    private void requireKeyIsAbsent(String issueKey, String previousKey) {
        if (issueKey.contains(previousKey)) {
            throw new BusinessRuleViolationException(
                "'" + issueKey + "' carries this project's key somewhere other than the start, so it "
                + "cannot be rewritten safely — where the key sits differs from key to key. Change the "
                + "project's key format so that new keys open with ${key} before rekeying.");
        }
    }

    /**
     * ⚠️ <strong>Checked before anything is written, so the refusal names the key that clashes.</strong>
     * The unique constraint on {@code issue_key} would catch this too, at flush time, as a constraint
     * violation naming an index — which tells whoever pressed the button nothing they can act on.
     *
     * <p>It can only happen through a custom pattern: two projects whose keys are distinct can still
     * mint the same string if one of them writes something the other's prefix happens to produce.
     */
    private void requireNoCollisions(String projectId, List<String> minted) {
        Set<String> unique = new HashSet<>(minted);

        if (unique.size() != minted.size()) {
            throw new BusinessRuleViolationException(
                "That key would give two issues in this project the same key.");
        }

        issueRepository.findByIssueKeyIn(minted).stream()
            .filter(issue -> !issue.getProjectId().equals(projectId))
            .findFirst()
            .ifPresent(clash -> {
                throw new BusinessRuleViolationException(
                    "That key would collide with an issue that already exists: " + clash.getIssueKey() + ".");
            });
    }
}
