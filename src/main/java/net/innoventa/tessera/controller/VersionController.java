package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.version.SaveVersionRequest;
import net.innoventa.tessera.dto.version.VersionResponse;
import net.innoventa.tessera.service.VersionService;
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
 * Project versions (ticket 06). Listing needs membership; create/edit/delete require
 * {@code ADMINISTER_PROJECT} (enforced in the service).
 */
@RestController
@RequestMapping("/api/projects/{projectId}/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @GetMapping
    public List<VersionResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return versionService.list(jwt, projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VersionResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody SaveVersionRequest request
    ) {
        return versionService.create(jwt, projectId, request);
    }

    @PutMapping("/{versionId}")
    public VersionResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String versionId,
        @Valid @RequestBody SaveVersionRequest request
    ) {
        return versionService.update(jwt, versionId, request);
    }

    @DeleteMapping("/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String versionId
    ) {
        versionService.delete(jwt, versionId);
    }

}
