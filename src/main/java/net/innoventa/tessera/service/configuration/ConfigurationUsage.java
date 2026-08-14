package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.IssueTypeScheme;
import net.innoventa.tessera.domain.IssueTypeSchemeItem;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Transition;
import net.innoventa.tessera.domain.Workflow;
import net.innoventa.tessera.domain.WorkflowScheme;
import net.innoventa.tessera.domain.WorkflowSchemeItem;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport.Holder;
import net.innoventa.tessera.repository.BoardColumnStatusRepository;
import net.innoventa.tessera.repository.CountByKey;
import net.innoventa.tessera.repository.IssueLinkRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeItemRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.repository.TransitionRepository;
import net.innoventa.tessera.repository.WorkflowRepository;
import net.innoventa.tessera.repository.WorkflowSchemeItemRepository;
import net.innoventa.tessera.repository.WorkflowSchemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one place that answers <em>what holds this configuration row</em>.
 *
 * <p>⚠️ <strong>Every refusal and every "used by" panel is this one read.</strong> The alternative —
 * each write service counting its own holders — produces two implementations of one question, and they
 * drift in the worst possible direction: the panel says a status is free, Delete refuses it, and the
 * screen has told an administrator two different things about the same row in two clicks.
 *
 * <p>It answers and never decides. Whether a non-empty report is a refusal, a warning or a note is the
 * caller's judgement — deleting a status in use is refused, changing its category is merely reported —
 * and a class that knew which would be a class every write service had to be read alongside.
 *
 * <p>Counts are named rather than merely tallied wherever the name is small and useful: a project key
 * or a scheme name is what somebody navigates by, and "held by 12 issues" without saying where is a
 * fact nobody can act on. Issues are counted and not listed, because twelve of them is a number and
 * twelve thousand is the same number.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfigurationUsage {

    /** How many holders of a kind are named before the list becomes noise rather than a signpost. */
    private static final int NAMES_SHOWN = 8;

    private final IssueRepository               issueRepository;
    private final IssueLinkRepository           issueLinkRepository;
    private final BoardColumnStatusRepository   boardColumnStatusRepository;
    private final TransitionRepository          transitionRepository;
    private final WorkflowRepository            workflowRepository;
    private final WorkflowSchemeRepository      workflowSchemeRepository;
    private final WorkflowSchemeItemRepository  workflowSchemeItemRepository;
    private final IssueTypeSchemeRepository     issueTypeSchemeRepository;
    private final IssueTypeSchemeItemRepository issueTypeSchemeItemRepository;
    private final ProjectRepository             projectRepository;

    // ── The flat catalogs ─────────────────────────────────────────────────────

    /** An issue must have a priority to exist, so issues are the only thing that can hold one. */
    public ConfigurationUsageReport ofPriority(String priorityId) {
        return report(issuesHolding(
            issueRepository.countByPriorityId(priorityId),
            issueRepository.countProjectsHoldingPriority(priorityId)));
    }

    /** A resolution is set by a transition and cleared by one; only issues carry it. */
    public ConfigurationUsageReport ofResolution(String resolutionId) {
        return report(issuesHolding(
            issueRepository.countByResolutionId(resolutionId),
            issueRepository.countProjectsHoldingResolution(resolutionId)));
    }

    public ConfigurationUsageReport ofLinkType(String linkTypeId) {
        long links = issueLinkRepository.countByLinkTypeId(linkTypeId);

        return report(links == 0 ? null
            : Holder.of("issueLinks", links, links + " issue " + plural("link", links)));
    }

    // ── Statuses ──────────────────────────────────────────────────────────────

    /**
     * Three kinds of holder, and they refuse for three different reasons: an issue <em>is</em> in this
     * status, a board column maps it, and a transition names it as an endpoint. The last is why a status
     * usually cannot be deleted at all — a status belongs to a workflow only through its edges, so any
     * status a workflow actually uses has at least one.
     */
    public ConfigurationUsageReport ofStatus(String statusId) {
        List<Transition> edges = transitionRepository.findByFromStatusIdOrToStatusId(statusId, statusId);
        List<String> workflows = edges.isEmpty() ? List.of() : workflowNames(edges);
        long boardColumns = boardColumnStatusRepository.findByStatusId(statusId).size();

        return report(
            issuesHolding(
                issueRepository.countByStatusId(statusId),
                issueRepository.countProjectsHoldingStatus(statusId)),
            boardColumns == 0 ? null : Holder.of(
                "boardColumns", boardColumns,
                boardColumns + " board " + plural("column", boardColumns) + " mapping it"),
            edges.isEmpty() ? null : new Holder(
                "transitions", edges.size(),
                edges.size() + " " + plural("transition", edges.size())
                + " in " + workflows.size() + " " + plural("workflow", workflows.size()),
                names(workflows)));
    }

    // ── Issue types ───────────────────────────────────────────────────────────

    /**
     * Issues of the type, the issue-type schemes granting it — including any that <em>preselects</em>
     * it, which is a reference the item rows do not carry — and the per-type workflow overrides naming
     * it.
     */
    public ConfigurationUsageReport ofIssueType(String issueTypeId) {
        List<IssueTypeScheme> schemes = issueTypeSchemesGranting(issueTypeId);
        List<WorkflowSchemeItem> overrides = workflowSchemeItemRepository.findByIssueTypeId(issueTypeId);

        return report(
            issuesHolding(
                issueRepository.countByIssueTypeId(issueTypeId),
                issueRepository.countProjectsHoldingIssueType(issueTypeId)),
            schemes.isEmpty() ? null : new Holder(
                "schemes", schemes.size(),
                schemes.size() + " issue-type " + plural("scheme", schemes.size()),
                names(schemes.stream().map(IssueTypeScheme::getName).toList())),
            overrides.isEmpty() ? null : Holder.of(
                "workflowOverrides", overrides.size(),
                overrides.size() + " workflow-scheme " + plural("override", overrides.size())));
    }

    /** Every issue-type scheme this type is in, whether as a granted item or as the scheme's default. */
    public List<IssueTypeScheme> issueTypeSchemesGranting(String issueTypeId) {
        Set<String> schemeIds = new LinkedHashSet<>(
            issueTypeSchemeItemRepository.findByIssueTypeId(issueTypeId).stream()
                .map(IssueTypeSchemeItem::getSchemeId)
                .toList());

        issueTypeSchemeRepository.findByDefaultIssueTypeId(issueTypeId)
            .forEach(scheme -> schemeIds.add(scheme.getId()));

        return issueTypeSchemeRepository.findAllById(schemeIds).stream()
            .sorted(Comparator.comparing(IssueTypeScheme::getName))
            .toList();
    }

    // ── Workflows ─────────────────────────────────────────────────────────────

    /**
     * A workflow is held by the schemes referencing it, and through them by projects.
     *
     * <p>Both are reported, and the projects are the half that matters: a scheme is a name an
     * administrator may not recognise, while "OPS, WEB" is the answer to "whose day am I about to
     * affect".
     */
    public ConfigurationUsageReport ofWorkflow(String workflowId) {
        List<WorkflowScheme> schemes = workflowSchemesUsing(workflowId);
        List<Project> projects = projectsOnWorkflow(workflowId);

        return report(
            schemes.isEmpty() ? null : new Holder(
                "schemes", schemes.size(),
                schemes.size() + " workflow " + plural("scheme", schemes.size()),
                names(schemes.stream().map(WorkflowScheme::getName).toList())),
            projects.isEmpty() ? null : new Holder(
                "projects", projects.size(),
                projects.size() + " " + plural("project", projects.size()),
                names(projects.stream().map(Project::getKey).toList())));
    }

    /** Every scheme pointing at this workflow, as its default or through a per-type override. */
    public List<WorkflowScheme> workflowSchemesUsing(String workflowId) {
        Set<String> schemeIds = new LinkedHashSet<>(
            workflowSchemeRepository.findByDefaultWorkflowId(workflowId).stream()
                .map(WorkflowScheme::getId)
                .toList());

        workflowSchemeItemRepository.findByWorkflowId(workflowId)
            .forEach(item -> schemeIds.add(item.getSchemeId()));

        return workflowSchemeRepository.findAllById(schemeIds).stream()
            .sorted(Comparator.comparing(WorkflowScheme::getName))
            .toList();
    }

    /** The projects a workflow change actually reaches — the ones whose scheme names it. */
    public List<Project> projectsOnWorkflow(String workflowId) {
        List<String> schemeIds = workflowSchemesUsing(workflowId).stream()
            .map(WorkflowScheme::getId)
            .toList();

        return schemeIds.isEmpty()
            ? List.of()
            : projectRepository.findByWorkflowSchemeIdInOrderByKeyAsc(schemeIds);
    }

    // ── Bulk, for the Administration screen ───────────────────────────────────

    /**
     * Every catalog's issue counts in five queries rather than one per row.
     *
     * <p>The screen shows a number beside every status and every issue type at once. Asking
     * {@link #ofStatus} per row would be correct and would turn a catalog of thirty into sixty round
     * trips, so the page-level read is grouped and the per-row read stays for the moment somebody is
     * about to delete something.
     */
    public ConfigurationCounts counts() {
        return new ConfigurationCounts(
            tally(issueRepository.countIssuesByStatus()),
            tally(issueRepository.countIssuesByIssueType()),
            tally(issueRepository.countIssuesByPriority()),
            tally(issueRepository.countIssuesByResolution()),
            tally(issueLinkRepository.countLinksByLinkType()));
    }

    /**
     * How many issues (or links) each catalog row holds, by row id.
     *
     * <p>⚠️ A row nothing holds is <strong>absent rather than zero</strong> — a {@code group by} has no
     * row for an empty group. Readers use {@code getOrDefault(id, 0L)}.
     */
    public record ConfigurationCounts(
        Map<String, Long> issuesByStatus,
        Map<String, Long> issuesByIssueType,
        Map<String, Long> issuesByPriority,
        Map<String, Long> issuesByResolution,
        Map<String, Long> linksByLinkType
    ) {
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * "12 issues in 3 projects", or nothing at all when no issue holds the row.
     *
     * <p>The project count is folded into the phrase rather than reported as its own holder because it
     * is not one: those projects hold the row only by way of their issues, and listing them separately
     * would read as two independent reasons deletion is refused.
     */
    private Holder issuesHolding(long issues, long projects) {
        if (issues == 0) {
            return null;
        }

        return Holder.of("issues", issues,
            issues + " " + plural("issue", issues) + " in " + projects + " " + plural("project", projects));
    }

    /** Assembles a report out of holders that may each be absent, which is most of them. */
    private ConfigurationUsageReport report(Holder... holders) {
        List<Holder> present = new ArrayList<>();

        for (Holder holder : holders) {
            if (holder != null) {
                present.add(holder);
            }
        }

        return new ConfigurationUsageReport(List.copyOf(present));
    }

    private List<String> workflowNames(List<Transition> edges) {
        List<String> workflowIds = edges.stream().map(Transition::getWorkflowId).distinct().toList();

        return workflowRepository.findAllById(workflowIds).stream()
            .map(Workflow::getName)
            .sorted()
            .toList();
    }

    /** The first few, plus a count of the rest — a signpost, never the whole list. */
    private List<String> names(List<String> all) {
        if (all.size() <= NAMES_SHOWN) {
            return List.copyOf(all);
        }

        List<String> shown = new ArrayList<>(all.subList(0, NAMES_SHOWN));
        shown.add("and " + (all.size() - NAMES_SHOWN) + " more");

        return List.copyOf(shown);
    }

    private static Map<String, Long> tally(List<CountByKey> rows) {
        return rows.stream()
            // ⚠️ The null bucket is dropped: a grouping over a nullable column has one, and "how many
            // issues have no resolution" is not a fact about any resolution row.
            .filter(row -> row.key() != null)
            .collect(Collectors.toMap(CountByKey::key, CountByKey::count));
    }

    private static String plural(String noun, long count) {
        return count == 1 ? noun : noun + "s";
    }

}
