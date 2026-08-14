package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.ProjectMembershipRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.service.ProjectPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Asks both authorization models the same questions, over this installation's real data, and reports
 * every answer they disagree about.
 *
 * <p><strong>This is what makes annotating seventy-five endpoints boring.</strong> Replacing a working
 * authorization model with another one is a change whose failures are invisible until somebody is
 * refused something they should have, or — far worse — allowed something they should not. Nothing in the
 * cutover proves the new model reproduces the old one; a comparison over the rows that actually exist
 * does.
 *
 * <p>Every {@code (member, project)} pair that has a membership is asked twice:
 *
 * <ul>
 *   <li>{@link ProjectPermissionService#effectivePermissions} — role permissions ∪ ALLOW − DENY, joined
 *       through {@code project_roles} and {@code permissions}.
 *   <li>{@link ProjectAccess#permissionsIn} — the covering chain over {@code access_*}, with deny-wins
 *       and the subtraction last.
 * </ul>
 *
 * <h2>⚠️ It runs after the handover and before anything is thrown away</h2>
 *
 * <p>V000013 copies the rows across and drops nothing, so both models are answerable at the same moment.
 * That window is the whole point, and it closes at V000014 — which deletes the old tables, this class,
 * {@code ProjectPermissionService} and {@link LocalAuthorizationMirror} together.
 *
 * <h2>⚠️ It reports and does not refuse</h2>
 *
 * <p>Tempting to fail the boot on a disagreement, and wrong: the interesting run is the <em>first</em>
 * one after the handover, on a database somebody has been using, and an installation that will not start
 * is one whose report nobody can read. A disagreement is logged at error with both sets named, per pair,
 * which is what somebody actually needs to decide whether the engine is wrong or the old model was.
 *
 * <p>Switched off with {@code tessera.access.parallel-check.enabled=false} once the answer is known and
 * the walk is no longer worth its startup cost.
 */
@Component
@RequiredArgsConstructor
public class ParallelAuthorizationCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelAuthorizationCheck.class);

    /**
     * What the retiring tables call each permission, as the engine now spells it.
     *
     * <p>⚠️ <strong>Without this the comparison would report every single pair as a disagreement</strong>
     * — nine permissions gained and nine lost — which is a report so loud it says nothing. The names
     * changed because a permission in a {@code .jmp} document must be {@code namespace:action}; see
     * {@link net.innoventa.tessera.security.Permissions}. This is the same mapping
     * {@code V000013__authorization_handover.sql} applies to the stored rows, and both go together.
     */
    private static final Map<String, String> AS_THE_ENGINE_SPELLS_IT = Map.of(
            "BROWSE_PROJECT",     Permissions.BROWSE_PROJECT,
            "CREATE_ISSUE",       Permissions.CREATE_ISSUE,
            "EDIT_ISSUE",         Permissions.EDIT_ISSUE,
            "ASSIGN_ISSUE",       Permissions.ASSIGN_ISSUE,
            "TRANSITION_ISSUE",   Permissions.TRANSITION_ISSUE,
            "DELETE_ISSUE",       Permissions.DELETE_ISSUE,
            "ADD_COMMENT",        Permissions.ADD_COMMENT,
            "MANAGE_SPRINT",      Permissions.MANAGE_SPRINT,
            "ADMINISTER_PROJECT", Permissions.ADMINISTER_PROJECT);

    private final ProjectMembershipRepository memberships;
    private final MemberRepository            members;
    private final ProjectPermissionService    local;
    private final ProjectAccess               engine;

    @Value("${tessera.access.parallel-check.enabled:true}")
    private boolean enabled;

    /**
     * ⚠️ Ordered after {@link net.innoventa.tessera.bootstrap.BootstrapLedger}, which takes
     * {@code HIGHEST_PRECEDENCE}. Comparing before the seed has written the role bundles would report
     * every permission in the installation as missing from the engine, which is true for a fraction of a
     * second and useless.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    @Transactional(readOnly = true)
    public void compareBothModels() {
        if (!enabled) {
            return;
        }

        Map<String, String> whoIsWho = members.findAll().stream()
                .collect(Collectors.toMap(Member::getId, ParallelAuthorizationCheck::describe));

        List<Disagreement> disagreements = new ArrayList<>();
        int                compared      = 0;
        int                orphaned      = 0;

        for (Pair pair : pairs()) {
            Member member = members.findById(pair.memberId()).orElse(null);

            if (member == null) {
                orphaned++;
                continue;
            }

            Set<String> byTheOldModel = local.effectivePermissions(pair.memberId(), pair.projectId())
                    .stream()
                    .map(name -> AS_THE_ENGINE_SPELLS_IT.getOrDefault(name, name))
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<String> byTheEngine = new TreeSet<>(engine.permissionsIn(member, pair.projectId()));

            compared++;

            if (!byTheOldModel.equals(byTheEngine)) {
                disagreements.add(new Disagreement(pair, byTheOldModel, byTheEngine));
            }
        }

        report(compared, orphaned, disagreements, whoIsWho);
    }

    private List<Pair> pairs() {
        return memberships.findAll().stream()
                .map(membership -> new Pair(membership.getMemberId(), membership.getProjectId()))
                .distinct()
                .toList();
    }

    private void report(
            int compared, int orphaned, List<Disagreement> disagreements, Map<String, String> whoIsWho) {

        // ⚠️ Nothing compared is NOT the same fact as everything agreeing, and reporting it as one is
        // the failure this whole check exists to prevent, committed by the check itself. An installation
        // with no memberships — or one whose membership rows point at accounts that are gone — would
        // otherwise read a reassuring line every start while proving absolutely nothing.
        if (compared == 0) {
            LOGGER.warn("Access parallel run: NOTHING WAS COMPARED. {} membership row(s) exist and {} of "
                        + "them name an account that no longer has a member row. Until there is data to "
                        + "compare, this check has not confirmed anything about the cutover.",
                    compared + orphaned, orphaned);
            return;
        }

        if (disagreements.isEmpty()) {
            LOGGER.info("Access parallel run: {} (member, project) pair(s) compared, both models agree "
                        + "on every one. The engine reproduces what ProjectPermissionService answered.{}",
                    compared, orphaned == 0 ? "" : orphanNote(orphaned));
            return;
        }

        LOGGER.error("Access parallel run: {} of {} (member, project) pair(s) DISAGREE. The engine is "
                     + "what the routes now resolve from, so every line below is a permission somebody "
                     + "gained or lost in this change.", disagreements.size(), compared);

        for (Disagreement disagreement : disagreements) {
            Set<String> gained = difference(disagreement.byTheEngine(), disagreement.byTheOldModel());
            Set<String> lost   = difference(disagreement.byTheOldModel(), disagreement.byTheEngine());

            LOGGER.error("  {} in project {} — gained {}, lost {}",
                    whoIsWho.getOrDefault(disagreement.pair().memberId(), disagreement.pair().memberId()),
                    disagreement.pair().projectId(),
                    gained.isEmpty() ? "nothing" : gained,
                    lost.isEmpty() ? "nothing" : lost);
        }
    }

    /**
     * ⚠️ A membership naming an account that no longer exists is worth a sentence even when everything
     * else agrees. Nothing cascades: the row survived its member, so it is both a comparison that could
     * not happen and a row somebody has to clear.
     */
    private static String orphanNote(int orphaned) {
        return " " + orphaned + " membership row(s) were skipped because the member they name no longer "
             + "exists — those pairs were not compared.";
    }

    private static Set<String> difference(Set<String> from, Set<String> without) {
        return from.stream().filter(name -> !without.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * A name a person reading the report can recognise.
     *
     * <p>Falls back to the identifier rather than to null, because the map this feeds refuses one — and
     * because a report line reading {@code null in project …} is a line nobody can act on.
     */
    private static String describe(Member member) {
        if (member.getEmail() != null) {
            return member.getEmail();
        }

        return member.getDisplayName() == null ? member.getId() : member.getDisplayName();
    }

    /** One question, asked of both models. Distinct, because several roles are several membership rows. */
    private record Pair(String memberId, String projectId) {}

    private record Disagreement(Pair pair, Set<String> byTheOldModel, Set<String> byTheEngine) {}
}
