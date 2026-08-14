package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.AccessOverview;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.RoleView;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.SetBundleRequest;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.AccessAdministrationService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** Everything the screen shows, in one request — the vocabulary, the roles, and every holding. */
    @GetMapping
    public AccessOverview overview() {
        return accessAdministrationService.overview();
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
}
