package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.sprint.CreateSprintRequest;
import net.innoventa.tessera.dto.sprint.SprintSummary;
import net.innoventa.tessera.dto.sprint.StartSprintRequest;
import net.innoventa.tessera.dto.sprint.UpdateSprintRequest;
import net.innoventa.tessera.service.SprintService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A project's sprints (Phase-3 tickets 02/03/04). Project-scoped like every other planning endpoint —
 * a sprint belongs to a project, not to a board (ADR-0012). Reads go through the visibility gate
 * (non-member → 404, member without {@code BROWSE_PROJECT} → 403); every mutation requires
 * {@code MANAGE_SPRINT}, and the product's refusals — starting a second sprint, starting without an end
 * date, deleting one that already ran — come back as 409s through the global handler.
 */
@RestController
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @GetMapping("/api/projects/{projectId}/sprints")
    public List<SprintSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return sprintService.list(jwt, projectId);
    }

    @PostMapping("/api/projects/{projectId}/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    public SprintSummary create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody CreateSprintRequest request
    ) {
        return sprintService.create(jwt, projectId, request);
    }

    @PutMapping("/api/projects/{projectId}/sprints/{sprintId}")
    public SprintSummary update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String sprintId,
        @Valid @RequestBody UpdateSprintRequest request
    ) {
        return sprintService.update(jwt, projectId, sprintId, request);
    }

    @DeleteMapping("/api/projects/{projectId}/sprints/{sprintId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String sprintId
    ) {
        sprintService.delete(jwt, projectId, sprintId);
    }

    /** Starting is its own action, not an update: the end date is required and {@code startedAt} is ours. */
    @PostMapping("/api/projects/{projectId}/sprints/{sprintId}/start")
    public SprintSummary start(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String sprintId,
        @RequestBody StartSprintRequest request
    ) {
        return sprintService.start(jwt, projectId, sprintId, request);
    }

}
