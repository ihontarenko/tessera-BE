package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Comment;
import net.innoventa.tessera.domain.CommentTopic;
import net.innoventa.tessera.security.CallingAgent;
import org.jmouse.ai.agent.Agent;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.comment.CommentResponse;
import net.innoventa.tessera.dto.comment.CommentTopicSummary;
import net.innoventa.tessera.dto.comment.SaveCommentRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ForbiddenException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.CommentRepository;
import net.innoventa.tessera.repository.CommentTopicRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    private final CommentTopicRepository commentTopicRepository;
    private final IssueRepository issueRepository;
    private final MemberRepository memberRepository;
    private final ProjectAccess projectAccess;
    private final MemberService memberService;
    private final Supplier<String> idGenerator;
    private final CallingAgent callingAgent;

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Jwt jwt, String issueId) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);

        return commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId).stream()
            .map(comment -> toResponse(comment, caller, issue.getProjectId()))
            .toList();
    }

    @Transactional
    public CommentResponse add(Jwt jwt, String issueId, SaveCommentRequest request) {
        return add(memberService.resolveMember(jwt), issueId, request);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    @Transactional
    public CommentResponse add(Member caller, String issueId, SaveCommentRequest request) {
        Issue issue = requireIssue(issueId);

        // ⚠️ Stamped on creation and never on edit. The agent that wrote a comment is a fact about the
        // writing; a person correcting a typo afterwards has not made it theirs, and an edit re-stamping
        // it would erase the one thing the badge exists to say.
        Agent agent = callingAgent.current().orElse(null);

        Comment comment = commentRepository.save(Comment.builder()
            .id(idGenerator.get())
            .issueId(issueId)
            .authorMemberId(caller.getId())
            .body(request.body())
            .topicId(requireTopicId(request.topicId()))
            .parentCommentId(requireAnswerableParent(request.parentCommentId(), issueId))
            .agentId(agent == null ? null : agent.id())
            .agentName(agent == null ? null : agent.name())
            .build());

        return toResponse(comment, caller, issue.getProjectId());
    }

    @Transactional
    public CommentResponse edit(Jwt jwt, String issueId, String commentId, SaveCommentRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Comment comment = requireComment(commentId, issueId);

        // A member edits only their own comment (ticket 13). ADD_COMMENT is on the route; this is the
        // half an annotation cannot phrase, because it is about whose row this is.
        if (!comment.getAuthorMemberId().equals(caller.getId())) {
            throw new ForbiddenException("You can only edit your own comment");
        }

        // ⚠️ Replaced, not merged — an edit that names no topic clears the one that was there. See
        // SaveCommentRequest: one record serves both moves, and a field given is a field replaced.
        //
        // ⚠️ parentCommentId is deliberately NOT read here. What a reply answers is not something an
        // edit changes, and reading it would mean a caller who reworded a reply and forgot the field
        // promoted it to the top of the thread.
        comment.setBody(request.body());
        comment.setTopicId(requireTopicId(request.topicId()));

        return toResponse(comment, caller, issue.getProjectId());
    }

    @Transactional
    public void delete(Jwt jwt, String issueId, String commentId) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Comment comment = requireComment(commentId, issueId);

        boolean isAuthor = comment.getAuthorMemberId().equals(caller.getId());
        boolean isAdministrator = projectAccess.holds(caller, issue.getProjectId(), Permissions.ADMINISTER_PROJECT);
        if (!isAuthor && !isAdministrator) {
            throw new ForbiddenException("You can only delete your own comment");
        }

        // ⚠️ The replies go with it, and the service does this rather than an ON DELETE CASCADE — so the
        // screen can say how many first. An answer to a comment that no longer exists is an answer to
        // nothing: it reads as a non-sequitur, and no reader can recover what it meant.
        List<Comment> replies = commentRepository.findByParentCommentIdOrderByCreatedAtAsc(commentId);

        if (!replies.isEmpty()) {
            commentRepository.deleteAll(replies);
        }

        commentRepository.delete(comment);
    }

    private CommentResponse toResponse(Comment comment, Member caller, String projectId) {
        MemberSummary author = memberRepository.findById(comment.getAuthorMemberId())
            .map(MemberSummary::from)
            .orElse(null);

        boolean isAuthor = comment.getAuthorMemberId().equals(caller.getId());
        boolean isAdministrator = projectAccess.holds(caller, projectId, Permissions.ADMINISTER_PROJECT);

        CommentTopicSummary topic = comment.getTopicId() == null
            ? null
            : commentTopicRepository.findById(comment.getTopicId()).map(CommentTopicSummary::from).orElse(null);

        return new CommentResponse(
            comment.getId(),
            author,
            comment.getAgentName(),
            topic,
            comment.getParentCommentId(),
            comment.getBody(),
            isAuthor || isAdministrator,
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }

    /**
     * The topic as it is stored: null when nothing was named, and refused when the catalog does not know
     * it — with the names that would have worked, the way every other named reference here fails.
     *
     * <p>Blank collapses to null rather than being looked up: a select that was cleared posts an empty
     * string, and "" is not an id the catalog could ever hold.
     */
    private String requireTopicId(String topicId) {
        String trimmed = topicId == null ? "" : topicId.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        if (!commentTopicRepository.existsById(trimmed)) {
            String known = commentTopicRepository.findAllByOrderByNameAsc().stream()
                .map(CommentTopic::getName)
                .collect(Collectors.joining(", "));

            throw new BusinessRuleViolationException(
                "There is no comment topic '" + trimmed + "'. "
                + (known.isEmpty() ? "This installation has none yet." : "The ones there are: " + known + "."));
        }

        return trimmed;
    }

    /**
     * The comment this one may answer — null when it answers nothing, refused when it may not (TSSR-26).
     *
     * <p>Two checks, and each closes a different hole:
     *
     * <ul>
     *   <li><strong>Same issue.</strong> Without it a reply becomes a link between two issues that
     *       nothing in the product would ever draw, and one of them would render a thread whose parent
     *       it does not hold.
     *   <li><strong>The parent has no parent.</strong> This is the depth cap, and it lives here because
     *       no column constraint can say it. A tracker is not a forum: nesting without a bound produces
     *       a shape nobody can scan or quote from.
     * </ul>
     */
    private String requireAnswerableParent(String parentCommentId, String issueId) {
        String trimmed = parentCommentId == null ? "" : parentCommentId.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        Comment parent = requireComment(trimmed, issueId);

        if (parent.getParentCommentId() != null) {
            throw new BusinessRuleViolationException(
                "That comment is itself a reply, and replies go one level deep. Answer the comment it "
                + "replies to instead.");
        }

        return parent.getId();
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
