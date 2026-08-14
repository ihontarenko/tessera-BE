package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.project.IssueKeyPreview;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ProjectIssueCounterRepository;
import net.innoventa.tessera.service.key.IssueKeyFormat;
import net.innoventa.tessera.service.key.IssueKeyStrategies;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a key format would produce, before anybody commits to it.
 *
 * <p>⚠️ <strong>It reads the counter without locking it.</strong> {@code IssueKeyAllocator} takes a
 * {@code PESSIMISTIC_WRITE} on that row because it is about to increment it; a preview is a question,
 * asked twice a keystroke by a settings screen, and taking a write lock to answer it would serialise
 * every issue creation in the project behind somebody typing.
 *
 * <p>A racing creation makes the previewed number one out of date, which is exactly as wrong as any
 * preview of a future can be.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueKeyPreviewService {

    private final ProjectIssueCounterRepository counterRepository;
    private final IssueRepository               issueRepository;
    private final IssueKeyStrategies            issueKeyStrategies;

    public IssueKeyPreview preview(Project project, String keyStrategy, String keyPattern) {
        int sequence = counterRepository.findById(project.getId())
            .map(counter -> counter.getNextValue())
            .orElse(1);

        String nextKey = issueKeyStrategies.resolve(keyStrategy)
            .format(project.getKey(), sequence, keyPattern);

        return new IssueKeyPreview(nextKey, anyExistingKey(project), formats());
    }

    /**
     * ⚠️ Any one key the project already holds, so the screen can put the old shape beside the new one.
     * Which one it is does not matter — what it shows is that the two will not match.
     */
    private String anyExistingKey(Project project) {
        return issueRepository.findFirstByProjectIdOrderByRankDesc(project.getId())
            .map(issue -> issue.getIssueKey())
            .orElse(null);
    }

    private static java.util.List<IssueKeyPreview.Format> formats() {
        return IssueKeyFormat.all().stream()
            .map(format -> new IssueKeyPreview.Format(format.name(), format.example()))
            .toList();
    }

}
