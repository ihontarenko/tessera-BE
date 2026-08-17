package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueLabel;
import net.innoventa.tessera.domain.Label;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.UpdateIssueOrganizationRequest;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueLabelRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.LabelRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An issue's labels (ticket 11): global free-text strings, created on the fly and shared across
 * projects. The PUT replaces the whole set, so the UI sends the desired final state rather than a
 * delta. The change is recorded to the activity log as a before/after of the joined names.
 * <p>
 * This once also carried components and the two version associations; both were removed with the
 * release-tracking model they belonged to (ADR-0017), leaving labels as the one grouping mechanism.
 */
@Service
@RequiredArgsConstructor
public class IssueOrganizationService {

    static final String FIELD_LABELS = "labels";

    private final IssueRepository issueRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final LabelRepository labelRepository;
    private final ProjectService projectService;
    private final MemberService memberService;
    private final ActivityLogService activityLogService;
    private final IssueAssembler issueAssembler;
    private final java.util.function.Supplier<String> idGenerator;

    @Transactional
    public IssueResponse update(Jwt jwt, String issueId, UpdateIssueOrganizationRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Issue issue = requireIssue(issueId);
        Project project = projectService.requireProject(issue.getProjectId());

        ActivityLogService.ChangeSet changes = activityLogService.changeSet()
            .compare(FIELD_LABELS, currentLabels(issueId), joinedLabels(request.resolveLabels()));

        replaceLabels(issueId, request.resolveLabels());

        activityLogService.record(issueId, caller.getId(), changes);

        return issueAssembler.detail(issue, project, caller);
    }

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
