package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.board.BoardCardView;
import net.innoventa.tessera.dto.board.BoardMoveRequest;
import net.innoventa.tessera.dto.board.BoardResponse;
import net.innoventa.tessera.service.BoardMoveService;
import net.innoventa.tessera.service.BoardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Kanban board (Phase-2 tickets 01/02). Project-scoped like the issue endpoints and addressed by
 * the same {@code {projectId}} the rest of the project API uses (the board is 1:1 with the project, so
 * no separate board id is exposed). Column configuration lives in {@link BoardColumnController}.
 * Visibility, not-found and permission errors flow through the global handler exactly as the issue
 * endpoints do — non-member → 404, member without the gating permission → 403.
 */
@RestController
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final BoardMoveService boardMoveService;

    @GetMapping("/api/projects/{projectId}/board")
    public BoardResponse board(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return boardService.getBoard(jwt, projectId);
    }

    @PostMapping("/api/projects/{projectId}/board/move")
    public BoardCardView move(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody BoardMoveRequest request
    ) {
        return boardMoveService.move(jwt, projectId, request);
    }

}
