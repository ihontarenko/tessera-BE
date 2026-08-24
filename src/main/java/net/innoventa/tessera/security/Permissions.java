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

    /**
     * Raise a new project.
     *
     * <p>⚠️ <strong>Installation-wide, because there is no project yet to scope it to.</strong> Every
     * other permission here answers "may you do this <em>here</em>"; this one cannot, and that is exactly
     * why it had to exist. {@code projects_create} declared {@link #BROWSE_PROJECT} instead — the only
     * permission it could honestly borrow — and the borrowing made it unreachable: {@code project:browse}
     * is carried by the three {@code @PROJECT} roles and by nothing else, so creating a project required
     * already belonging to one. A person who belonged to none could never create their first.
     *
     * <p>⚠️ <strong>The HTTP route is deliberately not gated on this.</strong> {@code ProjectController}
     * asks for nothing but a signed-in caller and keeps doing so — narrowing a screen people already use
     * would be a new rule smuggled in as a fix. The two surfaces differ on purpose: a project cannot be
     * deleted through any surface at all, so a mistaken one is permanent, and a conversation is the
     * surface where a mistaken one is likeliest.
     */
    public static final String CREATE_PROJECT = "project:create";

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
    /**
     * Listing and reading files — an issue's attachments, and whatever else is filed in the tree.
     *
     * <h3>⚠️ Its own permission, reversing what {@code AttachmentsAccess} used to say</h3>
     *
     * <p>The file routes borrowed {@link #BROWSE_PROJECT} and {@link #EDIT_ISSUE} while an attachment was
     * the only file this product had. The reasoning was sound then: an attachment is part of what an
     * issue discloses, and a second permission beside it would be granted separately the first time
     * somebody forgot.</p>
     *
     * <p>It stopped being sound when files got a tree of their own (TSSR-0102). ⚠️ <strong>The library
     * gates its whole file surface with ONE pair of permissions</strong> — files and directories, every
     * tree at once — so borrowing the issue permissions means a personal file cabinet can only be reached
     * by somebody who may browse a project, and a folder that belongs to nobody's project is reachable by
     * nobody. Two of the three trees are not about issues, and a permission named for issues cannot
     * honestly gate them.</p>
     *
     * <p>Nothing is narrowed by the change: every project role below carries both of these alongside the
     * permissions it already had, so whoever could see an attachment still can. What it buys is the
     * ability to say something about files that is not also a sentence about issues — which is exactly
     * the argument {@link #READ_PAGE} won against {@link #BROWSE_PROJECT}.</p>
     */
    public static final String READ_FILE = "file:read";

    /** Uploading, renaming, re-filing and deleting files, and arranging the folders they sit in. */
    public static final String WRITE_FILE = "file:write";

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

    /**
     * Administering the installation's people and clients — their names and their faces (TSSR-79).
     *
     * <p>⚠️ <strong>Its own, because {@link #ADMINISTER_CONFIGURATION} says so.</strong> That one is
     * named for the catalogs and its javadoc states that an installation-wide concern which is not the
     * configuration — <em>accounts, licensing, quotas</em> — should have to ask for a grant of its own
     * rather than inherit it. This is the first of those to arrive.
     *
     * <p>⚠️ <strong>It does not gate the member directory.</strong> {@code GET /api/members} stays open
     * to every signed-in caller: it is the picker somebody adds a colleague to a project from, and
     * gating it would mean only administrators could name a person. This gates the screen that
     * <em>administers</em> those rows.
     */
    public static final String ADMINISTER_MEMBERS = "member:administer";

    /**
     * See what the tools have been asked to do, and what is in force behind the assistant.
     *
     * <p>⚠️ <strong>A disclosure surface rather than a convenience.</strong> It reports every caller's
     * activity across the installation — which action, on whose behalf, how often, and how it ended —
     * so it is asked for by name rather than inherited from administering one project's issues.
     *
     * <p>⚠️ <strong>Deliberately not folded into {@link #ADMINISTER_CONFIGURATION}.</strong> That one
     * is named for the catalogs, and its own javadoc says an installation-wide concern that is not the
     * configuration should have to ask for a grant of its own. The model this installation talks to is
     * exactly such a concern.
     */
    public static final String READ_AI = "ai:read";

    /**
     * Choose the model, hold the key, and decide what is in force.
     *
     * <p>⚠️ <strong>Separate from {@link #READ_AI} because these are two different powers.</strong>
     * Reading the trail says what has been spent; this one decides what this installation sends
     * somebody else's servers and holds the credential it pays with. Reading does not imply spending,
     * and the intersection is nobody's convenience.
     */
    public static final String ADMINISTER_AI = "ai:administer";

    private static final List<String> ALL = List.of(
            BROWSE_PROJECT,
            CREATE_PROJECT,
            CREATE_ISSUE,
            EDIT_ISSUE,
            ASSIGN_ISSUE,
            TRANSITION_ISSUE,
            DELETE_ISSUE,
            ADD_COMMENT,
            MANAGE_SPRINT,
            ADMINISTER_PROJECT,
            ADMINISTER_ACCESS,
            ADMINISTER_CONFIGURATION,
            READ_AI,
            ADMINISTER_AI);

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
