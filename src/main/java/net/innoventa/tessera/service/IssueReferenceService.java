package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.issue.IssueReferenceView;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Resolving the issue keys a document mentions, all of them in one go.
 *
 * <p>A `TES-42` written in a description or a comment becomes a live link carrying the issue's summary
 * and status. This is what makes it live — and it is a batch because a document mentions several and
 * the alternative is a request per mention, on a path that runs while somebody is typing.
 *
 * <h2>⚠️ A key the caller may not see is not in the answer, and is not an error</h2>
 *
 * <p>Two things are indistinguishable here on purpose: an issue that does not exist, and one in a
 * project the reader holds nothing at. Telling them apart would let anybody enumerate the tracker by
 * writing keys into a document and reading which ones came back — which is exactly the isolation
 * ADR-0002 buys everywhere else, and it would be a shame to sell it for a nicer error message.
 *
 * <p>The interface renders an unresolved key as the plain text somebody typed, so the two also look the
 * same to a reader.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueReferenceService {

    private final IssueRepository  issues;
    private final StatusRepository statuses;
    private final MemberService    members;
    private final ProjectAccess    projectAccess;

    public List<IssueReferenceView> resolve(Jwt jwt, List<String> issueKeys) {
        return resolve(members.resolveMember(jwt), issueKeys);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    public List<IssueReferenceView> resolve(Member caller, List<String> issueKeys) {
        if (issueKeys == null || issueKeys.isEmpty()) {
            return List.of();
        }

        // Keys are stored uppercase, and a person writing one in prose may not have been. MySQL would
        // match either way and PostgreSQL would not, so the normalisation is here rather than left to
        // a collation to decide differently per database.
        List<String> normalised = issueKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .map(key -> key.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        if (normalised.isEmpty()) {
            return List.of();
        }

        Predicate<Issue> visible = visibleTo(caller);

        List<Issue> found = issues.findByIssueKeyIn(normalised).stream()
                .filter(visible)
                .toList();

        Map<String, Status> statusesById = statusesFor(found);

        return found.stream().map(issue -> describe(issue, statusesById)).toList();
    }

    /**
     * Whether the caller may see the project an issue lives in.
     *
     * <p>⚠️ <strong>A predicate rather than a set, because "all of them" is not a set.</strong> An
     * installation-wide grant covers projects nobody has made yet, so it cannot be enumerated — and
     * returning null to mean it is the kind of sentinel that reads fine and dereferences badly two
     * refactors later. Asking the question as a function makes both answers the same shape.
     */
    private Predicate<Issue> visibleTo(Member caller) {
        if (projectAccess.browsesEveryProject(caller)) {
            return issue -> true;
        }

        Set<String> visible = Set.copyOf(projectAccess.visibleProjectIds(caller));

        return issue -> visible.contains(issue.getProjectId());
    }

    private Map<String, Status> statusesFor(List<Issue> found) {
        List<String> statusIds = found.stream().map(Issue::getStatusId).distinct().toList();

        return statuses.findAllById(statusIds).stream()
                .collect(Collectors.toMap(Status::getId, Function.identity()));
    }

    private IssueReferenceView describe(Issue issue, Map<String, Status> statusesById) {
        Status status = statusesById.get(issue.getStatusId());

        return new IssueReferenceView(
                issue.getIssueKey(),
                issue.getSummary(),
                status == null ? null : status.getName(),
                // Open is the invariant rather than a status name (ADR-0004), so it keeps working when
                // somebody adds a status this code has never heard of.
                issue.getResolutionId() == null);
    }
}
