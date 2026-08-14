package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.bootstrap.BootstrapStep;
import org.jmouse.access.PermissionCatalog;
import org.jmouse.access.ScopeCatalog;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.el.PolicyWriter;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.jpa.AccessAdministration.BundleEntry;
import org.jmouse.access.jpa.AccessAdministration.Change;
import org.jmouse.access.jpa.AccessAdministration.Effect;
import org.jmouse.access.jpa.AccessAdministration.RoleView;
import org.jmouse.access.policy.AccessPolicy;
import org.jmouse.access.policy.AccessPolicy.BoundAssignment;
import org.jmouse.access.policy.AccessPolicy.BoundSubject;
import org.jmouse.access.policy.PolicyBinder;
import org.jmouse.access.policy.model.PolicyDocument;
import org.jmouse.access.policy.model.PolicyRole;
import org.jmouse.access.spi.BundledPermission;
import org.jmouse.access.spi.DirectGrant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Writes what {@code policy/tessera.jmp} declares into the tables that answer for it.
 *
 * <p>This is the step where a grant stops having two possible homes. The document could be a second
 * {@code GrantStore} the engine read beside the rows — the library offers exactly that, and
 * {@link net.innoventa.tessera.config.AccessStorageConfiguration} refuses it. Instead the file is read
 * once, at a start whose checksum has moved, and turned into rows; after which the management screen
 * edits the same fact the engine reads, and there is nothing to reconcile.
 *
 * <h2>⚠️ Bound, not raw</h2>
 *
 * <p>{@link AccessPolicy} carries bundles with any {@code namespace:*} already expanded against the
 * catalogue and scopes already resolved to references. Expanding them here would be a second
 * implementation of {@code PolicyBinder}'s, and the two would drift.
 *
 * <p>It also means <strong>what lands in the table is a snapshot</strong>, which is why
 * {@link #checksum()} folds in the permission catalogue: add a permission and the checksum moves, the
 * ledger re-runs the step, and a role declared with a wildcard means what it says again.
 */
@Component
@RequiredArgsConstructor
public class PolicySeedStep implements BootstrapStep {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicySeedStep.class);

    public static final String KEY = "access:policy";

    /**
     * The provenance {@code AccessAdministration} keeps deliberately open, so that a later revocation of
     * somebody's project membership leaves a seeded assignment alone.
     */
    private static final String ASSIGNED = "SEEDED";

    private static final String BY = "bootstrap";

    /**
     * Bumped whenever this step writes the <em>same</em> document as different rows.
     *
     * <p>⚠️ The ledger re-runs a step when its checksum moves, and the checksum is made of the policy
     * file. A fix that changes how that file becomes rows therefore reaches nobody who has already
     * booted — the file did not move — which is how a repaired seeder leaves every existing installation
     * broken. This constant is the part of the checksum that is about the seeder rather than about the
     * policy.
     */
    private static final int SEED_FORMAT = 1;

    private final PolicyDocument       shipped;
    private final PolicyBinder         binder;
    private final PermissionCatalog    permissions;
    private final ScopeCatalog         scopes;
    private final AccessAdministration access;

    @Override
    public String key() {
        return KEY;
    }

    /**
     * The document as it would be written out, plus the permission catalogue.
     *
     * <p>The rendered form rather than the file text, because a rendering is canonical: reformatting the
     * file must not look like a policy change.
     *
     * <p>⚠️ <strong>The catalogue is sorted, and leaving that out makes the whole ledger useless.</strong>
     * {@link PermissionCatalog} holds an immutable set, and those randomise their iteration order on
     * every JVM start by design. Joining one in encounter order hashes the randomisation rather than the
     * catalogue, so a second start of an unchanged build reads as a changed source. Anything unordered
     * has to be canonicalised before it is hashed.
     */
    @Override
    public String checksum() {
        return sha256(PolicyWriter.write(shipped)
                      + "\n" + String.join(",", new TreeSet<>(permissions.all()))
                      + "\n" + SEED_FORMAT);
    }

    @Override
    public String note() {
        return "Roles, bundles and any declared assignments, from policy/tessera.jmp";
    }

    @Override
    public Outcome apply() {
        AccessPolicy policy = binder.bind(shipped);

        int written = seedRoles(policy) + seedSubjects(policy);

        return new Outcome(written, "%d role(s), %d subject(s)".formatted(
                policy.roles().size(), policy.subjects().size()));
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    /**
     * ⚠️ {@code assignableAt} comes from the document and from nowhere else.
     *
     * <p>It is the widest scope a role may be handed out at, and it is what stops
     * {@code PROJECT_ADMINISTRATOR} being granted installation-wide. Deriving it from the widest scope in
     * the bundle would be right by accident for these three and undefined for a role that bundles
     * nothing — so the grammar states it, and a role that does not is skipped loudly rather than seeded
     * at a guess.
     */
    private int seedRoles(AccessPolicy policy) {
        Set<String> alreadyDefined = access.roles().stream()
                .map(RoleView::name)
                .collect(Collectors.toSet());

        int written = 0;

        for (PolicyRole declared : shipped.roles()) {
            Optional<ScopeKind> assignableAt = assignableScopeOf(declared);

            if (assignableAt.isEmpty()) {
                continue;
            }

            // ⚠️ This runs on a re-run as often as on a first run. `defineRole` refuses a name that
            // exists — rightly, because two roles by one name are two answers to one question — so the
            // row is created only where there is none, and the bundle is written every time. That is
            // what makes a re-run mean "bring the tables back in line with the file" rather than "fail".
            if (!alreadyDefined.contains(declared.name())) {
                access.defineRole(declared.name(), assignableAt.get());
            }

            access.setBundle(declared.name(), bundleOf(declared.name(), policy.roles()));

            written++;
        }

        return written;
    }

    private Optional<ScopeKind> assignableScopeOf(PolicyRole role) {
        if (!role.statesWhereItMayBeAssigned()) {
            LOGGER.error("Role '{}' does not say where it may be assigned, so it is NOT seeded. Write "
                         + "'declare role {} assignable @SCOPE {{ … }}' — without it there is nothing "
                         + "to stop the role being handed out at any scope at all.",
                    role.name(), role.name());
            return Optional.empty();
        }

        Optional<ScopeKind> kind = scopes.byName(role.assignableAt());

        if (kind.isEmpty()) {
            LOGGER.error("Role '{}' is assignable at '@{}', which is not a scope this build registers. "
                         + "It is NOT seeded.", role.name(), role.assignableAt());
        }

        return kind;
    }

    private List<BundleEntry> bundleOf(String roleName, Map<String, List<BundledPermission>> bound) {
        return bound.getOrDefault(roleName, List.of()).stream()
                .map(entry -> new BundleEntry(entry.permission(), entry.carriedAt().name()))
                .toList();
    }

    // ── Subjects ──────────────────────────────────────────────────────────────

    /**
     * Whatever the document assigns by hand.
     *
     * <p>⚠️ <strong>Empty today, and the loop is here anyway.</strong> Tessera's members are provisioned
     * from Identity on first sign-in, so at the moment this file is read there is no identifier the
     * document could name — see {@code tessera.jmp}'s note on why the installation's first access
     * administrator is assigned by {@code MemberProvisioner} instead. The day an installation does want
     * to write one down, this is what reads it, rather than a seeder somebody has to remember to grow.
     */
    private int seedSubjects(AccessPolicy policy) {
        int written = 0;

        for (Map.Entry<String, BoundSubject> held : policy.subjects().entrySet()) {
            written += seedOneSubject(held.getKey(), held.getValue());
        }

        return written;
    }

    private int seedOneSubject(String subjectId, BoundSubject held) {
        int written = 0;

        for (BoundAssignment assignment : held.roles()) {
            // ⚠️ Counted by what the port says it changed, not by what was attempted. On a re-run every
            // one of these is already there and reports nothing, and a step claiming it wrote eight rows
            // when it wrote none is a log line that makes the ledger unreadable.
            Change assigned = access.assign(
                    subjectId, assignment.roleName(), assignment.at(), ASSIGNED, BY, null);

            if (assigned.changed()) {
                written++;
            }
        }

        for (DirectGrant grant : held.grants()) {
            Change change = access.grant(subjectId, grant.permission(), grant.at(),
                    grant.allowed() ? Effect.ALLOW : Effect.DENY,
                    "Seeded from the policy document", BY, null);

            if (change.changed()) {
                written++;
            }
        }

        return written;
    }

    // ── ─────────────────────────────────────────────────────────────────────

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", impossible);
        }
    }
}
