package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.backlog.BacklogMoveRequest;
import net.innoventa.tessera.dto.backlog.BacklogResponse;
import net.innoventa.tessera.service.BacklogMoveService;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.BacklogService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The backlog screen (Phase-3 tickets 02/03): one read for the whole thing, and one write for every
 * drag on it. Both are addressed by the same {@code {projectId}} the rest of the project API uses, and
 * neither is gated on the board's scope strategy — every project has a backlog (ADR-0016), so any
 * project may be asked for its own.
 * <p>
 * The move returns the screen rather than the moved row, because a drag changes two panels' counts and
 * story-point totals — sending back the whole shape keeps the client from re-deriving them.
 */
@RestController
@RequiredArgsConstructor
@RequiresAccess(permission = Permissions.BROWSE_PROJECT, scope = Scopes.PROJECT)
public class BacklogController {

    private final BacklogService backlogService;
    private final BacklogMoveService backlogMoveService;

    @GetMapping("/api/projects/{projectId}/backlog")
    public BacklogResponse backlog(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return backlogService.getBacklog(jwt, projectId);
    }

    /**
     * ⚠️ <strong>Declared on browsing, and the write permission is decided inside.</strong> Which one a
     * drag costs depends on <em>where it lands</em>: onto a sprint it is {@code MANAGE_SPRINT}, within the
     * backlog it is {@code EDIT_ISSUE}. An annotation can only name one, and naming the stronger would
     * refuse a developer reordering their own backlog while naming the weaker would let them commit work
     * to a sprint. So the outer gate is here — nobody outside the project reaches the service at all —
     * and the branch stays where the destination is known.
     */
    @PostMapping("/api/projects/{projectId}/backlog/move")
    public BacklogResponse move(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody BacklogMoveRequest request
    ) {
        return backlogMoveService.move(jwt, projectId, request);
    }

}
