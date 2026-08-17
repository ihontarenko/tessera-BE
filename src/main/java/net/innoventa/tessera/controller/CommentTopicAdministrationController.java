package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.configuration.CommentTopicRequest;
import net.innoventa.tessera.dto.configuration.CommentTopicResponse;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.configuration.ConfigurationUsage;
import net.innoventa.tessera.service.configuration.FlatCatalogWriteService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editing the comment-topic catalog — what a comment may be said to be about.
 *
 * <p>Reads open, writes behind {@code configuration:administer} at {@code GLOBAL}, exactly as the
 * priorities controller explains.
 *
 * <p>⚠️ Unlike resolutions, <strong>the last topic may be deleted</strong>: a topic is optional, so an
 * installation with none is coherent. See {@code FlatCatalogWriteService.deleteCommentTopic}.
 */
@RestController
@RequestMapping("/api/admin/configuration/comment-topics")
@RequiredArgsConstructor
@RequiresAccess
public class CommentTopicAdministrationController {

    private final FlatCatalogWriteService flatCatalogWriteService;
    private final ConfigurationUsage      configurationUsage;

    @GetMapping("/{commentTopicId}/usage")
    public ConfigurationUsageReport usage(@PathVariable String commentTopicId) {
        return configurationUsage.ofCommentTopic(commentTopicId);
    }

    @PostMapping
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public CommentTopicResponse create(@Valid @RequestBody CommentTopicRequest request) {
        return flatCatalogWriteService.createCommentTopic(request);
    }

    @PutMapping("/{commentTopicId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public CommentTopicResponse update(
        @PathVariable String commentTopicId, @Valid @RequestBody CommentTopicRequest request) {

        return flatCatalogWriteService.updateCommentTopic(commentTopicId, request);
    }

    @DeleteMapping("/{commentTopicId}")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public void delete(@PathVariable String commentTopicId) {
        flatCatalogWriteService.deleteCommentTopic(commentTopicId);
    }

}
