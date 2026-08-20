package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.AccessOverview;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.AssignRoleRequest;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.GrantPermissionRequest;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.RevokePermissionRequest;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.RoleView;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.SetBundleRequest;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.AccessAdministrationService;
import net.innoventa.tessera.service.access.PolicyProjectionService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The installation's access screen: what each role carries, and who holds what where.
 *
 * <p>⚠️ <strong>Installation-wide, and behind a permission of its own.</strong> A role is not a
 * project's — changing what {@code PROJECT_DEVELOPER} carries changes it in every project at once — so
 * this cannot honestly sit under {@code project:administer}, which a person may hold in one project and
 * not the next. Reading who holds what across the installation is a disclosure surface for the same
 * reason.
 *
 * <p>⚠️ <strong>Editing here changes authorization with no deploy.</strong> The engine reads rows and
 * the policy document is only what a fresh installation was born with, so a bundle saved on this screen
 * is in force on the next request. The counterpart is written down in {@code AccessAdministrationService}
 * and shown to the user: a role the document declares is rewritten from the file whenever that file's
 * checksum moves.
 *
 * <p>Per-<em>project</em> administration — who is in a project, what role they hold there, and their
 * personal allow or deny — is {@code ProjectMembershipController}'s and stays gated on
 * {@code project:administer}. The two screens are different questions about different scopes.
 */
@RestController
@RequestMapping("/api/admin/access")
@RequiredArgsConstructor
@RequiresAccess(permission = Permissions.ADMINISTER_ACCESS, scope = Scopes.GLOBAL)
public class AccessAdministrationController {

    private final AccessAdministrationService accessAdministrationService;
    private final PolicyProjectionService     policyProjectionService;

    /** Everything the screen shows, in one request — the vocabulary, the roles, and every holding. */
    @GetMapping
    public AccessOverview overview() {
        return accessAdministrationService.overview();
    }

    /**
     * The same authorization, rendered back into the policy language — read-only (TSSR-20).
     *
     * <p>⚠️ <strong>Text, not JSON.</strong> The value of this tab is that it reads like the file
     * somebody already knows how to read; a structured payload the client re-renders would be a second
     * implementation of the grammar, and the two would drift.
     */
    @GetMapping(value = "/projection", produces = "text/plain;charset=UTF-8")
    public String projection() {
        return policyProjectionService.render();
    }

    /**
     * What a role carries from now on.
     *
     * <p>⚠️ The whole bundle, never a difference — see the service for why. Addressed by name, because a
     * role's name is what the engine stores and what a policy document writes; a surrogate identifier
     * here would be a second way to say the same thing.
     */
    @PutMapping("/roles/{roleName}/bundle")
    public RoleView setBundle(@PathVariable String roleName, @Valid @RequestBody SetBundleRequest request) {
        return accessAdministrationService.setBundle(roleName, request.bundle());
    }

    // ── Who holds what ────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>The screen would be a viewer without these.</strong> Reading who holds what and being
     * able to change it are the same job: an administrator who can see that somebody is missing a role
     * and has to go and edit a project's people screen to fix it is being shown a problem and handed no
     * tool. Project membership still has its own screen — this is the installation-wide one, and it can
     * reach a project too, because "give this person that role over there" is exactly the request
     * somebody brings to it.
     */
    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssignRoleRequest request) {
        accessAdministrationService.assign(
                request.memberId(), request.roleName(), request.projectId(), callerId(jwt));
    }

    @DeleteMapping("/assignments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@Valid @RequestBody AssignRoleRequest request) {
        accessAdministrationService.unassign(
                request.memberId(), request.roleName(), request.projectId());
    }

    // ── What one person holds personally ──────────────────────────────────────

    /**
     * ⚠️ <strong>A deny beats every role that grants it.</strong> This is the sharpest thing on the
     * screen: it is how one person loses one power without the role that gives it to everybody else
     * being touched — and it is why the service refuses one without a reason.
     */
    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GrantPermissionRequest request) {

        accessAdministrationService.grant(
                request.memberId(), request.permission(), request.allowed(), request.projectId(),
                request.reason(), callerId(jwt));
    }

    @DeleteMapping("/grants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ungrant(@Valid @RequestBody RevokePermissionRequest request) {
        accessAdministrationService.ungrant(
                request.memberId(), request.permission(), request.projectId());
    }

    /**
     * Who made the change, recorded beside it.
     *
     * <p>The identity-provider subject rather than the member id, because this column is read by a
     * person during an incident and {@code SU} means something to them where a UUID does not. It is
     * provenance, never an identity anything resolves.
     */
    private String callerId(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
