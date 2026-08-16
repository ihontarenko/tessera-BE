package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.security.Permissions;
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
 * <p>⚠️ <strong>Everything written here is still capped by the owner, in every scope, on every
 * request.</strong> Granting an agent something its owner does not hold changes nothing — which is why
 * {@link #offerFor} answers the owner's set rather than the installation's.
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

    @Override
    @Transactional(readOnly = true)
    public Held heldBy(String agentId) {
        return new Held(installationWidePermissionsOf(agentId), placementsOf(agentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Offer offerFor(String agentId) {
        Member owner = ownerOf(agentId);

        // ⚠️ The owner's set, never the installation's. An agent granted what its owner does not hold
        // resolves to nothing and reads, from outside, as the agent being broken.
        return new Offer(
                Permissions.all().stream()
                        .filter(permission -> projectAccess.holdsAnywhere(owner, permission))
                        .sorted()
                        .toList(),
                placesOf(projectAccess.visibleProjectIds(owner)),
                access.roles().stream()
                        .map(role -> new Role(
                                role.name(), !Scopes.GLOBAL.equals(role.assignableAt())))
                        .toList());
    }

    @Override
    @Transactional
    public Held replace(
            String agentId, List<String> permissions, List<Placement> placements, String grantedBy) {

        Member         owner  = ownerOf(agentId);
        Set<String>    wanted = new LinkedHashSet<>(permissions);
        Set<Placement> places = new LinkedHashSet<>(placements);

        refuseBeyondTheOwner(owner, wanted, places);

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

    /**
     * ⚠️ Refused rather than silently trimmed to what the owner holds.
     *
     * <p>Trimming would leave the screen showing a smaller set than was saved with no explanation, and
     * the person would reasonably conclude the save failed. Naming the one that is over the line is the
     * only version of this that teaches the rule.
     */
    private void refuseBeyondTheOwner(Member owner, Set<String> permissions, Set<Placement> placements) {
        permissions.stream()
                .filter(permission -> !projectAccess.holdsAnywhere(owner, permission))
                .findFirst()
                .ifPresent(permission -> {
                    throw new RefusedException(
                            "You cannot give an agent '" + permission + "' — the account it acts for does "
                            + "not hold it, so the grant would resolve to nothing on every call. Nothing "
                            + "was changed.");
                });

        List<String> reachable = projectAccess.visibleProjectIds(owner);

        placements.stream()
                .filter(placement -> placement.placeId() != null)
                .filter(placement -> !reachable.contains(placement.placeId()))
                .findFirst()
                .ifPresent(placement -> {
                    throw new RefusedException(
                            "You cannot put an agent in a project its owner cannot reach. Nothing was "
                            + "changed.");
                });
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
