package net.innoventa.tessera.service.report;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.SprintIssue;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.SprintIssueRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The I/O half of reporting (ADR-0013): membership rows joined to their issues and handed over as the
 * flat {@link SprintMemberFact} rows {@link SprintReportProjection} consumes. It is a batch loader on
 * purpose — one sprint's report and a whole project's velocity are the same read at different widths,
 * so neither costs a query per sprint and certainly not one per issue.
 * <p>
 * Every row is loaded, including memberships that already ended: the report needs the work that left,
 * which is exactly the part the board's "current members" view deliberately hides.
 */
@Component
@RequiredArgsConstructor
public class SprintFactLoader {

    private final SprintIssueRepository sprintIssueRepository;
    private final IssueRepository issueRepository;

    public LoadedFacts load(Collection<String> sprintIds) {
        if (sprintIds.isEmpty()) {
            return new LoadedFacts(Map.of(), Map.of());
        }

        List<SprintIssue> memberships = sprintIssueRepository.findBySprintIdIn(sprintIds);
        Map<String, Issue> issues = issueRepository
            .findAllById(memberships.stream().map(SprintIssue::getIssueId).distinct().toList()).stream()
            .collect(Collectors.toMap(Issue::getId, Function.identity()));

        Map<String, List<SprintMemberFact>> factsBySprintId = memberships.stream()
            // A membership whose issue has since been deleted describes nothing, and valuing it at zero
            // would quietly shrink a closed sprint's commitment after the fact.
            .filter(membership -> issues.containsKey(membership.getIssueId()))
            .sorted(Comparator.comparingInt((SprintIssue membership) -> issues.get(membership.getIssueId()).getSequence()))
            .collect(Collectors.groupingBy(
                SprintIssue::getSprintId,
                Collectors.mapping(membership -> toFact(membership, issues.get(membership.getIssueId())), Collectors.toList())));

        return new LoadedFacts(factsBySprintId, issues);
    }

    /** A membership row plus the one thing about its issue the projection cares about: when it was resolved. */
    private SprintMemberFact toFact(SprintIssue membership, Issue issue) {
        return new SprintMemberFact(
            membership.getIssueId(),
            membership.getAddedAt(),
            membership.getRemovedAt(),
            membership.getStoryPointsAtAdd(),
            issue.getResolvedAt()
        );
    }

    /**
     * What one load yields: the projection's inputs per sprint, and the issues behind them for a caller
     * that also has to render report lines. Velocity uses only the first half.
     *
     * @param factsBySprintId in issue order within each sprint, so a report's buckets come out in the
     *                        order the project ranks its issues rather than in insertion order
     * @param issues          every issue any of those rows refers to, keyed by id
     */
    public record LoadedFacts(Map<String, List<SprintMemberFact>> factsBySprintId, Map<String, Issue> issues) {

        /** A sprint with no surviving membership rows is an empty sprint, never a missing one. */
        public List<SprintMemberFact> factsFor(String sprintId) {
            return factsBySprintId.getOrDefault(sprintId, List.of());
        }

    }

}
