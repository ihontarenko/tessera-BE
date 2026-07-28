package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.ProjectIssueCounter;
import net.innoventa.tessera.repository.ProjectIssueCounterRepository;
import net.innoventa.tessera.service.key.IssueKeyStrategies;
import org.springframework.stereotype.Service;

/**
 * Allocates the next {@code (sequence, issueKey)} for a project atomically (ADR-0003). It reads the
 * project's counter row under a {@code PESSIMISTIC_WRITE} lock, increments it, and formats the key via
 * the project's {@link net.innoventa.tessera.service.key.IssueKeyStrategy} — all inside the caller's
 * issue-creation transaction, so parallel creates in the same project serialise on the counter row and
 * never collide on a key. The counter is normally seeded at project creation; the lazy fallback covers
 * any project that predates its counter row.
 */
@Service
@RequiredArgsConstructor
public class IssueKeyAllocator {

    private final ProjectIssueCounterRepository counterRepository;
    private final IssueKeyStrategies issueKeyStrategies;

    public Allocation allocate(Project project) {
        ProjectIssueCounter counter = counterRepository.findByProjectIdForUpdate(project.getId())
            .orElseGet(() -> counterRepository.save(ProjectIssueCounter.builder()
                .projectId(project.getId())
                .nextValue(1)
                .build()));

        int sequence = counter.getNextValue();
        counter.setNextValue(sequence + 1);

        String issueKey = issueKeyStrategies.resolve(project.getKeyStrategy())
            .format(project.getKey(), sequence, project.getKeyPattern());

        return new Allocation(sequence, issueKey);
    }

    /** Seed a project's counter at creation so allocation never has to lazily create it under a race. */
    public void initializeCounter(String projectId) {
        if (!counterRepository.existsById(projectId)) {
            counterRepository.save(ProjectIssueCounter.builder()
                .projectId(projectId)
                .nextValue(1)
                .build());
        }
    }

    public record Allocation(int sequence, String issueKey) {
    }

}
