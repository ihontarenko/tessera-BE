package net.innoventa.tessera.security;

/**
 * Project-permission name constants, matching the {@code name} rows seeded in
 * {@code V000004__authorization.sql}. Referenced by services when gating an action
 * ({@code projectPermissionService.require(member, projectId, Permissions.ADMINISTER_PROJECT)})
 * rather than repeating the raw string. Unlike Moneta's {@code Privileges} bean these are checked
 * per-project in the service layer, not via {@code @PreAuthorize}, since a member holds different
 * permissions in each of their projects — so a plain constants holder, not a Spring bean.
 */
public final class Permissions {

    public static final String BROWSE_PROJECT = "BROWSE_PROJECT";
    public static final String CREATE_ISSUE = "CREATE_ISSUE";
    public static final String EDIT_ISSUE = "EDIT_ISSUE";
    public static final String ASSIGN_ISSUE = "ASSIGN_ISSUE";
    public static final String TRANSITION_ISSUE = "TRANSITION_ISSUE";
    public static final String DELETE_ISSUE = "DELETE_ISSUE";
    public static final String ADD_COMMENT = "ADD_COMMENT";
    public static final String MANAGE_SPRINT = "MANAGE_SPRINT";
    public static final String ADMINISTER_PROJECT = "ADMINISTER_PROJECT";

    private Permissions() {
    }

}
