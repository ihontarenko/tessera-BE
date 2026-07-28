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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Typed relationships between issues (ticket 12). A link is stored once as a directed
 * {@code source → target} row of a {@link LinkType}; the target renders the type's inward label from
 * the same record — there is no duplicate reverse row. Creating and removing a link both require
 * {@code EDIT_ISSUE} on the acting issue's project and are recorded to its activity log.
 */
@Service
@RequiredArgsConstructor
public class IssueLinkService {

    static final String FIELD_LINK = "link";

    private final IssueRepository issueRepository;
    private final IssueLinkRepository issueLinkRepository;
    private final LinkTypeRepository linkTypeRepository;
    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final ActivityLogService activityLogService;
    private final IssueAssembler issueAssembler;
    private final Supplier<String> idGenerator;

    @Transactional
    public IssueResponse addLink(Jwt jwt, String issueId, CreateIssueLinkRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Issue source = requireIssue(issueId);
        Project project = projectService.requireProject(source.getProjectId());
        projectPermissionService.require(caller, source.getProjectId(), Permissions.EDIT_ISSUE);

        Issue target = requireIssue(request.targetIssueId());
        if (target.getId().equals(source.getId())) {
            throw new BusinessRuleViolationException("An issue cannot be linked to itself");
        }

        LinkType linkType = linkTypeRepository.findById(request.linkTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("Link type not found: " + request.linkTypeId()));

        if (issueLinkRepository.existsBySourceIssueIdAndTargetIssueIdAndLinkTypeId(source.getId(), target.getId(), linkType.getId())) {
            throw new BusinessRuleViolationException("This link already exists");
        }

        issueLinkRepository.save(IssueLink.builder()
            .id(idGenerator.get())
            .sourceIssueId(source.getId())
            .targetIssueId(target.getId())
            .linkTypeId(linkType.getId())
            .build());

        activityLogService.record(source.getId(), caller.getId(),
            activityLogService.changeSet().added(FIELD_LINK, linkType.getOutwardLabel() + " " + target.getIssueKey()));

        return issueAssembler.detail(source, project);
    }

    @Transactional
    public IssueResponse removeLink(Jwt jwt, String issueId, String linkId) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());
        projectPermissionService.require(caller, issue.getProjectId(), Permissions.EDIT_ISSUE);

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

        return issueAssembler.detail(issue, project);
    }

    private Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

}
