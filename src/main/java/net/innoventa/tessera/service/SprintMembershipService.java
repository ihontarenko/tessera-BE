package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintIssue;
import net.innoventa.tessera.repository.SprintIssueRepository;
import net.innoventa.tessera.repository.SprintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * <strong>The</strong> expression of "this issue is committed to an open sprint" — a membership row
 * whose {@code removedAt} is null, in a sprint that is not {@code CLOSED}. Every consumer goes through
 * here: the backlog's exclusion list, the board's narrowed issue source, the move endpoint's
 * permission split and the invariant checks. A second, subtly different copy of this predicate is the
 * primary way these features rot, so there is exactly one.
 * <p>
 * It also owns the write side, because the invariant "an issue holds at most one such membership" is
 * only true if joining always ends whatever came before it — which is one operation
 * ({@link #moveToSprint}), not two the callers must remember to pair. That invariant is enforced here
 * rather than in the schema: it spans two tables, and the partial unique index that could express it
 * exists only in PostgreSQL, which would break the {@code mysql == postgresql} rule every migration obeys.
 * <p>
 * Entering and leaving a sprint is recorded on the issue's history as an ordinary field change, with
 * the sprint names captured as point-in-time display strings (ADR-0007) — so renaming a sprint later
 * does not rewrite history that mentions its old name. A rank-only reorder is deliberately not logged,
 * consistent with the board's rule that reordering is not an issue-field change.
 */
@Service
@RequiredArgsConstructor
public class SprintMembershipService {

    static final String FIELD_SPRINT = "sprint";

    private final SprintRepository sprintRepository;
    private final SprintIssueRepository sprintIssueRepository;
    private final ActivityLogService activityLogService;
    private final Supplier<String> idGenerator;

    // ── The predicate ───────────────────────────────────────────────────────────

    /** A project's sprints that still count as a live commitment — everything but {@code CLOSED}. */
    @Transactional(readOnly = true)
    public List<Sprint> openSprints(String projectId) {
        return sprintRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
            .filter(sprint -> sprint.getState().isOpen())
            .toList();
    }

    /**
     * Every live membership in a project, keyed by issue id — the whole-project answer the backlog and
     * the board both read, in one pass rather than a query per issue. The invariant guarantees at most
     * one row per issue; the merge function is belt-and-braces against corrupt data.
     */
    @Transactional(readOnly = true)
    public Map<String, SprintIssue> liveMembershipsByIssue(String projectId) {
        List<String> openSprintIds = openSprints(projectId).stream().map(Sprint::getId).toList();

        if (openSprintIds.isEmpty()) {
            return Map.of();
        }

        return sprintIssueRepository.findBySprintIdIn(openSprintIds).stream()
            .filter(membership -> membership.getRemovedAt() == null)
            .collect(Collectors.toMap(SprintIssue::getIssueId, Function.identity(), (first, second) -> first));
    }

    /** The one open-sprint membership an issue currently holds, if any. */
    @Transactional(readOnly = true)
    public Optional<SprintIssue> liveMembership(String issueId) {
        return sprintIssueRepository.findByIssueId(issueId).stream()
            .filter(membership -> membership.getRemovedAt() == null)
            .filter(membership -> isOpen(membership.getSprintId()))
            .findFirst();
    }

    /** A sprint's members as they stand right now — the set the board renders and a report starts from. */
    @Transactional(readOnly = true)
    public List<SprintIssue> currentMembers(String sprintId) {
        return sprintIssueRepository.findBySprintId(sprintId).stream()
            .filter(membership -> membership.getRemovedAt() == null)
            .toList();
    }

    // ── The write side ──────────────────────────────────────────────────────────

    /**
     * Move {@code issue}'s commitment to {@code targetSprint}, or back to the product backlog when that
     * is null, and record it on the issue's history. Whatever open membership the issue held is ended
     * first, which is what keeps "at most one live membership" true without a database constraint.
     * <p>
     * Returns {@code false} — changing nothing and logging nothing — when the issue is already exactly
     * where it is being moved to, so a caller may hand this a no-op safely.
     */
    @Transactional
    public boolean moveToSprint(Issue issue, Sprint targetSprint, Member actor) {
        Optional<SprintIssue> current = liveMembership(issue.getId());
        String currentSprintId = current.map(SprintIssue::getSprintId).orElse(null);
        String targetSprintId = targetSprint == null ? null : targetSprint.getId();

        if (Objects.equals(currentSprintId, targetSprintId)) {
            return false;
        }

        String previousName = currentSprintId == null ? null : sprintName(currentSprintId);
        current.ifPresent(membership -> membership.setRemovedAt(LocalDateTime.now()));

        if (targetSprint != null) {
            join(targetSprint, issue, actor);
        }

        activityLogService.record(issue.getId(), actor.getId(), activityLogService.changeSet()
            .changed(FIELD_SPRINT, previousName, targetSprint == null ? null : targetSprint.getName()));

        return true;
    }

    /**
     * Begin (or revive) {@code issue}'s membership of {@code sprint}. Identity is
     * {@code (sprint, issue)}: an issue re-added to a sprint it was removed from reuses that row —
     * removal cleared, and the added timestamp, frozen estimate and acting member all reset to this
     * act — so churn within a single sprint collapses to its latest stint rather than accumulating
     * rows nothing reads.
     */
    private void join(Sprint sprint, Issue issue, Member actor) {
        SprintIssue membership = sprintIssueRepository
            .findBySprintIdAndIssueId(sprint.getId(), issue.getId())
            .orElseGet(() -> SprintIssue.builder()
                .id(idGenerator.get())
                .sprintId(sprint.getId())
                .issueId(issue.getId())
                .build());

        membership.setAddedAt(LocalDateTime.now());
        membership.setRemovedAt(null);
        // The estimate is frozen here and never re-read: a later re-estimate must not rewrite the commitment.
        membership.setStoryPointsAtAdd(issue.getStoryPoints());
        membership.setAddedByMemberId(actor.getId());

        sprintIssueRepository.save(membership);
    }

    /**
     * Carry an unfinished issue out of a sprint that is closing, into {@code destination} or back to the
     * product backlog when that is null.
     * <p>
     * Deliberately <strong>not</strong> {@link #moveToSprint}: the closing sprint's membership row is left
     * exactly as it stands — not deleted, not marked removed — because that row <em>is</em> the sprint
     * report, and a sprint that has ended should never rewrite what happened inside it. Nothing is needed
     * to keep "at most one live membership" true either, since a {@code CLOSED} sprint's rows stop
     * counting as a live commitment the moment the state changes.
     * <p>
     * A carried issue therefore ends up with one row per sprint — the join table is the membership
     * history, and both sprints' reports read correctly. Every move is recorded on the issue's own
     * history with whoever closed the sprint as the actor, so a bulk carry-over is as traceable as a
     * manual drag.
     */
    @Transactional
    public void carryOver(Issue issue, Sprint closingSprint, Sprint destination, Member actor) {
        if (destination != null) {
            join(destination, issue, actor);
        }

        activityLogService.record(issue.getId(), actor.getId(), activityLogService.changeSet()
            .changed(FIELD_SPRINT, closingSprint.getName(), destination == null ? null : destination.getName()));
    }

    /**
     * Discard a sprint's membership rows outright — the deleting-a-{@code FUTURE}-sprint path, where the
     * sprint itself is about to disappear and its rows have nowhere left to point. Every issue still in
     * it records the departure first, so an abandoned plan is still visible in the issue's history; the
     * issues themselves are untouched and simply fall back into the product backlog.
     */
    @Transactional
    public void discardMemberships(Sprint sprint, Member actor) {
        List<SprintIssue> memberships = sprintIssueRepository.findBySprintId(sprint.getId());

        memberships.stream()
            .filter(membership -> membership.getRemovedAt() == null)
            .forEach(membership -> activityLogService.record(membership.getIssueId(), actor.getId(),
                activityLogService.changeSet().removed(FIELD_SPRINT, sprint.getName())));

        sprintIssueRepository.deleteAll(memberships);
    }

    private boolean isOpen(String sprintId) {
        return sprintRepository.findById(sprintId)
            .map(sprint -> sprint.getState().isOpen())
            .orElse(false);
    }

    private String sprintName(String sprintId) {
        return sprintRepository.findById(sprintId).map(Sprint::getName).orElse(null);
    }

}
