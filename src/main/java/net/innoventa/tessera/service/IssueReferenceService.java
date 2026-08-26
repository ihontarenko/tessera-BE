package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.issue.IssueReferenceView;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Resolving the references a document mentions, all of them in one go.
 *
 * <p>A `TES-42` written in a description or a comment becomes a live badge carrying the issue's summary
 * and state. This is what makes it live — and it is a batch because a document mentions several and the
 * alternative is a request per mention, on a path that runs while somebody is typing.
 *
 * <h2>⚠️ A reference arrives in either of two forms, and the caller does not have to say which</h2>
 *
 * <p>A key is what somebody types; a permanent hash is what a picker inserts and what survives the key
 * being re-minted. Both are just strings in a link, so this asks the key first and the hash for
 * whatever is left over — the order matters, because six hex characters is also a perfectly ordinary
 * issue key to anything that only looks at shape, and asking the hash first would let one shadow the
 * other.
 *
 * <p>The answer carries both identifiers, so the client files each one under whichever token it sent.
 *
 * <h2>⚠️ A reference the caller may not see is not in the answer, and is not an error</h2>
 *
 * <p>Two things are indistinguishable here on purpose: an issue that does not exist, and one in a
 * project the reader holds nothing at. Telling them apart would let anybody enumerate the tracker by
 * writing keys into a document and reading which ones came back — which is exactly the isolation
 * ADR-0002 buys everywhere else, and it would be a shame to sell it for a nicer error message.
 *
 * <p>The interface renders an unresolved reference as the plain text somebody typed, so the two also
 * look the same to a reader.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueReferenceService {

    private final IssueRepository     issues;
    private final StatusRepository    statuses;
    private final IssueTypeRepository issueTypes;
    private final MemberService       members;
    private final ProjectAccess       projectAccess;

    public List<IssueReferenceView> resolve(Jwt jwt, List<String> references) {
        return resolve(members.resolveMember(jwt), references);
    }

    /** The same, for a caller that is not an HTTP request — see {@code ProjectService.list(Member)}. */
    public List<IssueReferenceView> resolve(Member caller, List<String> references) {
        List<String> tokens = tokensOf(references);

        if (tokens.isEmpty()) {
            return List.of();
        }

        List<Issue> found = findByKeyThenHash(tokens);

        if (found.isEmpty()) {
            return List.of();
        }

        Predicate<Issue> visible = visibleTo(caller);
        List<Issue> readable = found.stream().filter(visible).toList();

        Map<String, Status>    statusesById = statusesFor(readable);
        Map<String, IssueType> typesById    = typesFor(readable);

        return readable.stream().map(issue -> describe(issue, statusesById, typesById)).toList();
    }

    /**
     * What was actually asked for: blanks dropped, whitespace trimmed, each one once.
     *
     * <p>⚠️ <strong>Case is left alone here</strong> and applied per lookup below. Keys are stored
     * uppercase and hashes lowercase, and MySQL would match either way while PostgreSQL would not — so
     * normalising once, in one direction, is how the same document comes to render differently on the
     * two databases.
     */
    private static List<String> tokensOf(List<String> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }

        return references.stream()
                .filter(reference -> reference != null && !reference.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * The issues these tokens name — by key, and then by permanent hash for whatever the keys missed.
     *
     * <p>⚠️ <strong>Two queries, never one over both columns.</strong> A single query would have to
     * decide which column a token belongs to before asking, and the whole point is that a caller does
     * not have to know. Asking twice is one extra round trip on a batch that already exists to save
     * dozens.
     */
    private List<Issue> findByKeyThenHash(List<String> tokens) {
        List<Issue> byKey = issues.findByIssueKeyIn(
                tokens.stream().map(token -> token.toUpperCase(Locale.ROOT)).distinct().toList());

        Set<String> claimed = byKey.stream()
                .map(issue -> issue.getIssueKey().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<String> unclaimed = tokens.stream()
                .filter(token -> !claimed.contains(token.toUpperCase(Locale.ROOT)))
                .map(token -> token.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        if (unclaimed.isEmpty()) {
            return byKey;
        }

        // ⚠️ Merged through a map keyed by id: a document may mention the same issue by its key in one
        // sentence and by its hash in another, and describing it twice would make the client's own
        // index disagree with itself about which entry is current.
        Map<String, Issue> merged = new LinkedHashMap<>();

        byKey.forEach(issue -> merged.put(issue.getId(), issue));
        issues.findByHashIn(unclaimed).forEach(issue -> merged.putIfAbsent(issue.getId(), issue));

        return new ArrayList<>(merged.values());
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

    private Map<String, IssueType> typesFor(List<Issue> found) {
        List<String> typeIds = found.stream().map(Issue::getIssueTypeId).distinct().toList();

        return issueTypes.findAllById(typeIds).stream()
                .collect(Collectors.toMap(IssueType::getId, Function.identity()));
    }

    private IssueReferenceView describe(
        Issue issue,
        Map<String, Status> statusesById,
        Map<String, IssueType> typesById
    ) {
        Status    status = statusesById.get(issue.getStatusId());
        IssueType type   = typesById.get(issue.getIssueTypeId());

        return new IssueReferenceView(
                issue.getIssueKey(),
                issue.getHash(),
                issue.getSummary(),
                status == null ? null : status.getName(),
                status == null ? null : status.getColor(),
                status == null || status.getCategory() == null ? null : status.getCategory().name(),
                type == null ? null : type.getName(),
                type == null ? null : type.getIconKey(),
                // Open is the invariant rather than a status name (ADR-0004), so it keeps working when
                // somebody adds a status this code has never heard of.
                issue.getResolutionId() == null);
    }
}
