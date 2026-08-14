package net.innoventa.tessera.security;

import java.util.List;

/**
 * The role names {@code policy/tessera.jmp} declares, for the code that has to hand one out.
 *
 * <p><strong>These used to be rows in {@code project_roles}, looked up by display name</strong> — which
 * is how {@code ProjectService.create} came to search for the literal string {@code "Administrator"} and
 * throw <em>"Administrator role not seeded"</em> when it did not find it. A role is a row in
 * {@code access_roles} now, addressed by the name written in the document, and these constants are what
 * keeps a caller from spelling it themselves.
 *
 * <p>⚠️ <strong>Nothing checks these against the document.</strong> {@link Permissions} is checked in
 * both directions because a mistyped permission grants nothing silently; a mistyped role name here fails
 * loudly at the moment somebody is assigned it, because {@code AccessAdministration.assign} refuses a
 * role that does not exist. Loud is enough.
 */
public final class Roles {

    /** Read-only, and may comment. What somebody following work they are not doing needs. */
    public static final String PROJECT_VIEWER = "PROJECT_VIEWER";

    /** Works on issues — everything but deleting one and administering the project. */
    public static final String PROJECT_DEVELOPER = "PROJECT_DEVELOPER";

    /** Full control of the project, its settings and its membership. */
    public static final String PROJECT_ADMINISTRATOR = "PROJECT_ADMINISTRATOR";

    /**
     * The installation's way back in — editing the shared roles, reading who holds what, and editing
     * the catalogs every project runs on.
     *
     * <p>Deliberately not a superuser: it opens no project and transitions nothing. See the document,
     * which also says why {@code configuration:administer} joined it rather than getting a role.
     */
    public static final String GLOBAL_ACCESS_ADMINISTRATOR = "GLOBAL_ACCESS_ADMINISTRATOR";

    /** The three a person may be given in a project, widest last — the order a picker offers them in. */
    public static final List<String> PROJECT_ROLES =
            List.of(PROJECT_VIEWER, PROJECT_DEVELOPER, PROJECT_ADMINISTRATOR);

    private Roles() {
    }
}
