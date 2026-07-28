package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.component.ComponentResponse;
import net.innoventa.tessera.dto.component.SaveComponentRequest;
import net.innoventa.tessera.service.ComponentService;
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
 * Project components (ticket 06). Listing needs membership; create/edit/delete require
 * {@code ADMINISTER_PROJECT} (enforced in the service). Components hang off the project; the
 * individual-component operations are keyed by component id.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/components")
@RequiredArgsConstructor
public class ComponentController {

    private final ComponentService componentService;

    @GetMapping
    public List<ComponentResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return componentService.list(jwt, projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComponentResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @Valid @RequestBody SaveComponentRequest request
    ) {
        return componentService.create(jwt, projectId, request);
    }

    @PutMapping("/{componentId}")
    public ComponentResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String componentId,
        @Valid @RequestBody SaveComponentRequest request
    ) {
        return componentService.update(jwt, componentId, request);
    }

    @DeleteMapping("/{componentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String projectId,
        @PathVariable String componentId
    ) {
        componentService.delete(jwt, componentId);
    }

}
