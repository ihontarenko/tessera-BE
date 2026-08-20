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
import net.innoventa.tessera.security.access.ProjectAccess;
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
 *
 * <p>⚠️ <strong>A parent is in the child's project, unless its type is entitled to span them</strong>
 * (TSSR-56). That was an unconditional rule until a Hub at level 2 needed to hold work across projects,
 * and it is still the rule for everything below {@link #PROJECT_SPANNING_LEVEL} — every board, backlog
 * and epic lane is written against it.
 */
@Service
@RequiredArgsConstructor
public class IssueHierarchyService {

    static final String FIELD_PARENT = "parent";

    /**
     * The level at which a parent may hold work in <em>another</em> project (TSSR-56).
     *
     * <p>⚠️ <strong>Crossing is a property of the parent's type, not of parenthood.</strong> Epic (1)
     * over Story (0) stays firmly inside one project — which is what every board, backlog and epic lane
     * already relies on — and only a Hub or an Initiative at 2 reaches out. Making the rule "any parent,
     * any project" would have changed the meaning of hierarchy everywhere to buy one type its feature.
     */
    static final int PROJECT_SPANNING_LEVEL = 2;

    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final ProjectAccess projectAccess;
    private final ProjectService projectService;
    private final MemberService memberService;
    private final ActivityLogService activityLogService;
    private final IssueCatalog issueCatalog;
    private final IssueAssembler issueAssembler;

    @Transactional
    public IssueResponse setParent(Jwt jwt, String issueId, SetParentRequest request) {
        return setParent(memberService.resolveMember(jwt), issueId, request);
    }

    /**
     * The same, for a caller that is not an HTTP request — see {@code IssueService.create(Member)}.
     *
     * <p>⚠️ A tool call has no security context to resolve a {@code Jwt} out of, so an agent adopting an
     * issue into an epic needs this overload rather than the one above (TSSR-2).
     */
    @Transactional
    public IssueResponse setParent(Member caller, String issueId, SetParentRequest request) {
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());

        String newParentId = request.parentId();
        if (newParentId != null) {
            requireVisibleParent(caller, issue.getIssueTypeId(), newParentId, issue.getProjectId());
        }

        String oldParentKey = issueCatalog.issueKey(issue.getParentId());
        String newParentKey = issueCatalog.issueKey(newParentId);

        issue.setParentId(newParentId);

        activityLogService.record(issue.getId(), caller.getId(),
            activityLogService.changeSet().compare(FIELD_PARENT, oldParentKey, newParentKey));

        return issueAssembler.detail(issue, project, caller);
    }

    /**
     * Enforce that {@code parentId} is a legal parent for an issue of {@code childIssueTypeId} in
     * {@code projectId}: the parent exists, its type is strictly higher in the hierarchy, and it is
     * either in the same project or of a type entitled to span them. Reused by issue creation.
     * Illegal → 409, missing parent → 404.
     *
     * <p>⚠️ <strong>This does not ask whether the caller may see the parent</strong>, because it does not
     * know who the caller is. {@link #requireVisibleParent} is the overload that does, and every path
     * that has a caller uses it — a cross-project parent somebody cannot browse would otherwise be a way
     * to confirm that a key exists.
     */
    @Transactional(readOnly = true)
    public void validateParent(String childIssueTypeId, String parentId, String projectId) {
        Issue parent = requireIssue(parentId);

        IssueType childType = requireIssueType(childIssueTypeId);
        IssueType parentType = requireIssueType(parent.getIssueTypeId());

        if (parentType.getHierarchyLevel() <= childType.getHierarchyLevel()) {
            throw new BusinessRuleViolationException(
                "A parent's type must be strictly higher in the hierarchy than the child's ('"
                    + parentType.getName() + "' is not above '" + childType.getName() + "')");
        }

        if (!parent.getProjectId().equals(projectId)
            && parentType.getHierarchyLevel() < PROJECT_SPANNING_LEVEL) {
            throw new BusinessRuleViolationException(
                "A parent issue must be in the same project unless its type sits at level "
                    + PROJECT_SPANNING_LEVEL + " or above ('" + parentType.getName() + "' is at level "
                    + parentType.getHierarchyLevel() + ")");
        }
    }

    /**
     * The same rule, plus the one question it cannot answer on its own: may this caller see the parent?
     *
     * <p>⚠️ <strong>Only asked when the projects differ</strong>, and that is deliberate rather than an
     * optimisation. A caller acting inside a project has already been authorised there by the endpoint;
     * asking again would be a check that always passes, pretending to be a rule. What is genuinely new is
     * a parent <em>elsewhere</em> — and without this, naming a key and reading the refusal would tell
     * somebody whether that key exists in a project they were never given.
     *
     * <p>⚠️ And the answer is the same {@code NOTHING_TO_ACT_ON} shape the rest of the product uses for
     * an issue somebody may not reach: <em>no such issue</em>, never <em>forbidden</em>. The second
     * confirms what the first withholds.
     */
    @Transactional(readOnly = true)
    public void requireVisibleParent(Member caller, String childIssueTypeId, String parentId, String projectId) {
        Issue parent = requireIssue(parentId);

        if (!parent.getProjectId().equals(projectId)
            && !projectAccess.holds(caller, parent.getProjectId(), Permissions.BROWSE_PROJECT)) {
            throw new ResourceNotFoundException("Issue not found: " + parent.getIssueKey());
        }

        validateParent(childIssueTypeId, parentId, projectId);
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
