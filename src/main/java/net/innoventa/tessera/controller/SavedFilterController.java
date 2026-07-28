package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.filter.FilterPreviewView;
import net.innoventa.tessera.dto.filter.PreviewFilterRequest;
import net.innoventa.tessera.dto.filter.SaveFilterRequest;
import net.innoventa.tessera.dto.filter.SavedFilterView;
import net.innoventa.tessera.service.BoardService;
import net.innoventa.tessera.service.SavedFilterService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Saved board filters (ADR-0008), project-scoped like every other project resource. A saved filter is
 * applied by handing its {@code expression} straight back to
 * {@code GET /api/projects/{projectId}/board?filter=…} — there is no "run this filter" endpoint,
 * because a filter is a string, not a stored procedure.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/filters")
public class SavedFilterController {

    private final SavedFilterService savedFilterService;
    private final BoardService boardService;

    @GetMapping
    public List<SavedFilterView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return savedFilterService.list(jwt, projectId);
    }

    /**
     * Try an expression against the caller's own board without saving or applying it — what the editor
     * calls while someone is still typing. A broken expression comes back as {@code valid: false} with a
     * message rather than a {@code 400}, because mid-edit is not a client error.
     */
    @PostMapping("/preview")
    public FilterPreviewView preview(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @RequestBody PreviewFilterRequest request
    ) {
        return boardService.previewFilter(jwt, projectId, request.expression());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedFilterView create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody SaveFilterRequest request
    ) {
        return savedFilterService.create(jwt, projectId, request);
    }

    @PutMapping("/{savedFilterId}")
    public SavedFilterView update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String savedFilterId,
        @Valid @RequestBody SaveFilterRequest request
    ) {
        return savedFilterService.update(jwt, projectId, savedFilterId, request);
    }

    @DeleteMapping("/{savedFilterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String savedFilterId
    ) {
        savedFilterService.delete(jwt, projectId, savedFilterId);
    }

}
