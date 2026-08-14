package net.innoventa.tessera.dto.access;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the access screen reads and writes.
 *
 * <p>One file rather than nine, because these are one screen's vocabulary and splitting them costs a
 * reader eight files to learn what a single page shows.
 *
 * <p>⚠️ <strong>A role is addressed by NAME, not by an identifier.</strong> The engine stores one that
 * way and a name is what a policy document writes, so exposing the surrogate key would hand the client a
 * second way to say the same thing — and a second way is what the retiring {@code project_roles} table
 * was.
 */
public final class AccessAdministrationDtos {

    private AccessAdministrationDtos() {
    }

    /**
     * One permission this build knows about.
     *
     * @param description what the policy document says it means, or null where the document is silent —
     *                    which {@code DeclaredPolicyValidator} makes impossible, so null never happens
     *                    in a build that started
     */
    public record PermissionView(String name, String description) {}

    /**
     * A role, and everything it carries.
     *
     * @param assignableAt the widest scope it may be handed out at. ⚠️ Read-only here: it comes from the
     *                     policy document and changing it through a screen would let {@code PROJECT_ADMINISTRATOR}
     *                     be granted installation-wide, which is the one thing the field exists to stop
     * @param declared     whether {@code policy/tessera.jmp} declares this role. ⚠️ The screen has to say
     *                     so: a bundle edited here is rewritten from the document the next time its
     *                     checksum moves, and a role the document does not mention is not
     */
    public record RoleView(String name, String assignableAt, boolean declared, List<BundleEntryView> bundle) {}

    /**
     * One line of a role's bundle: a permission, and how far it reaches.
     *
     * @param carriedAt a scope <em>kind</em> and never an instance. {@code @PROJECT issue:edit} inside a
     *                  role means "as far as a project"; <em>which</em> project is decided by where the
     *                  role was assigned
     */
    public record BundleEntryView(String permission, String carriedAt) {}

    /** What a role should carry from now on — the whole bundle, never a difference. */
    public record SetBundleRequest(List<BundleEntryView> bundle) {}

    /**
     * One person's hold on one role, at one place.
     *
     * @param project where it was assigned, or null for an installation-wide holding
     */
    public record RoleHoldingView(
            MemberSummary member,
            String        roleName,
            String        scopeType,
            ProjectRef    project,
            String        source,
            LocalDateTime since) {}

    /** One person's personal allow or deny, at one place. */
    public record DirectHoldingView(
            MemberSummary member,
            String        permission,
            boolean       allowed,
            String        scopeType,
            ProjectRef    project,
            String        reason,
            LocalDateTime since) {}

    /**
     * A project named rather than identified.
     *
     * <p>A scope reference carries an identifier, which is right for the engine and useless on a screen —
     * nobody recognises a project by its key column.
     */
    public record ProjectRef(String id, String key, String name) {}

    /** Everything the screen shows at once, so it is one request rather than four. */
    public record AccessOverview(
            List<PermissionView>     permissions,
            List<RoleView>           roles,
            List<RoleHoldingView>    roleHoldings,
            List<DirectHoldingView>  directHoldings) {}
}
