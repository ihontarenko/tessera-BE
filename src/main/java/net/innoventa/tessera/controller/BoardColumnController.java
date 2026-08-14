package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.board.BoardColumnView;
import net.innoventa.tessera.dto.board.CreateBoardColumnRequest;
import net.innoventa.tessera.dto.board.ReorderBoardColumnRequest;
import net.innoventa.tessera.dto.board.SetColumnFallbackRequest;
import net.innoventa.tessera.dto.board.UpdateBoardColumnRequest;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.BoardColumnService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Board column configuration (Phase-2 ticket 03) — every operation here requires
 * {@code ADMINISTER_PROJECT}, declared once on the class because all eight cost the same and a column is
 * only ever addressed inside its project's path. Reshaping the column list
 * (create/rename/reorder/delete/WIP) and the fallback role are distinct concerns from the explicit
 * status→column overrides, split across their own sub-paths for the same reason
 * {@link net.innoventa.tessera.domain.BoardColumnStatus} is its own table: a status mapping is a
 * targeted override, not a column property edit.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/board/columns")
@RequiredArgsConstructor
@RequiresAccess(permission = Permissions.ADMINISTER_PROJECT, scope = Scopes.PROJECT)
public class BoardColumnController {

    private final BoardColumnService boardColumnService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardColumnView create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody CreateBoardColumnRequest request
    ) {
        return boardColumnService.create(jwt, projectId, request);
    }

    @PutMapping("/{columnId}")
    public BoardColumnView update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String columnId,
        @Valid @RequestBody UpdateBoardColumnRequest request
    ) {
        return boardColumnService.update(jwt, projectId, columnId, request);
    }

    @PutMapping("/{columnId}/position")
    public BoardColumnView reorder(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String columnId,
        @Valid @RequestBody ReorderBoardColumnRequest request
    ) {
        return boardColumnService.reorder(jwt, projectId, columnId, request.position());
    }

    @DeleteMapping("/{columnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId, @PathVariable String columnId) {
        boardColumnService.delete(jwt, projectId, columnId);
    }

    @PutMapping("/{columnId}/fallback")
    public BoardColumnView setFallback(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String columnId,
        @Valid @RequestBody SetColumnFallbackRequest request
    ) {
        return boardColumnService.setFallback(jwt, projectId, columnId, request);
    }

    @DeleteMapping("/{columnId}/fallback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearFallback(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId, @PathVariable String columnId) {
        boardColumnService.clearFallback(jwt, projectId, columnId);
    }

    @PutMapping("/{columnId}/statuses/{statusId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mapStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String columnId,
        @PathVariable String statusId
    ) {
        boardColumnService.mapStatus(jwt, projectId, columnId, statusId);
    }

    @DeleteMapping("/{columnId}/statuses/{statusId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmapStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String columnId,
        @PathVariable String statusId
    ) {
        boardColumnService.unmapStatus(jwt, projectId, columnId, statusId);
    }

}
