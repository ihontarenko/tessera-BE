package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.UpdateIssueScheduleRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * When an issue is meant to happen: the queue date somebody plans around, and the two dates they are
 * held to.
 *
 * <h2>⚠️ Its own service and its own route, beside the fields on the issue</h2>
 *
 * <p>A schedule is written from screens that are not editing the issue — a badge on a board card, a row
 * on a backlog, a client pushing three tickets to today — and every one of those would otherwise have to
 * send the summary, the description, the priority and the assignee back unchanged to move one date. That
 * is the trap the assignee already works around, and one more field on the general update would make it
 * three fields wider.
 *
 * <p>The permission is still {@code EDIT_ISSUE}. Saying when work happens is editing the issue.
 */
@Service
@RequiredArgsConstructor
public class IssueScheduleService {

    static final String FIELD_QUEUED_FOR = "queuedFor";
    static final String FIELD_RED_LINE   = "redLine";
    static final String FIELD_DEADLINE   = "deadline";

    private final IssueRepository    issueRepository;
    private final ProjectService     projectService;
    private final MemberService      memberService;
    private final ActivityLogService activityLogService;
    private final IssueAssembler     issueAssembler;

    @Transactional
    public IssueResponse update(Jwt jwt, String issueId, UpdateIssueScheduleRequest request) {
        return update(memberService.resolveMember(jwt), issueId, request);
    }

    /** The same, for a caller that is not an HTTP request — the protocol's schedule action. */
    @Transactional
    public IssueResponse update(Member caller, String issueId, UpdateIssueScheduleRequest request) {
        Issue   issue   = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());

        requireOrderedLimits(request.redLine(), request.deadline());

        ActivityLogService.ChangeSet changes = activityLogService.changeSet()
            .compare(FIELD_QUEUED_FOR, written(issue.getQueuedFor()), written(request.queuedFor()))
            .compare(FIELD_RED_LINE, written(issue.getRedLine()), written(request.redLine()))
            .compare(FIELD_DEADLINE, written(issue.getDeadline()), written(request.deadline()));

        issue.setQueuedFor(request.queuedFor());
        issue.setRedLine(request.redLine());
        issue.setDeadline(request.deadline());

        activityLogService.record(issue.getId(), caller.getId(), changes);

        return issueAssembler.detail(issue, project, caller);
    }

    /**
     * Take an issue out of the queue, leaving what it is held to alone.
     *
     * <p>⚠️ <strong>The queue date is a plan and the other two are commitments</strong>, which is why
     * finishing an issue touches only this one. Nothing is picked up twice, so a queue date on completed
     * work is noise on every listing that reads one — while what the deadline <em>was</em> is worth
     * reading beside when the work actually landed, and erasing it would delete the only evidence of
     * whether it was met.
     *
     * <p>⚠️ No activity entry and no caller, deliberately. This is a consequence of the transition rather
     * than an act of its own, and the transition is already in the log a line above; a second entry
     * saying the queue date was cleared would read as somebody having done it.
     */
    public void clearQueue(Issue issue) {
        issue.setQueuedFor(null);
    }

    /**
     * ⚠️ <strong>A red line after the deadline is not a warning, it is a contradiction.</strong> The
     * point of the earlier date is that it falls due first; set behind the commitment it can never fire
     * before the issue is already overdue, so the field would be present, editable and incapable of
     * meaning anything. Refused with the two dates named, because a message that only says "invalid"
     * leaves somebody guessing which of the two they got wrong.
     */
    private void requireOrderedLimits(LocalDate redLine, LocalDate deadline) {
        if (redLine == null || deadline == null || !redLine.isAfter(deadline)) {
            return;
        }

        throw new BusinessRuleViolationException(
            "The red line (" + written(redLine) + ") falls after the deadline (" + written(deadline)
            + "). A red line is the warning before the commitment, so it has to come first — move it "
            + "earlier, or push the deadline back.");
    }

    /** How a date reads in the activity log: the way it is written down, or nothing at all. */
    private String written(LocalDate date) {
        return date == null ? null : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

}
