package net.innoventa.tessera.security.access.target;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import net.innoventa.tessera.security.access.Targets;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.spi.AccessTargetResolver;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The same answer as {@link IssueAccessTargetResolver}, reached through the key.
 *
 * <p>Written by hand rather than extended from {@code ProjectedAccessTargetResolver}, because that
 * class matches on {@code resource.id} — which is the right thing for every other resource and the one
 * thing this route cannot do.
 *
 * <p>⚠️ <strong>{@code getResultList}, never {@code getResultStream}.</strong> This runs from the
 * authorization interceptor, <em>before</em> the handler and therefore outside its transaction, so a
 * scrollable result set goes back to the pool before anything reads it and the first row lands on a
 * closed cursor. Annotating the class {@code @Transactional} would also work and would be the wrong
 * fix: a transaction per authorization check, to hold a cursor over one row.
 *
 * <p>An unknown key resolves to nothing, which the engine reads as <em>no such row</em> — a 404 rather
 * than an unscoped call that would pass every question about a place.
 */
@Component
public class IssueByKeyAccessTargetResolver implements AccessTargetResolver<IssueByKey> {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Class<IssueByKey> resourceType() {
        return IssueByKey.class;
    }

    @Override
    public Optional<AccessTarget> resolve(String issueKey) {
        if (issueKey == null || issueKey.isBlank()) {
            return Optional.empty();
        }

        return entityManager
                .createQuery(
                        "SELECT issue.projectId, issue.reporterMemberId FROM Issue issue "
                        + "WHERE issue.issueKey = :issueKey", Object[].class)
                .setParameter("issueKey", issueKey)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .map(row -> Targets.ownedBy((String) row[0], (String) row[1]));
    }
}
