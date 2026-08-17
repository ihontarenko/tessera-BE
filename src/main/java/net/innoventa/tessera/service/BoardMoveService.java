package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardColumn;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.board.BoardCardView;
import net.innoventa.tessera.dto.board.BoardMoveRequest;
import net.innoventa.tessera.dto.issue.TransitionIssueRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Drag-and-drop a card (Phase-2 ticket 02) — the board's single write path. A drop within the card's
 * current column only re-ranks it ({@code EDIT_ISSUE}); a drop on another column resolves the
 * <strong>first</strong> status that column maps (by {@link BoardColumnResolver}, name order for
 * determinism) with a legal {@code (from → to)} edge and runs it through {@link TransitionService} —
 * which already enforces {@code TRANSITION_ISSUE}, the resolution-required rule on entering Done, and
 * the {@code resolvedAt}/activity-log bookkeeping (ADR-0011/0007). Either way the rank update happens in
 * the same transaction as the transition, so a cross-column drop transitions and ranks atomically.
 * <p>
 * A rank-only reorder is deliberately not written to the activity log (a status change already is,
 * inside {@link TransitionService}) — per the spec, reordering has no field-level meaning to record.
 */
@Service
@RequiredArgsConstructor
public class BoardMoveService {

    private final StatusRepository statusRepository;
    private final IssueService issueService;
    private final ProjectService projectService;
    private final ProjectAccess projectAccess;
    private final MemberService memberService;
    private final WorkflowResolver workflowResolver;
    private final RankService rankService;
    private final TransitionService transitionService;
    private final BoardService boardService;
    private final BoardColumnResolver boardColumnResolver;
    private final IssueBlockers issueBlockers;

    @Transactional
    public BoardCardView move(Jwt jwt, String projectId, BoardMoveRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Project project = projectService.requireProject(projectId);

        Issue issue = issueService.requireIssueInProject(request.issueKey(), projectId);
        Board board = boardService.requireBoard(projectId);
        BoardService.ColumnMapping mapping = boardService.loadColumnMapping(board.getId());
        BoardColumn targetColumn = requireColumn(request.targetColumnId(), mapping.columns());

        Status currentStatus = workflowResolver.requireStatus(issue.getStatusId());
        String currentColumnId = boardColumnResolver.resolveColumnId(currentStatus, mapping.columns(), mapping.statusToColumn());

        // ⚠️ Which permission a drag costs depends on where the card lands, which is why the route
        // declares only browsing: within a column it is a rank change, across one the workflow runs.
        if (targetColumn.getId().equals(currentColumnId)) {
            projectAccess.require(caller, projectId, Permissions.EDIT_ISSUE);
        } else {
            projectAccess.require(caller, projectId, Permissions.TRANSITION_ISSUE);
            String workflowId = workflowResolver.resolveWorkflowId(project, issue.getIssueTypeId());
            Status targetStatus = resolveTargetStatus(targetColumn, mapping, workflowId, issue);
            transitionService.transition(jwt, issue.getId(), new TransitionIssueRequest(targetStatus.getId(), request.resolutionId()));
        }

        issue.setRank(rankService.between(
            issueService.neighbourRank(request.beforeIssueKey(), projectId),
            issueService.neighbourRank(request.afterIssueKey(), projectId)));

        return boardService.cardView(issue, mapping);
    }

    /**
     * The first status (name order, for a deterministic pick among several legal candidates) that
     * {@link BoardColumnResolver} would resolve into {@code targetColumn} and that the issue's workflow
     * allows a legal edge into from its current status. No candidate at all — the column maps no legal
     * status for this issue — is refused as a business-rule violation ({@code 409}).
     */
    private Status resolveTargetStatus(BoardColumn targetColumn, BoardService.ColumnMapping mapping, String workflowId, Issue issue) {
        List<Status> legal = statusRepository.findAllByOrderByNameAsc().stream()
            .filter(status -> targetColumn.getId().equals(boardColumnResolver.resolveColumnId(status, mapping.columns(), mapping.statusToColumn())))
            .filter(status -> workflowResolver.transitionExists(workflowId, issue.getStatusId(), status.getId()))
            .toList();

        // ⚠️ Blocked candidates are skipped rather than picked and then refused (TSSR-41). A column may
        // map several statuses, and taking the first one alphabetically would fail a drag that had a
        // perfectly good landing place one entry further down.
        Status unblocked = legal.stream()
            .filter(status -> issueBlockers.blocking(issue.getId(), status.getCategory()).isEmpty())
            .findFirst()
            .orElse(null);

        if (unblocked != null) {
            return unblocked;
        }

        // ⚠️ And when every candidate is blocked, the refusal says so. Falling through to "no legal
        // transition" would name the workflow for something the workflow allows, sending whoever reads
        // it to the wrong screen.
        if (!legal.isEmpty()) {
            throw new BusinessRuleViolationException(issueBlockers.refusalFor(issue, legal.getFirst().getCategory()));
        }

        throw new BusinessRuleViolationException(
            "No legal transition into column '" + targetColumn.getName() + "' from the issue's current status");
    }

    private BoardColumn requireColumn(String columnId, List<BoardColumn> columns) {
        return columns.stream()
            .filter(column -> column.getId().equals(columnId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Board column not found: " + columnId));
    }

}
