package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.IssueTypeScheme;
import net.innoventa.tessera.domain.IssueTypeSchemeItem;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.configuration.IssueTypeResponse;
import net.innoventa.tessera.dto.project.ProjectIssueTypesResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeItemRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which issue types a project may create — the one place that reads a project's {@code IssueTypeScheme}
 * as a set of permitted types.
 * <p>
 * It answers that question twice, for two callers who must never disagree: {@link #available} lets the
 * create dialog offer the right list, and {@link #requireCreatable} refuses anything outside it at the
 * API. Splitting those across two classes is how a UI ends up offering a type the backend rejects, or
 * — worse — accepting one it never offered.
 * <p>
 * The scheme is global and shareable (ADR-0001), so this reads it through the project rather than
 * caching anything: two projects may hold the same scheme, and editing that scheme must take effect
 * for both immediately.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectIssueTypeService {

    private final IssueTypeSchemeRepository issueTypeSchemeRepository;
    private final IssueTypeSchemeItemRepository issueTypeSchemeItemRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;

    /** The types {@code projectId} may create, scheme-ordered, plus the default to preselect. */
    public ProjectIssueTypesResponse listCreatableIssueTypes(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectPermissionService.requireVisible(caller.getId(), projectId);

        Project project = projectService.requireProject(projectId);
        List<IssueType> granted = grantedTypes(project);

        List<IssueTypeResponse> issueTypes = granted.stream()
            .map(this::toResponse)
            .toList();

        return new ProjectIssueTypesResponse(issueTypes, defaultTypeIdWithin(project, granted));
    }

    /**
     * Accept {@code issueTypeId} for creation in {@code project}, or refuse it.
     * <p>
     * The two refusals are deliberately different: a type that does not exist at all is a {@code 404},
     * unchanged from before schemes were enforced, while a real type the project's scheme does not
     * grant is a {@code 409} — a rule violation rather than a missing thing, matching how the hierarchy
     * invariant already answers.
     * <p>
     * Checked against the same {@link #grantedTypes} list the read offers, not against the raw scheme
     * items. The two must not be able to disagree: offering a type creation would refuse, or accepting
     * one that was never offered, is exactly the bug this class exists to make impossible.
     */
    public void requireCreatable(Project project, String issueTypeId) {
        if (!issueTypeRepository.existsById(issueTypeId)) {
            throw new ResourceNotFoundException("Issue type not found: " + issueTypeId);
        }

        boolean granted = grantedTypes(project).stream()
            .anyMatch(issueType -> issueType.getId().equals(issueTypeId));

        if (!granted) {
            throw new BusinessRuleViolationException(
                "This project's issue type scheme does not offer issue type %s".formatted(issueTypeId));
        }
    }

    /**
     * The scheme's types in the scheme's own order. The items carry the ordering and the types carry
     * everything else, so the items drive the sequence and the type lookup is a single batched read
     * rather than one query per row.
     * <p>
     * An item whose type no longer resolves is dropped. The foreign key on {@code issue_type_scheme_items}
     * should make that unreachable; dropping rather than failing means a scheme that somehow lost a type
     * still offers the rest, and {@link #requireCreatable} reads the same list so it cannot accept what
     * the read did not offer.
     */
    private List<IssueType> grantedTypes(Project project) {
        List<IssueTypeSchemeItem> items =
            issueTypeSchemeItemRepository.findBySchemeIdOrderBySequenceAsc(project.getIssueTypeSchemeId());

        Map<String, IssueType> typesById = issueTypeRepository
            .findAllById(items.stream().map(IssueTypeSchemeItem::getIssueTypeId).toList()).stream()
            .collect(Collectors.toMap(IssueType::getId, Function.identity()));

        return items.stream()
            .map(item -> typesById.get(item.getIssueTypeId()))
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * The type the create dialog should preselect: the scheme's own default, but only when the scheme
     * actually grants it, and otherwise the first type it does grant.
     * <p>
     * A scheme naming a default outside its own membership would otherwise have the API preselect a
     * type it would then refuse to create — the client cannot be the one to notice that.
     */
    private String defaultTypeIdWithin(Project project, List<IssueType> granted) {
        String declared = requireScheme(project).getDefaultIssueTypeId();

        boolean grantsDeclared = granted.stream()
            .anyMatch(issueType -> issueType.getId().equals(declared));

        if (grantsDeclared) {
            return declared;
        }

        return granted.isEmpty() ? null : granted.getFirst().getId();
    }

    private IssueTypeScheme requireScheme(Project project) {
        return issueTypeSchemeRepository.findById(project.getIssueTypeSchemeId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Issue type scheme not found: " + project.getIssueTypeSchemeId()));
    }

    private IssueTypeResponse toResponse(IssueType issueType) {
        return new IssueTypeResponse(
            issueType.getId(),
            issueType.getName(),
            issueType.getHierarchyLevel(),
            issueType.getIconKey(),
            issueType.getDescription());
    }

}
