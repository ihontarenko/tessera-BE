package net.innoventa.tessera.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.PublicAvatarRoutes;
import net.innoventa.tessera.service.MemberAvatarService;
import org.jmouse.access.enforcement.PublicEndpoint;
import org.jmouse.storage.delivery.DeliveryIntent;
import org.jmouse.storage.spring.DeliveryIntents;
import org.jmouse.storage.spring.DeliveryRenderer;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bytes of an uploaded avatar, served to anybody who has the address.
 *
 * <p>⚠️ <strong>THE ONE UNAUTHENTICATED ROUTE IN THIS API, AND IT IS UNAUTHENTICATED BECAUSE AN
 * {@code <img>} TAG CANNOT SIGN IN.</strong> A browser fetching an image sends no Authorization header
 * and there is no way to give it one — every alternative means fetching each avatar as a blob in
 * JavaScript and managing an object URL's lifetime at each of the fifteen places a person is rendered.
 * That is a large amount of machinery in exchange for guarding a thumbnail.
 *
 * <p>What actually protects it is that the address is a capability rather than a name. It carries a
 * random registry identifier, not a member id, a subject or an email: it cannot be constructed from
 * knowing who somebody is, cannot be walked to find the next person, and is only ever learned from an
 * authenticated response that already showed you that member. Nothing here discloses whose face it is.
 *
 * <p>⚠️ And the second half of that guarantee is {@code tessera.file.upload} — an allowlist of raster
 * image types with SVG deliberately absent. This route serves whatever was stored under the type it
 * was stored as, so if a script host could be uploaded, this is where it would be executed from.
 * Widening that allowlist and leaving this route public is the mistake to never make.
 */
@RestController
@RequiredArgsConstructor
@PublicEndpoint("An <img> tag cannot sign in. The address is a capability rather than a name — a random "
              + "registry identifier that cannot be constructed from knowing who somebody is, cannot be "
              + "walked to the next person, and is only ever learned from an authenticated response that "
              + "already showed you that member.")
public class PublicAvatarController {

    private final MemberAvatarService memberAvatarService;
    private final DeliveryRenderer    renderer;

    /**
     * @param storedFileId the registry identifier the payload handed out — see
     *                     {@link PublicAvatarRoutes}
     */
    @GetMapping("/api/public/avatars/{storedFileId}")
    public ResponseEntity<Resource> serve(@PathVariable String storedFileId, HttpServletRequest request) {
        // PUBLIC rather than OWNER: it decides which of the two cache lifetimes the planner applies, and
        // a content-addressed avatar can never change under a cached copy — replacing your picture
        // produces a different address.
        DeliveryIntent intent = DeliveryIntents.of(request, false, DeliveryIntent.Audience.PUBLIC);

        return renderer.render(memberAvatarService.planDelivery(storedFileId, intent));
    }

}
