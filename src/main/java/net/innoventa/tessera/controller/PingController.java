package net.innoventa.tessera.controller;

import net.innoventa.tessera.dto.PingResponse;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase-0 smoke test. Requires a valid Identity token whose audience contains {@code tessera};
 * returns the caller's subject so the auth wiring can be confirmed before the domain lands.
 *
 * <p>A bare {@code @RequiresAccess} — a signed-in caller and nothing more. It is not
 * {@code @PublicEndpoint}, because the whole thing it smoke-tests is that a token arrived.
 */
@RestController
@RequestMapping("/api")
@RequiresAccess
public class PingController {

    @GetMapping("/ping")
    public PingResponse ping(@AuthenticationPrincipal Jwt jwt) {
        return new PingResponse("tessera", jwt.getSubject(), jwt.getAudience());
    }

}
