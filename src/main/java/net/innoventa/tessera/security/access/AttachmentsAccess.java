package net.innoventa.tessera.security.access;

import net.innoventa.tessera.security.Permissions;
import org.jmouse.access.enforcement.ExternalAccessRules;
import org.jmouse.files.management.FilesAccessRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What the library's file routes require, in Tessera's own words.
 *
 * <h2>⚠️ Declared, not annotated</h2>
 *
 * <p>{@code FileController} is the library's and cannot carry {@code @RequiresAccess} — an annotation
 * would win over this declaration and make Tessera's permissions unreachable, which looks exactly like a
 * rule that is being honoured. So the requirement moves instead of the enforcement, and the same engine
 * answers it on the same axes as every route Tessera wrote itself.</p>
 *
 * <h2>⚠️ Reading an attachment is reading the issue</h2>
 *
 * <p>{@code project:browse} for reading and {@code issue:edit} for writing, rather than a {@code file:*} of their own. An attachment is part
 * of what an issue discloses; a second permission beside it would be granted separately the first time
 * somebody forgot, and the two would then mean different things with nothing to say which was meant.</p>
 *
 * <p>⚠️ <strong>The far end of a re-file is not covered by this and cannot be.</strong> Moving an
 * attachment to another issue names the destination in the request body, where no rule can see it —
 * whoever wires that route up has to check it, or somebody who may write on their own issue can attach a
 * document to one they cannot see.</p>
 */
@Configuration
public class AttachmentsAccess {

    /**
     * The declaration.
     *
     * @return rules covering the library's file routes at project scope
     */
    @Bean
    public ExternalAccessRules attachmentAccessRules() {
        return FilesAccessRules.atScope(Scopes.PROJECT)
                .reading(Permissions.BROWSE_PROJECT)
                .writing(Permissions.EDIT_ISSUE)
                // ⚠️ Declared even though the surface is not mounted here. It is one line, and the
                // alternative is a GLOBAL disclosure of every stored object in the installation
                // inheriting the PROJECT-scoped write rule the moment somebody sets the property.
                .administeringWith(Permissions.ADMINISTER_CONFIGURATION)
                .build();
    }
}
