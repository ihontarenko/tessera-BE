package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.comment.CommentResponse;
import net.innoventa.tessera.dto.comment.SaveCommentRequest;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.CommentService;
import org.jmouse.access.enforcement.RequiresAccess;
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
 * Issue comments (ticket 13) — a flat list under the issue.
 *
 * <p>The class declares the project every route here is about: the issue named in the path is what
 * resolves it, so a comment identifier never has to. Each method adds only the permission its own verb
 * needs.
 *
 * <p>⚠️ <strong>Edit and delete carry {@code ADD_COMMENT} and the rest is the service's.</strong> "Your
 * own comment, or anybody's if you administer the project" is one handler with two answers, and the
 * second is a question an annotation cannot phrase — it is about <em>whose row this is</em> combined
 * with a different permission. What the annotation still buys is the outer gate: somebody with no
 * business in the project is refused before the service loads anything.
 */
@RestController
@RequestMapping("/api/issues/{issueId}/comments")
@RequiredArgsConstructor
@RequiresAccess(scope = Scopes.PROJECT, resource = Issue.class, resourceId = "issueId")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    @RequiresAccess(permission = Permissions.BROWSE_PROJECT)
    public List<CommentResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueId) {
        return commentService.list(jwt, issueId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAccess(permission = Permissions.ADD_COMMENT)
    public CommentResponse add(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @Valid @RequestBody SaveCommentRequest request
    ) {
        return commentService.add(jwt, issueId, request);
    }

    @PutMapping("/{commentId}")
    @RequiresAccess(permission = Permissions.ADD_COMMENT)
    public CommentResponse edit(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @PathVariable String commentId,
        @Valid @RequestBody SaveCommentRequest request
    ) {
        return commentService.edit(jwt, issueId, commentId, request);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresAccess(permission = Permissions.ADD_COMMENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @PathVariable String commentId
    ) {
        commentService.delete(jwt, issueId, commentId);
    }

}
