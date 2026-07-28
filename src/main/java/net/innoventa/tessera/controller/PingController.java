package net.innoventa.tessera.controller;

import net.innoventa.tessera.dto.PingResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase-0 smoke test. Requires a valid Identity token whose audience contains {@code tessera};
 * returns the caller's subject so the auth wiring can be confirmed before the domain lands.
 */
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public PingResponse ping(@AuthenticationPrincipal Jwt jwt) {
        return new PingResponse("tessera", jwt.getSubject(), jwt.getAudience());
    }

}
