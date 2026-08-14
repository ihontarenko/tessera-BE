package net.innoventa.tessera.security;

import java.util.List;

/**
 * Every permission this build can be asked about.
 *
 * <p><strong>These used to be rows.</strong> {@code permissions} was a table with a surrogate key per
 * name, joined to {@code project_role_permissions} to answer what a role carried — three copies of one
 * list, and a constant renamed here would silently stop matching a row and grant nothing. The engine
 * stores a permission <em>by name</em>, so the table is gone and this is the vocabulary.
 *
 * <p>⚠️ <strong>Checked against {@code policy/tessera.jmp} in both directions</strong> by
 * {@link net.innoventa.tessera.security.access.DeclaredPolicyValidator}. A name here the document does
 * not declare fails the boot; a name the document declares that no constant matches is litter, and
 * fails it too. That check is the whole reason writing the policy down is safe: a document is text, and
 * {@code @PROJECT BROWSE_PROJEKT} would otherwise load, match nothing, and say nothing.
 *
 * <h2>⚠️ They were renamed, and the policy grammar is why</h2>
 *
 * <p>They used to be {@code BROWSE_PROJECT}, {@code CREATE_ISSUE} and so on. A permission in a
 * {@code .jmp} document must be {@code namespace:action} — the colon is what the parser identifies the
 * shape <em>by</em>, since nothing else distinguishes a permission from one of the language's own
 * keywords — so a single-word name cannot be written down at all. {@code CursorMatcher} is explicit that
 * a grammar refusing more than two segments would be "a language dictating a product's vocabulary"; the
 * floor of two segments is the same thing said quietly, and it is worth recording as a library finding
 * rather than as a Tessera preference.
 *
 * <p>The rename is not a loss. {@code issue:edit} says the same thing as {@code EDIT_ISSUE} and reads
 * the way every other product in this workspace already writes one, and
 * {@code V000013__authorization_handover.sql} maps the old names across so nothing stored under one is
 * left behind.
 */
public final class Permissions {

    /** See the project and its issues. The permission every read in a project is gated on. */
    public static final String BROWSE_PROJECT = "project:browse";

    public static final String CREATE_ISSUE = "issue:create";
    public static final String EDIT_ISSUE = "issue:edit";
    public static final String ASSIGN_ISSUE = "issue:assign";
    public static final String TRANSITION_ISSUE = "issue:transition";
    public static final String DELETE_ISSUE = "issue:delete";

    /**
     * Comment on issues — and edit or delete your own.
     *
     * <p>{@code comment:write} rather than the {@code ADD_COMMENT} it replaces, because adding was never
     * all it carried: editing and deleting one's own comment have always been gated on it too.
     */
    public static final String ADD_COMMENT = "comment:write";

    public static final String MANAGE_SPRINT = "sprint:manage";

    /** Settings, membership, roles and personal overrides — the project's own administration. */
    public static final String ADMINISTER_PROJECT = "project:administer";

    /**
     * Editing the roles every project shares, and reading who holds what across the installation.
     *
     * <p>⚠️ <strong>Installation-wide on purpose, and the one permission this move adds.</strong> A role
     * is not a project's: changing what {@code PROJECT_DEVELOPER} carries changes it everywhere at once,
     * which is not a power {@link #ADMINISTER_PROJECT} can honestly contain — somebody administering one
     * project would be editing everybody else's. Reading who holds what installation-wide is a
     * disclosure surface for the same reason.
     */
    public static final String ADMINISTER_ACCESS = "access:administer";

    /**
     * Editing the catalogs every project runs on — statuses, workflows, issue types, priorities,
     * resolutions, link types and both scheme kinds.
     *
     * <p>⚠️ <strong>Installation-wide, for the same reason {@link #ADMINISTER_ACCESS} is.</strong> A
     * workflow is shared: {@code IssueTypeScheme}'s own javadoc says the configuration is reused across
     * projects by design, so adding a transition changes what every project on that workflow may do.
     * That is not a power {@link #ADMINISTER_PROJECT} can honestly contain — somebody administering one
     * project would be editing everybody else's.
     *
     * <p>⚠️ <strong>Named for the catalogs, not for the instance.</strong> {@code instance:administer}
     * would read as the right name today and be wrong the moment an installation-wide concern arrives
     * that is not the configuration — accounts, licensing, quotas. Those should have to ask for a grant
     * of their own rather than inherit this one.
     *
     * <p>Reads are deliberately <em>not</em> gated on it: {@code GET /api/configuration} stays open to
     * any signed-in caller, because every picker in the product is built from it. Only writes ask.
     */
    public static final String ADMINISTER_CONFIGURATION = "configuration:administer";

    private static final List<String> ALL = List.of(
            BROWSE_PROJECT,
            CREATE_ISSUE,
            EDIT_ISSUE,
            ASSIGN_ISSUE,
            TRANSITION_ISSUE,
            DELETE_ISSUE,
            ADD_COMMENT,
            MANAGE_SPRINT,
            ADMINISTER_PROJECT,
            ADMINISTER_ACCESS,
            ADMINISTER_CONFIGURATION);

    /**
     * The catalogue, as the engine's third registration.
     *
     * <p>⚠️ <strong>Nothing on the decision path reads it.</strong> A permission is a bare string where
     * it is <em>asked about</em>, and an engine looking every one up on every request would have bought
     * a map lookup for nothing. It exists for the two readers that are not the engine: whatever
     * <em>writes</em> a grant, and whatever <em>checks</em> one somebody else wrote.
     */
    public static List<String> all() {
        return ALL;
    }

    private Permissions() {
    }

}
