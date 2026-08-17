package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The projects a member may browse, as a list a query can take.
 *
 * <p>It exists because <strong>two reads now start from the caller rather than from a project</strong> —
 * the cross-project search and the registers behind the Tracked tab (TSSR-45) — and both need the same
 * inverted opening move: resolve what the caller may see, then narrow inside it, never widen (ADR-0008).
 *
 * <p>⚠️ <strong>The subtlety is the only reason this is a component and not two lines copied twice.</strong>
 * An installation-wide grant reads as "every project" and cannot be expressed as a list, so
 * {@code visibleProjectIds} answers with an <em>empty</em> one — see its javadoc. A second copy of this
 * that forgot to ask {@code browsesEveryProject} first would show nothing at all to the one caller who may
 * see everything, and would do it silently.
 */
@Component
@RequiredArgsConstructor
public class BrowsableProjects {

    private final ProjectRepository projectRepository;
    private final ProjectAccess     projectAccess;

    public List<String> idsFor(Member caller) {
        if (projectAccess.browsesEveryProject(caller)) {
            return projectRepository.findAll().stream().map(Project::getId).toList();
        }

        return projectAccess.visibleProjectIds(caller);
    }

}
