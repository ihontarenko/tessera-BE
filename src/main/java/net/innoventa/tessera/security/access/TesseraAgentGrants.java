package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import org.jmouse.access.PermissionCatalog;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.spi.DirectGrant;
import org.jmouse.access.spi.GrantStore;
import org.jmouse.ai.agent.Agent;
import org.jmouse.ai.agent.AgentDirectory;
import org.jmouse.ai.agent.AgentGrants;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a restricted agent may do here, and in which projects.
 *
 * <h2>⚠️ The dead end this closes</h2>
 *
 * <p>The agents screen offered <em>Restrict it</em> and there was nowhere to fill the agent afterwards:
 * this product's access screen resolves a {@code Member} and only a {@code Member}, so an agent could be
 * narrowed to nothing and never widened again except by undoing the restriction. The button led one way.
 *
 * <p>Nothing about the storage had to change to fix it. An agent's grants have always been ordinary
 * {@code access_*} rows keyed on an opaque subject identifier — indistinguishable from a person's — so
 * what was missing was a way to <em>edit</em> them, not a place to put them.
 *
 * <h2>What this class knows that the library must not</h2>
 *
 * <p>That a place is a <strong>project</strong>, that its label reads {@code KEY · Name}, and which
 * roles exist. All three arrive at the port as opaque strings, which is the whole reason one screen can
 * edit an agent in two products that agree on none of it.
 *
 * <p>⚠️ <strong>Nothing here is capped by the owner.</strong> A RESTRICTED agent is its own account —
 * the engine stopped intersecting it with anybody — so {@link #offerFor} answers the installation's
 * whole vocabulary, on both axes, and {@link #replace} refuses nothing for exceeding a person.
 */
@Component
@RequiredArgsConstructor
public class TesseraAgentGrants implements AgentGrants {

    /**
     * ⚠️ A constant rather than nothing. {@code reason} is what makes a grant answerable a year later,
     * and an agent's are the ones most likely to be found by somebody with no idea this screen wrote
     * them.
     */
    private static final String REASON = "Set on the agents screen by whoever administers AI here.";

    /** How a grant records where it came from, matching what the access screen writes for a person. */
    private static final String SOURCE = "DIRECT";

    private final AgentDirectory       agents;
    private final MemberRepository     members;
    private final ProjectRepository    projects;
    private final ProjectAccess        projectAccess;
    private final AccessAdministration access;
    private final GrantStore           grants;
    private final PermissionCatalog    vocabulary;

    @Override
    @Transactional(readOnly = true)
    public Held heldBy(String agentId) {
        return new Held(installationWidePermissionsOf(agentId), placementsOf(agentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Offer offerFor(String agentId) {
        // ⚠️ THE INSTALLATION'S SET, NOT THE OWNER'S — and this is a reversal worth reading, because the
        // comment here used to say the exact opposite. A RESTRICTED agent is a separate account rather
        // than a narrowed owner: `AgentCallers` gives it its own identity and the engine no longer
        // intersects it with anybody's. So an agent may hold what its owner does not — a client trusted
        // with one destructive action nobody else has, a service account whose owner merely set it up —
        // and offering only the owner's set would make those impossible to express.
        //
        // ⚠️ THE CATALOGUE, WHICH IS THE POLICY DOCUMENTS. Both axes arrive together and neither is
        // named here: `tessera.jmp` declares what a subject may DO, `tools.jmp` declares one permission
        // per published tool action, and `PermissionCatalog` is read off the two. So adding a tool adds
        // a switch to this screen with nothing to change here — which is the whole reason the vocabulary
        // stopped living in Java.
        return new Offer(
                List.copyOf(vocabulary.all()),
                placesOf(projects.findAll().stream().map(Project::getId).toList()),
                access.roles().stream()
                        .map(role -> new Role(
                                role.name(), !Scopes.GLOBAL.equals(role.assignableAt())))
                        .toList());
    }

    @Override
    @Transactional
    public Held replace(
            String agentId, List<String> permissions, List<Placement> placements, String grantedBy) {

        Set<String>    wanted = new LinkedHashSet<>(permissions);
        Set<Placement> places = new LinkedHashSet<>(placements);

        // ⚠️ NOTHING IS REFUSED FOR EXCEEDING THE OWNER, and the guard that did it is gone rather than
        // relaxed. It existed because the engine used to intersect a restricted agent with its owner, so
        // a grant beyond them resolved to nothing and read as the agent being broken. That intersection
        // is gone: a restricted agent is its own account, and a set its owner does not hold is a set it
        // genuinely has. Refusing it here would be this screen enforcing a rule the engine stopped
        // having.
        replacePermissions(agentId, wanted, grantedBy);
        replacePlacements(agentId, places, grantedBy);

        return heldBy(agentId);
    }

    /**
     * ⚠️ The whole subject, not only what this screen wrote.
     *
     * <p>An agent's grants may also have come from the access screen; leaving those behind on the
     * strength of "this screen did not write them" is exactly the silent remainder this call exists to
     * prevent. Nothing foreign-keys an {@code access_*} row to an agent, so nothing else would.
     */
    @Override
    @Transactional
    public void revokeAllOf(String agentId) {
        access.revokeAllFor(agentId);
    }

    private void replacePermissions(String agentId, Set<String> wanted, String grantedBy) {
        installationWidePermissionsOf(agentId).stream()
                .filter(permission -> !wanted.contains(permission))
                .forEach(permission -> access.ungrant(agentId, permission, Targets.installationScope()));

        // ⚠️ Written unconditionally: the port is idempotent and reports `changed` false on a no-op, so
        // a re-save of an unchanged set writes nothing.
        wanted.forEach(permission -> access.grant(
                agentId, permission, Targets.installationScope(),
                AccessAdministration.Effect.ALLOW, REASON, grantedBy, null));
    }

    private void replacePlacements(String agentId, Set<Placement> wanted, String grantedBy) {
        placementsOf(agentId).stream()
                .filter(held -> !wanted.contains(held))
                .forEach(held -> access.unassign(held.roleName(), agentId, scopeOf(held)));

        wanted.forEach(placement -> access.assign(
                agentId, placement.roleName(), scopeOf(placement), SOURCE, grantedBy, null));
    }

    private org.jmouse.access.ScopeReference scopeOf(Placement placement) {
        return placement.placeId() == null
                ? Targets.installationScope()
                : Targets.projectScope(placement.placeId());
    }

    private List<String> installationWidePermissionsOf(String agentId) {
        return grants.directHeldBy(agentId).stream()
                .filter(DirectGrant::allowed)
                .filter(grant -> Scopes.GLOBAL.equals(grant.at().type().name()))
                .map(DirectGrant::permission)
                .sorted()
                .toList();
    }

    private List<Placement> placementsOf(String agentId) {
        return grants.rolesHeldBy(agentId).stream()
                .map(role -> new Placement(
                        role.roleName(),
                        Scopes.GLOBAL.equals(role.at().type().name()) ? null : role.at().id()))
                .distinct()
                .toList();
    }

    private List<Place> placesOf(List<String> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }

        return projects.findAllById(projectIds).stream()
                .map(project -> new Place(project.getId(), label(project)))
                .toList();
    }

    /** ⚠️ The label is this product's word for a project, and the library never parses it. */
    private static String label(Project project) {
        return project.getKey() + " · " + project.getName();
    }

    /**
     * ⚠️ The owner is resolved through the agent rather than taken from the caller. Whoever is editing
     * may be an administrator rather than the owner, and the ceiling that matters is the agent's
     * owner's — not the editor's.
     */
    private Member ownerOf(String agentId) {
        Agent agent = agents.find(agentId).orElseThrow(() -> new RefusedException(
                "No agent with id '" + agentId + "' exists."));

        return members.findById(agent.ownerReference()).orElseThrow(() -> new RefusedException(
                "The account this agent acts for no longer exists, so there is no ceiling to grant "
                + "within. Nothing was changed."));
    }
}
