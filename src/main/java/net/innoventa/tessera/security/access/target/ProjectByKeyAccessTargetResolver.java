package net.innoventa.tessera.security.access.target;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import net.innoventa.tessera.security.access.Targets;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.spi.AccessTargetResolver;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * A project key resolved to the project it names, for the authorization interceptor.
 *
 * <p>⚠️ <strong>Case-insensitive, matching the read it guards.</strong> A key arrives from a
 * hand-typed URL in whatever case somebody used, and a check that matched exactly would refuse
 * {@code /projects/tssr} as <em>no such project</em> while the handler behind it would have answered.
 * Two different answers to one address is worse than either answer.
 *
 * <p>⚠️ <strong>No owner on the target.</strong> A project has a lead, and a lead is not an owner in
 * this model — ownership is the {@code SELF} scope over rows a person created, and nobody creates a
 * project the way they report an issue. Naming the lead here would quietly grant them whatever
 * {@code SELF} ever comes to mean.
 *
 * <p>⚠️ <strong>{@code getResultList}, never {@code getResultStream}</strong> — the same reason
 * {@link IssueByKeyAccessTargetResolver} says: this runs before the handler and therefore outside its
 * transaction, so a scrollable result set goes back to the pool before anything reads it.
 *
 * <p>An unknown key resolves to nothing, which the engine reads as <em>no such row</em> — a 404 rather
 * than an unscoped call that would pass every question about a place.
 */
@Component
public class ProjectByKeyAccessTargetResolver implements AccessTargetResolver<ProjectByKey> {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * ⚠️ The word a policy writes for this type, declared HERE rather than on the class.
     * A library type cannot carry the annotation — `jmouse-files` has no dependency on
     * `jmouse-access` and must not grow one — and declaring every resolver's name the same way keeps
     * one rule instead of two. Same words as Innoventa's and Kiwi's: one type, one spelling.
     */
    @Override
    public String resourceName() {
        return "project_key";
    }

    @Override
    public Class<ProjectByKey> resourceType() {
        return ProjectByKey.class;
    }

    @Override
    public Optional<AccessTarget> resolve(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return Optional.empty();
        }

        return entityManager
                .createQuery("SELECT project.id FROM Project project WHERE upper(project.key) = :key", String.class)
                .setParameter("key", projectKey.trim().toUpperCase(java.util.Locale.ROOT))
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .map(Targets::project);
    }
}
