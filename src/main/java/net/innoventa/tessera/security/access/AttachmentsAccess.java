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
 * <h2>⚠️ Files have their own two permissions now, and this class used to argue the opposite</h2>
 *
 * <p>It said: {@code project:browse} to read and {@code issue:edit} to write, rather than a
 * {@code file:*} of their own — an attachment being part of what an issue discloses, and a second
 * permission beside it being the sort of thing somebody forgets to grant. That was right while an
 * attachment was the only file this product had.</p>
 *
 * <p>⚠️ <strong>It stopped being right when files got a tree (TSSR-0102).</strong> The builder below
 * takes ONE pair of permissions and one scope for the <em>whole</em> file surface — every file, every
 * folder, every tree. With the issue permissions in that pair, a member's own cabinet could only be
 * opened by somebody who may browse a project, and the branch an assistant files into, which belongs to
 * no project at all, could be opened by nobody.</p>
 *
 * <p>Nothing narrowed in the swap: every project role carries {@code file:read} beside
 * {@code project:browse} and {@code file:write} beside {@code issue:edit}. What changed is that there is
 * now something to say about files that is not also a sentence about issues.</p>
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
                .reading(Permissions.READ_FILE)
                .writing(Permissions.WRITE_FILE)
                // ⚠️ Declared even though the surface is not mounted here. It is one line, and the
                // alternative is a GLOBAL disclosure of every stored object in the installation
                // inheriting the PROJECT-scoped write rule the moment somebody sets the property.
                .administeringWith(Permissions.ADMINISTER_CONFIGURATION)
                .build();
    }
}
