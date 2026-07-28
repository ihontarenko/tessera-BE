package net.innoventa.tessera.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React SPA shell for the client-side routes React Router owns. The build output lives
 * directly under {@code src/main/resources/static} (see {@code Tessera/UI}'s {@code vite.config.ts}
 * {@code outDir}, written there by the frontend-maven-plugin), so Spring Boot's default static
 * resource handler already serves {@code /} (its welcome-page behavior finds {@code static/index.html}
 * automatically) and {@code /assets/**}; this controller only covers the deep routes that have no
 * matching static file, so a hard refresh or a shared deep link returns the shell instead of a 404.
 * A forward, not a redirect, so the browser's address bar and the requested path stay intact and
 * React Router can pick them up.
 * <p>
 * Extend this list (and the matching permit list in {@code SecurityConfiguration}) as new top-level
 * route sections are added — the same explicit-list approach Identity uses.
 */
@Controller
public class SinglePageApplicationController {

    @GetMapping({"/dashboard", "/projects/**", "/boards/**", "/backlog/**", "/issues/**", "/settings/**"})
    public String forwardToApplicationShell() {
        return "forward:/index.html";
    }

}
