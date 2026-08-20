package net.innoventa.tessera.security.access;

import org.jmouse.access.enforcement.ExternalAccessRules;
import org.jmouse.avatar.PublicAvatarController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What {@code jmouse-avatars}' controller requires — which is, deliberately, nothing.
 *
 * <h2>⚠️ Declared public rather than left out</h2>
 *
 * <p>{@code ForeignControllerAccessTest} refuses a library controller nothing has spoken about, and it is
 * right to: a handler Tessera mounts but did not write cannot carry {@code @PublicEndpoint}, so silence
 * about it is indistinguishable from having forgotten it.</p>
 *
 * <p>The two answers available before {@code publicType} existed were both wrong. Omitting
 * {@code org.jmouse.avatar} from that test's package list would drop the guarantee for every controller
 * that module ever ships, not just this one. Declaring {@code authenticated()} would be a lie that breaks
 * the route outright — the whole reason it is public is that the caller <em>cannot</em> sign in.</p>
 *
 * <h2>⚠️ What actually protects it, and what would stop protecting it</h2>
 *
 * <p>The address is a <strong>capability rather than a name</strong>: a random registry identifier that
 * cannot be constructed from knowing who somebody is, cannot be walked to the next person, and is only
 * ever learned from an authenticated response that already showed you that member.</p>
 *
 * <p>And the second half is {@code tessera.file.upload.profile} — an allowlist of raster image types with
 * SVG deliberately absent. This route serves whatever was stored under the type it was stored as, so if a
 * script host could be uploaded, this is where it would run against the visitor's session.
 * ⚠️ <strong>Widening that profile without revisiting this file is the mistake to never make.</strong></p>
 */
@Configuration
public class AvatarAccess {

    /**
     * The declaration.
     *
     * @return rules saying this one controller is public on purpose
     */
    @Bean
    public ExternalAccessRules avatarAccessRules() {
        return ExternalAccessRules.builder()
                .publicType(PublicAvatarController.class,
                            "An <img> tag cannot sign in. The address is a capability rather than a name "
                            + "— a random registry identifier that cannot be constructed from knowing who "
                            + "somebody is and cannot be walked to the next person — and the upload "
                            + "allowlist excludes every script host, which is what makes serving its "
                            + "bytes from this origin safe.")
                .build();
    }
}
