package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Sprint;
import net.innoventa.tessera.domain.SprintIssue;
import net.innoventa.tessera.domain.SprintState;
import net.innoventa.tessera.dto.block.BlockStatus;
import net.innoventa.tessera.dto.block.PageBlockView;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.repository.SprintRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import net.innoventa.tessera.service.block.spi.BlockRequest;
import net.innoventa.tessera.service.block.spi.PageBlockResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * {@code :::sprint TSSR} — a project's running sprint, or a named one (TSSR-18).
 *
 * <p>Two argument forms, and the short one is the one worth having: a page that says
 * {@code :::sprint TSSR} shows whatever this team is doing <em>now</em>, which is the thing a written
 * plan goes stale about first. {@code :::sprint TSSR/Sprint 3} names one instead, for a retrospective
 * that has to keep pointing at the sprint it is about.
 *
 * <p>⚠️ <strong>Every number is counted from the sprint's current members, on read.</strong> That is
 * this product's rule for sprint reporting (ADR-0013) and it matters more here than anywhere: a block
 * that cached its own totals would be the one place on a screen where the numbers could disagree with
 * the sprint report sitting beside them.
 *
 * <p>⚠️ <strong>Committed points are the points as they are now, not as they were at commitment.</strong>
 * {@code SprintIssue.storyPointsAtAdd} is the frozen figure the sprint report uses to say what was
 * signed up for; a page asking "how big is this sprint" is asking about the work in front of the team.
 * The two differ exactly when scope changed mid-sprint, which is the sprint report's story to tell.
 */
@Component
@RequiredArgsConstructor
public class SprintBlockResolver implements PageBlockResolver {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final SprintMembershipService sprintMembershipService;
    private final ProjectAccess projectAccess;

    @Override
    public String directive() {
        return "sprint";
    }

    @Override
    public PageBlockView resolve(BlockRequest request) {
        String argument = request.argument();
        int separator = argument.indexOf('/');
        String projectKey = (separator < 0 ? argument : argument.substring(0, separator)).trim();
        String sprintName = separator < 0 ? null : argument.substring(separator + 1).trim();

        Project project = visibleProject(request.caller(), projectKey);

        if (project == null) {
            return PageBlockView.miss(directive(), argument, BlockStatus.NOT_FOUND);
        }

        Sprint sprint = sprintName == null || sprintName.isEmpty()
            ? sprintRepository.findFirstByProjectIdAndState(project.getId(), SprintState.ACTIVE).orElse(null)
            : named(project.getId(), sprintName);

        if (sprint == null) {
            return PageBlockView.miss(directive(), argument, BlockStatus.NOT_FOUND);
        }

        return PageBlockView.of(directive(), argument, describe(project, sprint));
    }

    private PageBlockView.SprintBlock describe(Project project, Sprint sprint) {
        List<String> issueIds = sprintMembershipService.currentMembers(sprint.getId()).stream()
            .map(SprintIssue::getIssueId)
            .toList();

        List<Issue> issues = issueIds.isEmpty() ? List.of() : issueRepository.findAllById(issueIds);

        // ⚠️ Finished is `resolutionId != null`, never a status name (ADR-0004).
        List<Issue> completed = issues.stream().filter(issue -> issue.getResolutionId() != null).toList();

        return new PageBlockView.SprintBlock(
            project.getKey(),
            sprint.getName(),
            sprint.getGoal(),
            sprint.getState().name(),
            sprint.getEndDate(),
            issues.size(),
            completed.size(),
            pointsOf(issues),
            pointsOf(completed));
    }

    /**
     * The points on these issues, or null where none of them carries any.
     *
     * <p>⚠️ Null rather than zero, because a project that does not estimate has no story points at all —
     * and "0 points" beside eight issues reads as a team that committed to nothing.
     */
    private static Double pointsOf(List<Issue> issues) {
        boolean anyEstimated = issues.stream().anyMatch(issue -> issue.getStoryPoints() != null);

        if (!anyEstimated) {
            return null;
        }

        return issues.stream()
            .map(Issue::getStoryPoints)
            .filter(points -> points != null)
            .mapToDouble(Double::doubleValue)
            .sum();
    }

    private Sprint named(String projectId, String sprintName) {
        return sprintRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
            .filter(sprint -> sprint.getName().equalsIgnoreCase(sprintName))
            .findFirst()
            .orElse(null);
    }

    /**
     * The project this key names, if the reader may see it — null covering both "no such project" and
     * "not yours", which are deliberately the same answer (see {@link IssueBlockResolver}).
     */
    private Project visibleProject(Member caller, String projectKey) {
        return projectRepository.findByKey(projectKey.toUpperCase(Locale.ROOT))
            .filter(project -> projectAccess.holds(caller, project.getId(), Permissions.BROWSE_PROJECT))
            .orElse(null);
    }

}
