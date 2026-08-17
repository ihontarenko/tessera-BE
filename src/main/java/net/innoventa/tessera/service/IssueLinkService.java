package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueLink;
import net.innoventa.tessera.domain.LinkType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.issue.CreateIssueLinkRequest;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueLinkRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.LinkTypeRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Typed relationships between issues (ticket 12). A link is stored once as a directed
 * {@code source → target} row of a {@link LinkType}; the target renders the type's inward label from
 * the same record — there is no duplicate reverse row. Creating and removing a link both require
 * {@code EDIT_ISSUE} on the acting issue's project and are recorded to its activity log.
 *
 * <p>⚠️ <strong>A link crosses the project boundary, so it takes two permissions rather than one</strong>
 * (TSSR-43): {@code EDIT_ISSUE} on the end the link is recorded on, and {@code BROWSE_PROJECT} on the end
 * it points at — see {@link #requireLinkableIssue}. Writing to one project may not reach into a project
 * the caller cannot read.
 */
@Service
@RequiredArgsConstructor
public class IssueLinkService {

    static final String FIELD_LINK = "link";

    private final IssueRepository issueRepository;
    private final IssueLinkRepository issueLinkRepository;
    private final LinkTypeRepository linkTypeRepository;
    private final ProjectAccess projectAccess;
    private final ProjectService projectService;
    private final MemberService memberService;
    private final ActivityLogService activityLogService;
    private final IssueAssembler issueAssembler;
    private final BlockingCycles blockingCycles;
    private final Supplier<String> idGenerator;

    @Transactional
    public IssueResponse addLink(Jwt jwt, String issueId, CreateIssueLinkRequest request) {
        return addLink(memberService.resolveMember(jwt), issueId, request);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    @Transactional
    public IssueResponse addLink(Member caller, String issueId, CreateIssueLinkRequest request) {
        Issue source = requireIssue(issueId);
        Project project = projectService.requireProject(source.getProjectId());

        Issue target = requireLinkableIssue(caller, request.targetIssueId());
        if (target.getId().equals(source.getId())) {
            throw new BusinessRuleViolationException("An issue cannot be linked to itself");
        }

        LinkType linkType = linkTypeRepository.findById(request.linkTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("Link type not found: " + request.linkTypeId()));

        if (issueLinkRepository.existsBySourceIssueIdAndTargetIssueIdAndLinkTypeId(source.getId(), target.getId(), linkType.getId())) {
            throw new BusinessRuleViolationException("This link already exists");
        }

        blockingCycles.requireNoCycle(source, target, linkType);

        issueLinkRepository.save(IssueLink.builder()
            .id(idGenerator.get())
            .sourceIssueId(source.getId())
            .targetIssueId(target.getId())
            .linkTypeId(linkType.getId())
            .build());

        activityLogService.record(source.getId(), caller.getId(),
            activityLogService.changeSet().added(FIELD_LINK, linkType.getOutwardLabel() + " " + target.getIssueKey()));

        return issueAssembler.detail(source, project, caller);
    }

    @Transactional
    public IssueResponse removeLink(Jwt jwt, String issueId, String linkId) {
        return removeLink(memberService.resolveMember(jwt), issueId, linkId);
    }

    /** The same, for a caller that is not an HTTP request. */
    @Transactional
    public IssueResponse removeLink(Member caller, String issueId, String linkId) {
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());

        IssueLink link = issueLinkRepository.findById(linkId)
            .orElseThrow(() -> new ResourceNotFoundException("Link not found: " + linkId));

        boolean involvesIssue = link.getSourceIssueId().equals(issueId) || link.getTargetIssueId().equals(issueId);
        if (!involvesIssue) {
            throw new ResourceNotFoundException("Link not found on this issue: " + linkId);
        }

        LinkType linkType = linkTypeRepository.findById(link.getLinkTypeId()).orElse(null);
        String otherIssueId = link.getSourceIssueId().equals(issueId) ? link.getTargetIssueId() : link.getSourceIssueId();
        String otherKey = issueRepository.findById(otherIssueId).map(Issue::getIssueKey).orElse(otherIssueId);
        String label = linkType == null ? "link" : linkType.getName();

        issueLinkRepository.delete(link);

        activityLogService.record(issueId, caller.getId(),
            activityLogService.changeSet().removed(FIELD_LINK, label + " " + otherKey));

        return issueAssembler.detail(issue, project, caller);
    }

    /**
     * Changing what an existing link <em>is</em>, without unmaking the relationship (TSSR-40).
     *
     * <p>A mistyped link used to mean delete and recreate. It also means something else now that a link
     * type can carry an effect: retyping is how somebody legitimately says "these are related but this
     * one is not actually holding that one up".
     *
     * <p>⚠️ <strong>It is also how somebody gets past a gate, which is why it is recorded.</strong>
     * Turning "is blocked by" into "relates to" does not make a blocker go away — it relabels the truth
     * so the block lifts. That is right when the relationship was recorded wrongly and is quiet data
     * corruption when it was not, and the only thing that tells the two apart is that anybody can see it
     * happened. Hence {@code compare}, which writes both the old value and the new one, rather than a
     * remove-and-add pair that would read as two unrelated events.
     *
     * <p>⚠️ <strong>The type only — never the endpoints or the direction.</strong> A link is
     * {@code (source, target, type)}; change either of the first two and it is a different link, so that
     * stays delete-and-create. Reversing direction is the same thing wearing a friendlier name.
     */
    @Transactional
    public IssueResponse changeLinkType(Jwt jwt, String issueId, String linkId, String linkTypeId) {
        return changeLinkType(memberService.resolveMember(jwt), issueId, linkId, linkTypeId);
    }

    /** The same, for a caller that is not an HTTP request. */
    @Transactional
    public IssueResponse changeLinkType(Member caller, String issueId, String linkId, String linkTypeId) {
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());

        IssueLink link = requireLinkOnIssue(linkId, issueId);

        LinkType nextType = linkTypeRepository.findById(linkTypeId)
            .orElseThrow(() -> new ResourceNotFoundException("Link type not found: " + linkTypeId));

        if (nextType.getId().equals(link.getLinkTypeId())) {
            return issueAssembler.detail(issue, project, caller);
        }

        // The same pair may not hold two links of one type, and retyping is the one move that could
        // create that collision without going through addLink's check.
        if (issueLinkRepository.existsBySourceIssueIdAndTargetIssueIdAndLinkTypeId(
                link.getSourceIssueId(), link.getTargetIssueId(), nextType.getId())) {
            throw new BusinessRuleViolationException(
                "These two issues are already linked as '" + nextType.getName() + "'.");
        }

        // ⚠️ Retyping can close a cycle too — turning a harmless "relates to" into a blocking link is
        // exactly as capable of deadlocking two issues as creating one.
        blockingCycles.requireNoCycle(
            requireIssue(link.getSourceIssueId()), requireIssue(link.getTargetIssueId()), nextType);

        LinkType previousType = linkTypeRepository.findById(link.getLinkTypeId()).orElse(null);
        String otherKey = otherIssueKey(link, issueId);

        link.setLinkTypeId(nextType.getId());

        activityLogService.record(issueId, caller.getId(), activityLogService.changeSet().compare(
            FIELD_LINK,
            (previousType == null ? "link" : previousType.getName()) + " " + otherKey,
            nextType.getName() + " " + otherKey));

        return issueAssembler.detail(issue, project, caller);
    }

    /**
     * The link between two issues of a given type, whichever way round it was stored (TSSR-44).
     *
     * <p>⚠️ <strong>Addressed by what it <em>is</em>, not by a link id.</strong> No action in the
     * protocol ever hands a link id out — {@code describeInFull} strips identifiers on purpose — so a
     * tool that required one would be an action nobody could call. Two issues and a type name are what
     * a caller actually has.
     *
     * <p>⚠️ <strong>Direction is not part of the address.</strong> A link is stored once as
     * {@code source → target} and read from either end, so "the Blocks link between TSSR-1 and TSSR-2"
     * is one thing regardless of which of them it was created from. Making the caller guess the stored
     * direction would be asking about an implementation detail the interface never shows.
     */
    public IssueLink requireLinkBetween(String issueId, String otherIssueId, String linkTypeId) {
        return issueLinkRepository.findBySourceIssueId(issueId).stream()
            .filter(link -> link.getTargetIssueId().equals(otherIssueId))
            .filter(link -> link.getLinkTypeId().equals(linkTypeId))
            .findFirst()
            .or(() -> issueLinkRepository.findByTargetIssueId(issueId).stream()
                .filter(link -> link.getSourceIssueId().equals(otherIssueId))
                .filter(link -> link.getLinkTypeId().equals(linkTypeId))
                .findFirst())
            .orElseThrow(() -> new ResourceNotFoundException("These two issues are not linked that way"));
    }

    private Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

    /**
     * The far end of a new link — an issue the caller may actually <em>browse</em>.
     *
     * <p>⚠️ <strong>The route gates the acting issue and cannot gate this one.</strong>
     * {@code @RequiresAccess} names one resource, and the only one it can name here is the issue in the
     * path; the target arrives in the body. So until this existed, {@code EDIT_ISSUE} on one project was
     * enough to attach any issue in the installation to it, given its identifier — and a {@code Blocks}
     * link that way is not merely a disclosure, it puts a gate on somebody else's issue.
     *
     * <p>⚠️ <strong>Out of view is "no such issue", never "forbidden".</strong> The same answer
     * {@code IssueTool.requireLinkableIssue} gives and for the same reason: a refusal that distinguished
     * the two would confirm that an issue exists in a project the caller cannot see, which is exactly
     * what {@code IssueAssembler.visibleReferenceOf} redacts a reference to avoid saying.
     *
     * <p>⚠️ <strong>Asked of the project, not of a list of projects.</strong>
     * {@code visibleProjectIds} answers with an empty list for a caller who browses everything
     * installation-wide — see its javadoc — so filtering against it would refuse the one caller who may
     * do the most.
     */
    private Issue requireLinkableIssue(Member caller, String issueId) {
        Issue issue = requireIssue(issueId);

        if (!projectAccess.holds(caller, issue.getProjectId(), Permissions.BROWSE_PROJECT)) {
            throw new ResourceNotFoundException("Issue not found: " + issueId);
        }

        return issue;
    }

    /** A link is reachable from either end, and from nowhere else — the same rule {@code removeLink} applies. */
    private IssueLink requireLinkOnIssue(String linkId, String issueId) {
        IssueLink link = issueLinkRepository.findById(linkId)
            .orElseThrow(() -> new ResourceNotFoundException("Link not found: " + linkId));

        if (!link.getSourceIssueId().equals(issueId) && !link.getTargetIssueId().equals(issueId)) {
            throw new ResourceNotFoundException("Link not found on this issue: " + linkId);
        }

        return link;
    }

    private String otherIssueKey(IssueLink link, String issueId) {
        String otherIssueId = link.getSourceIssueId().equals(issueId)
            ? link.getTargetIssueId()
            : link.getSourceIssueId();

        return issueRepository.findById(otherIssueId).map(Issue::getIssueKey).orElse(otherIssueId);
    }

}
