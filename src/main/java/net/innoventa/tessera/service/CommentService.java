package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Comment;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.comment.CommentResponse;
import net.innoventa.tessera.dto.comment.SaveCommentRequest;
import net.innoventa.tessera.exception.ForbiddenException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.CommentRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

/**
 * Issue comments (ticket 13): a flat list, no threading, and a first-class entity kept out of the
 * activity log (ADR-0007) — discussion is not a field change. A member with {@code ADD_COMMENT} may
 * comment; a member may edit or delete their own; a project administrator may delete any. The
 * {@code editable} flag on each response tells the UI whether to show the edit/delete controls.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final MemberRepository memberRepository;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final Supplier<String> idGenerator;

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Jwt jwt, String issueId) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        projectPermissionService.requireVisible(caller.getId(), issue.getProjectId());

        return commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId).stream()
            .map(comment -> toResponse(comment, caller, issue.getProjectId()))
            .toList();
    }

    @Transactional
    public CommentResponse add(Jwt jwt, String issueId, SaveCommentRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        projectPermissionService.require(caller, issue.getProjectId(), Permissions.ADD_COMMENT);

        Comment comment = commentRepository.save(Comment.builder()
            .id(idGenerator.get())
            .issueId(issueId)
            .authorMemberId(caller.getId())
            .body(request.body())
            .build());

        return toResponse(comment, caller, issue.getProjectId());
    }

    @Transactional
    public CommentResponse edit(Jwt jwt, String issueId, String commentId, SaveCommentRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Comment comment = requireComment(commentId, issueId);
        projectPermissionService.require(caller, issue.getProjectId(), Permissions.ADD_COMMENT);

        // A member edits only their own comment (ticket 13).
        if (!comment.getAuthorMemberId().equals(caller.getId())) {
            throw new ForbiddenException("You can only edit your own comment");
        }

        comment.setBody(request.body());
        return toResponse(comment, caller, issue.getProjectId());
    }

    @Transactional
    public void delete(Jwt jwt, String issueId, String commentId) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Comment comment = requireComment(commentId, issueId);

        boolean isAuthor = comment.getAuthorMemberId().equals(caller.getId());
        boolean isAdministrator = projectPermissionService.hasPermission(caller.getId(), issue.getProjectId(), Permissions.ADMINISTER_PROJECT);
        if (!isAuthor && !isAdministrator) {
            throw new ForbiddenException("You can only delete your own comment");
        }

        commentRepository.delete(comment);
    }

    private CommentResponse toResponse(Comment comment, Member caller, String projectId) {
        MemberSummary author = memberRepository.findById(comment.getAuthorMemberId())
            .map(MemberSummary::from)
            .orElse(null);

        boolean isAuthor = comment.getAuthorMemberId().equals(caller.getId());
        boolean isAdministrator = projectPermissionService.hasPermission(caller.getId(), projectId, Permissions.ADMINISTER_PROJECT);

        return new CommentResponse(
            comment.getId(),
            author,
            comment.getBody(),
            isAuthor || isAdministrator,
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }

    private Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

    private Comment requireComment(String commentId, String issueId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!comment.getIssueId().equals(issueId)) {
            throw new ResourceNotFoundException("Comment not found on this issue: " + commentId);
        }

        return comment;
    }

}
