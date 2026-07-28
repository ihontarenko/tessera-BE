package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Component;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueComponent;
import net.innoventa.tessera.domain.IssueLabel;
import net.innoventa.tessera.domain.IssueVersion;
import net.innoventa.tessera.domain.Label;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Version;
import net.innoventa.tessera.domain.VersionLinkKind;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.UpdateIssueOrganizationRequest;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.ComponentRepository;
import net.innoventa.tessera.repository.IssueComponentRepository;
import net.innoventa.tessera.repository.IssueLabelRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueVersionRepository;
import net.innoventa.tessera.repository.LabelRepository;
import net.innoventa.tessera.repository.VersionRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An issue's organization associations (ticket 11): free-text labels (global strings created on the
 * fly), components, and the two distinct version associations (affects / fix). Each PUT replaces the
 * whole set for that association, so the UI sends the desired final state. Components and versions are
 * validated to belong to the issue's project. Every change is recorded to the activity log as a
 * before/after of the joined names.
 */
@Service
@RequiredArgsConstructor
public class IssueOrganizationService {

    static final String FIELD_LABELS = "labels";
    static final String FIELD_COMPONENTS = "components";
    static final String FIELD_AFFECTS_VERSIONS = "affectsVersions";
    static final String FIELD_FIX_VERSIONS = "fixVersions";

    private final IssueRepository issueRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final LabelRepository labelRepository;
    private final IssueComponentRepository issueComponentRepository;
    private final ComponentRepository componentRepository;
    private final IssueVersionRepository issueVersionRepository;
    private final VersionRepository versionRepository;
    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final ActivityLogService activityLogService;
    private final IssueAssembler issueAssembler;
    private final java.util.function.Supplier<String> idGenerator;

    @Transactional
    public IssueResponse update(Jwt jwt, String issueId, UpdateIssueOrganizationRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());
        projectPermissionService.require(caller, issue.getProjectId(), Permissions.EDIT_ISSUE);

        ActivityLogService.ChangeSet changes = activityLogService.changeSet()
            .compare(FIELD_LABELS, currentLabels(issueId), joinedLabels(request.labelsOrEmpty()))
            .compare(FIELD_COMPONENTS, currentComponents(issueId), joinedComponents(request.componentIdsOrEmpty(), issue.getProjectId()))
            .compare(FIELD_AFFECTS_VERSIONS, currentVersions(issueId, VersionLinkKind.AFFECTS),
                joinedVersions(request.affectsVersionIdsOrEmpty(), issue.getProjectId()))
            .compare(FIELD_FIX_VERSIONS, currentVersions(issueId, VersionLinkKind.FIX),
                joinedVersions(request.fixVersionIdsOrEmpty(), issue.getProjectId()));

        replaceLabels(issueId, request.labelsOrEmpty());
        replaceComponents(issue, request.componentIdsOrEmpty());
        replaceVersions(issue, VersionLinkKind.AFFECTS, request.affectsVersionIdsOrEmpty());
        replaceVersions(issue, VersionLinkKind.FIX, request.fixVersionIdsOrEmpty());

        activityLogService.record(issueId, caller.getId(), changes);

        return issueAssembler.detail(issue, project);
    }

    // ── Labels ──────────────────────────────────────────────────────────────────

    private void replaceLabels(String issueId, List<String> rawLabels) {
        issueLabelRepository.deleteByIssueId(issueId);
        issueLabelRepository.flush();

        normalizedLabels(rawLabels).forEach(name -> {
            Label label = resolveLabel(name);
            issueLabelRepository.save(IssueLabel.builder()
                .id(idGenerator.get())
                .issueId(issueId)
                .labelId(label.getId())
                .build());
        });
    }

    private Label resolveLabel(String name) {
        return labelRepository.findByName(name)
            .orElseGet(() -> labelRepository.save(Label.builder().id(idGenerator.get()).name(name).build()));
    }

    private List<String> normalizedLabels(List<String> rawLabels) {
        return rawLabels.stream()
            .filter(label -> label != null)
            .map(String::trim)
            .filter(label -> !label.isBlank())
            .distinct()
            .toList();
    }

    private String currentLabels(String issueId) {
        List<String> names = issueLabelRepository.findByIssueId(issueId).stream()
            .map(issueLabel -> labelRepository.findById(issueLabel.getLabelId()).map(Label::getName).orElse(null))
            .filter(name -> name != null)
            .toList();
        return joinSorted(names);
    }

    private String joinedLabels(List<String> rawLabels) {
        return joinSorted(normalizedLabels(rawLabels));
    }

    // ── Components ────────────────────────────────────────────────────────────────

    private void replaceComponents(Issue issue, List<String> componentIds) {
        issueComponentRepository.deleteByIssueId(issue.getId());
        issueComponentRepository.flush();

        requireProjectComponents(componentIds, issue.getProjectId()).forEach(component ->
            issueComponentRepository.save(IssueComponent.builder()
                .id(idGenerator.get())
                .issueId(issue.getId())
                .componentId(component.getId())
                .build()));
    }

    private String currentComponents(String issueId) {
        List<String> names = issueComponentRepository.findByIssueId(issueId).stream()
            .map(issueComponent -> componentRepository.findById(issueComponent.getComponentId()).map(Component::getName).orElse(null))
            .filter(name -> name != null)
            .toList();
        return joinSorted(names);
    }

    private String joinedComponents(List<String> componentIds, String projectId) {
        List<String> names = requireProjectComponents(componentIds, projectId).stream()
            .map(Component::getName)
            .toList();
        return joinSorted(names);
    }

    private List<Component> requireProjectComponents(List<String> componentIds, String projectId) {
        List<String> distinctIds = componentIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        List<Component> found = componentRepository.findByIdInAndProjectId(distinctIds, projectId);
        if (found.size() != distinctIds.size()) {
            throw new ResourceNotFoundException("One or more components do not belong to this project");
        }
        return found;
    }

    // ── Versions ──────────────────────────────────────────────────────────────────

    private void replaceVersions(Issue issue, VersionLinkKind kind, List<String> versionIds) {
        issueVersionRepository.findByIssueIdAndLinkKind(issue.getId(), kind)
            .forEach(issueVersionRepository::delete);
        issueVersionRepository.flush();

        requireProjectVersions(versionIds, issue.getProjectId()).forEach(version ->
            issueVersionRepository.save(IssueVersion.builder()
                .id(idGenerator.get())
                .issueId(issue.getId())
                .versionId(version.getId())
                .linkKind(kind)
                .build()));
    }

    private String currentVersions(String issueId, VersionLinkKind kind) {
        List<String> names = issueVersionRepository.findByIssueIdAndLinkKind(issueId, kind).stream()
            .map(issueVersion -> versionRepository.findById(issueVersion.getVersionId()).map(Version::getName).orElse(null))
            .filter(name -> name != null)
            .toList();
        return joinSorted(names);
    }

    private String joinedVersions(List<String> versionIds, String projectId) {
        List<String> names = requireProjectVersions(versionIds, projectId).stream()
            .map(Version::getName)
            .toList();
        return joinSorted(names);
    }

    private List<Version> requireProjectVersions(List<String> versionIds, String projectId) {
        List<String> distinctIds = versionIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        List<Version> found = versionRepository.findByIdInAndProjectId(distinctIds, projectId);
        if (found.size() != distinctIds.size()) {
            throw new ResourceNotFoundException("One or more versions do not belong to this project");
        }
        return found;
    }

    // ── Shared ────────────────────────────────────────────────────────────────────

    private String joinSorted(List<String> names) {
        String joined = names.stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    private Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

}
