package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.InstanceSettings;
import net.innoventa.tessera.domain.IssueTypeScheme;
import net.innoventa.tessera.domain.WorkflowScheme;
import net.innoventa.tessera.dto.configuration.InstanceDefaultsRequest;
import net.innoventa.tessera.dto.configuration.InstanceDefaultsResponse;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.InstanceSettingsRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeRepository;
import net.innoventa.tessera.repository.WorkflowSchemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * What a new project starts on — read by project creation, written by the Defaults screen.
 *
 * <p>⚠️ <strong>This is the whole reason ticket 06 is more than CRUD.</strong> {@code ProjectService}
 * named two schemes as string constants, which was fine while schemes were seeded and read-only. Making
 * them editable turns those constants into a way to break project creation from a settings page: delete
 * or rename the wrong scheme and nothing complains until somebody creates a project, at which point the
 * error names an identifier nobody recognises. A stored, foreign-keyed setting cannot get into that
 * state — the delete is refused instead, at the moment it is attempted, naming what it is.
 *
 * <p>Everything a caller might want to know about a default is here rather than spread between here and
 * whoever holds the {@link InstanceSettings} row: {@link #issueTypeSchemeId()} for creation,
 * {@link #isDefaultIssueTypeScheme} for the refusals, {@link #read()} for the screen.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstanceDefaults {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceDefaults.class);

    private final InstanceSettingsRepository instanceSettingsRepository;
    private final IssueTypeSchemeRepository  issueTypeSchemeRepository;
    private final WorkflowSchemeRepository   workflowSchemeRepository;

    /** The issue-type scheme a project is created on. */
    public String issueTypeSchemeId() {
        return require().getDefaultIssueTypeSchemeId();
    }

    /** The workflow scheme a project is created on. */
    public String workflowSchemeId() {
        return require().getDefaultWorkflowSchemeId();
    }

    public boolean isDefaultIssueTypeScheme(String schemeId) {
        return require().getDefaultIssueTypeSchemeId().equals(schemeId);
    }

    public boolean isDefaultWorkflowScheme(String schemeId) {
        return require().getDefaultWorkflowSchemeId().equals(schemeId);
    }

    public InstanceDefaultsResponse read() {
        InstanceSettings settings = require();

        return new InstanceDefaultsResponse(
            settings.getDefaultIssueTypeSchemeId(),
            issueTypeSchemeName(settings.getDefaultIssueTypeSchemeId()),
            settings.getDefaultWorkflowSchemeId(),
            workflowSchemeName(settings.getDefaultWorkflowSchemeId()));
    }

    @Transactional
    public InstanceDefaultsResponse update(InstanceDefaultsRequest request) {
        InstanceSettings settings = require();

        IssueTypeScheme issueTypeScheme = issueTypeSchemeRepository
            .findById(request.defaultIssueTypeSchemeId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Issue type scheme not found: " + request.defaultIssueTypeSchemeId()));

        WorkflowScheme workflowScheme = workflowSchemeRepository
            .findById(request.defaultWorkflowSchemeId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Workflow scheme not found: " + request.defaultWorkflowSchemeId()));

        LOGGER.info("New projects will start on '{}' and '{}' — existing projects keep the schemes they "
                    + "were created on", issueTypeScheme.getName(), workflowScheme.getName());

        settings.setDefaultIssueTypeSchemeId(issueTypeScheme.getId());
        settings.setDefaultWorkflowSchemeId(workflowScheme.getId());
        settings.setUpdatedAt(LocalDateTime.now());

        return read();
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * ⚠️ The row is seeded by V000015 and constrained to be the only one, so its absence is a broken
     * installation rather than a case to have an opinion about. Failing loudly beats every caller
     * inventing a fallback, which is what an {@code Optional} here would produce.
     */
    private InstanceSettings require() {
        return instanceSettingsRepository.findById(InstanceSettings.ID)
            .orElseThrow(() -> new IllegalStateException(
                "The instance settings row is missing. V000015 seeds it; an installation without it "
                + "cannot say what schemes a new project starts on."));
    }

    private String issueTypeSchemeName(String schemeId) {
        return issueTypeSchemeRepository.findById(schemeId).map(IssueTypeScheme::getName).orElse(null);
    }

    private String workflowSchemeName(String schemeId) {
        return workflowSchemeRepository.findById(schemeId).map(WorkflowScheme::getName).orElse(null);
    }

}
