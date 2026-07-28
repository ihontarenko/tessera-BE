package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.SetParentRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single unified issue hierarchy (ticket 10): an issue's {@code parent} may only be an issue whose
 * type is <strong>strictly higher</strong> in {@code hierarchyLevel} (Epic=1 over Story=0 over
 * Sub-task=−1), so Epic→Story→Sub-task nests but nothing nonsensical does. Sub-task-ness is
 * {@code hierarchyLevel < 0}, never a boolean; adding an Initiative=2 level works with no schema
 * change. Strictly-decreasing levels down a chain make cycles impossible by construction.
 */
@Service
@RequiredArgsConstructor
public class IssueHierarchyService {

    static final String FIELD_PARENT = "parent";

    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final ActivityLogService activityLogService;
    private final IssueCatalog issueCatalog;
    private final IssueAssembler issueAssembler;

    @Transactional
    public IssueResponse setParent(Jwt jwt, String issueId, SetParentRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());
        projectPermissionService.require(caller, issue.getProjectId(), Permissions.EDIT_ISSUE);

        String newParentId = request.parentId();
        if (newParentId != null) {
            validateParent(issue.getIssueTypeId(), newParentId, issue.getProjectId());
        }

        String oldParentKey = issueCatalog.issueKey(issue.getParentId());
        String newParentKey = issueCatalog.issueKey(newParentId);

        issue.setParentId(newParentId);

        activityLogService.record(issue.getId(), caller.getId(),
            activityLogService.changeSet().compare(FIELD_PARENT, oldParentKey, newParentKey));

        return issueAssembler.detail(issue, project);
    }

    /**
     * Enforce that {@code parentId} is a legal parent for an issue of {@code childIssueTypeId} in
     * {@code projectId}: the parent exists, is in the same project, and its type is strictly higher in
     * the hierarchy. Reused by issue creation. Illegal → 409, missing parent → 404.
     */
    @Transactional(readOnly = true)
    public void validateParent(String childIssueTypeId, String parentId, String projectId) {
        Issue parent = requireIssue(parentId);

        if (!parent.getProjectId().equals(projectId)) {
            throw new BusinessRuleViolationException("A parent issue must be in the same project");
        }

        IssueType childType = requireIssueType(childIssueTypeId);
        IssueType parentType = requireIssueType(parent.getIssueTypeId());

        if (parentType.getHierarchyLevel() <= childType.getHierarchyLevel()) {
            throw new BusinessRuleViolationException(
                "A parent's type must be strictly higher in the hierarchy than the child's ('"
                    + parentType.getName() + "' is not above '" + childType.getName() + "')");
        }
    }

    private Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

    private IssueType requireIssueType(String issueTypeId) {
        return issueTypeRepository.findById(issueTypeId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue type not found: " + issueTypeId));
    }

}
