package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Putting finished work away, and taking it back out (TSSR-4).
 *
 * <p><strong>Archived is a state, not a screen.</strong> One flag on the issue removes it from the
 * board, the backlog and the project's issue list at once — every one of those reads through
 * {@code findByProjectIdAndArchivedAtIsNullOrderByRankAsc}, so there is one rule rather than a hiding
 * option per screen. Search still finds it, and the Shipped screen still lists it: this is a filing
 * cabinet, not a wastebasket, and {@link IssueService#delete} remains the only thing that destroys
 * anything.
 *
 * <p><strong>The one invariant: only a closed issue can be archived.</strong> Archiving open work would
 * make it invisible while somebody is still expected to do it, which is the failure mode an archive is
 * supposed to prevent rather than cause. The refusal says which issue and what state it is in, because
 * the fix — resolve it first, as {@code Won't Do} if that is the truth — is a decision the caller has to
 * make rather than one this service can make for them.
 *
 * <p>The reverse direction has no such rule: un-archiving is always allowed, and reopening an archived
 * issue un-archives it on its own ({@link TransitionService}). Nothing can therefore end up open and
 * invisible at the same time.
 */
@Service
@RequiredArgsConstructor
public class IssueArchiveService {

    /** The history field name — the same string the Activity tab renders as a row. */
    static final String FIELD_ARCHIVED = "archived";

    private final IssueRepository issueRepository;
    private final ProjectService projectService;
    private final MemberService memberService;
    private final ActivityLogService activityLogService;
    private final IssueAssembler issueAssembler;
    private final StatusRepository statusRepository;

    @Transactional
    public IssueResponse archive(Jwt jwt, String issueId) {
        return archive(memberService.resolveMember(jwt), issueId);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    @Transactional
    public IssueResponse archive(Member caller, String issueId) {
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());

        if (issue.getResolutionId() == null) {
            throw new BusinessRuleViolationException(
                "Only finished work can be archived: " + issue.getIssueKey() + " is still open. Resolve it first.");
        }

        // Idempotent on purpose: archiving is a state somebody wants the issue to be in, not an event
        // they want to happen twice. Two clicks on a stale list should not read as a second change in
        // the history, and there is nothing for the second one to do.
        if (issue.getArchivedAt() != null) {
            return issueAssembler.detail(issue, project, caller);
        }

        issue.setArchivedAt(LocalDateTime.now());
        issue.setArchivedByMemberId(caller.getId());

        activityLogService.record(issue.getId(), caller.getId(),
            activityLogService.changeSet().added(FIELD_ARCHIVED, issue.getIssueKey()));

        return issueAssembler.detail(issue, project, caller);
    }

    /**
     * Files everything finished in one project at once.
     *
     * <h2>⚠️ Why this exists rather than the caller looping</h2>
     *
     * <p>Because the caller looping is what actually happened: clearing six projects after a long batch
     * took seventy-three separate archive calls, each resolving the same project and writing the same
     * kind of history row. A protocol exists to take exactly that shape of work.
     *
     * <h2>⚠️ It archives by RESOLUTION, not by status name</h2>
     *
     * <p>A status is a row on a configuration screen — renamed, reordered, duplicated per workflow — so
     * "archive everything in Done" is a rule that stops being true the day somebody adds *Shipped*
     * beside it. A resolution is the thing that actually means <em>this is finished</em>, and it is what
     * single-issue {@link #archive} already refuses without. Callers naming a status are answered by
     * resolving the status to its issues, so the two never disagree about what "finished" means.
     *
     * <p>⚠️ <strong>Unresolved issues are skipped, never refused.</strong> A bulk act that failed on the
     * first open issue would be unusable in exactly the situation it is for — a project that is mostly
     * finished. The answer says how many were skipped, so nothing is silently left behind.
     *
     * @param statusName optional; when given, only issues in that status are considered — and they must
     *                   still be resolved to be filed
     * @return the keys that were filed, in the order they were found
     */
    @Transactional
    public ArchivedBatch archiveCompleted(Member caller, String projectId, String statusName) {
        Project project = projectService.requireProject(projectId);

        List<Issue> candidates = issueRepository.findByProjectIdAndArchivedAtIsNullOrderByRankAsc(projectId).stream()
            .filter(issue -> statusName == null || matchesStatus(issue, statusName))
            .toList();

        List<String> filed   = new ArrayList<>();
        int          skipped = 0;

        for (Issue issue : candidates) {
            if (issue.getResolutionId() == null) {
                skipped++;
                continue;
            }

            issue.setArchivedAt(LocalDateTime.now());
            issue.setArchivedByMemberId(caller.getId());

            activityLogService.record(issue.getId(), caller.getId(),
                activityLogService.changeSet().added(FIELD_ARCHIVED, issue.getIssueKey()));

            filed.add(issue.getIssueKey());
        }

        return new ArchivedBatch(project.getKey(), filed, skipped);
    }

    /**
     * What a bulk filing did.
     *
     * @param skipped how many were left alone because they carry no resolution — said out loud, because
     *                "archive everything finished" quietly leaving work behind is the one way this can
     *                mislead
     */
    public record ArchivedBatch(String projectKey, List<String> archived, int skipped) {
    }

    private boolean matchesStatus(Issue issue, String statusName) {
        return statusRepository.findById(issue.getStatusId())
            .map(status -> status.getName().equalsIgnoreCase(statusName))
            .orElse(false);
    }

    @Transactional
    public IssueResponse unarchive(Jwt jwt, String issueId) {
        return unarchive(memberService.resolveMember(jwt), issueId);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    @Transactional
    public IssueResponse unarchive(Member caller, String issueId) {
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());

        if (issue.getArchivedAt() == null) {
            return issueAssembler.detail(issue, project, caller);
        }

        clear(issue);

        activityLogService.record(issue.getId(), caller.getId(),
            activityLogService.changeSet().removed(FIELD_ARCHIVED, issue.getIssueKey()));

        return issueAssembler.detail(issue, project, caller);
    }

    /**
     * Take an issue back out of the archive without saying who or writing history — what a reopening
     * transition does as a side effect of leaving a Done status.
     *
     * <p>It is not logged there because the caller is already recording the change that caused it: a
     * history reading <em>status: Done → In Progress</em> and, beside it, <em>archived: removed</em>
     * would be two entries for one act, the second of which nobody performed.
     */
    void clear(Issue issue) {
        issue.setArchivedAt(null);
        issue.setArchivedByMemberId(null);
    }

    private Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

}
