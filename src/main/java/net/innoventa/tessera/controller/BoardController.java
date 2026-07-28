package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.board.BoardCardView;
import net.innoventa.tessera.dto.board.BoardMoveRequest;
import net.innoventa.tessera.dto.board.BoardResponse;
import net.innoventa.tessera.dto.board.BoardSettingsView;
import net.innoventa.tessera.dto.board.SetDoneThresholdRequest;
import net.innoventa.tessera.dto.board.SetScopeStrategyRequest;
import net.innoventa.tessera.dto.board.SetSwimlaneStrategyRequest;
import net.innoventa.tessera.service.BoardMoveService;
import net.innoventa.tessera.service.BoardService;
import net.innoventa.tessera.service.BoardSettingsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Kanban board (Phase-2 tickets 01/02/04/06). Project-scoped like the issue endpoints and addressed
 * by the same {@code {projectId}} the rest of the project API uses (the board is 1:1 with the project,
 * so no separate board id is exposed). Column configuration lives in {@link BoardColumnController}.
 * Visibility, not-found and permission errors flow through the global handler exactly as the issue
 * endpoints do — non-member → 404, member without the gating permission → 403.
 */
@RestController
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final BoardMoveService boardMoveService;
    private final BoardSettingsService boardSettingsService;

    /**
     * The board, optionally narrowed by a jME predicate (ADR-0008) — {@code ?filter=issue.assignee ==
     * currentMember}. Only the expression is sent: the server already holds the issues, so a filter
     * costs one query parameter rather than a round trip of the board's own payload. An unusable
     * expression is a {@code 400} with a message written for whoever typed it.
     */
    @GetMapping("/api/projects/{projectId}/board")
    public BoardResponse board(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @RequestParam(required = false) String filter
    ) {
        return boardService.getBoard(jwt, projectId, filter);
    }

    @PostMapping("/api/projects/{projectId}/board/move")
    public BoardCardView move(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody BoardMoveRequest request
    ) {
        return boardMoveService.move(jwt, projectId, request);
    }

    /** Board-wide view settings, each set on its own so none overwrites another from a stale copy; all
     *  require {@code ADMINISTER_PROJECT}. */
    @PutMapping("/api/projects/{projectId}/board/settings/swimlane-strategy")
    public BoardSettingsView setSwimlaneStrategy(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody SetSwimlaneStrategyRequest request
    ) {
        return boardSettingsService.setSwimlaneStrategy(jwt, projectId, request);
    }

    @PutMapping("/api/projects/{projectId}/board/settings/done-threshold")
    public BoardSettingsView setDoneThreshold(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody SetDoneThresholdRequest request
    ) {
        return boardSettingsService.setDoneThreshold(jwt, projectId, request);
    }

    /**
     * Switch the project between Scrum and a board of everything (Phase-3 ticket 08). Its own endpoint
     * rather than a field on a combined update, for the reason above — and the change is a view change
     * only: no sprint is started, closed or touched by it.
     */
    @PutMapping("/api/projects/{projectId}/board/settings/scope-strategy")
    public BoardSettingsView setScopeStrategy(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody SetScopeStrategyRequest request
    ) {
        return boardSettingsService.setScopeStrategy(jwt, projectId, request);
    }

}
