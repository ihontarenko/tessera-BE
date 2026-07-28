package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.comment.CommentResponse;
import net.innoventa.tessera.dto.comment.SaveCommentRequest;
import net.innoventa.tessera.service.CommentService;
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
 * Issue comments (ticket 13) — a flat list under the issue. Add requires {@code ADD_COMMENT}; edit is
 * author-only; delete is author or project administrator (enforced in the service).
 */
@RestController
@RequestMapping("/api/issues/{issueId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueId) {
        return commentService.list(jwt, issueId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse add(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @Valid @RequestBody SaveCommentRequest request
    ) {
        return commentService.add(jwt, issueId, request);
    }

    @PutMapping("/{commentId}")
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
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String issueId,
        @PathVariable String commentId
    ) {
        commentService.delete(jwt, issueId, commentId);
    }

}
