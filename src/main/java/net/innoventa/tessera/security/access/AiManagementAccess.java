package net.innoventa.tessera.security.access;

import net.innoventa.tessera.security.Permissions;
import org.jmouse.access.enforcement.ExternalAccessRules;
import org.jmouse.access.enforcement.ExternalAccessRules.Declaration;
import org.jmouse.ai.management.AgentAdministrationController;
import org.jmouse.ai.management.OverviewController;
import org.jmouse.ai.management.ProviderAdministrationController;
import org.jmouse.ai.management.ProviderController;
import org.jmouse.ai.management.ToolCallHistoryController;
import org.jmouse.ai.management.ToolCatalogController;
import org.jmouse.ai.management.UsageController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What {@code jmouse-ai-management}'s controllers require — stated here, because they cannot state it
 * themselves.
 *
 * <p><strong>This is the file that let Tessera use the library instead of copying it.</strong> Those
 * six handlers belong to a library, so no {@code @RequiresAccess} can be written on them, and the only
 * gate left used to be a URL-prefix rule in the web layer keyed on a <em>role</em>. Innoventa met that
 * and concluded the module was unusable — in a product whose whole access model exists to stop rules
 * being written about roles — and wrote its own controller over the same ports. Two products, two
 * copies, one set of rules.
 *
 * <p>{@link ExternalAccessRules} is the other answer: the <em>declaration</em> moves rather than the
 * enforcement. These routes are decided by the same engine, on the same five axes, with the same
 * refusals and the same audit as every route Tessera wrote itself — and the requirement is a compile-time
 * constant beside all the others rather than a string in a security configuration.
 *
 * <p>⚠️ <strong>Two permissions, because these are two different powers.</strong> Everything that reads
 * asks {@link Permissions#READ_AI}: it discloses what every caller has asked the tools to do, across the
 * installation. The one that writes asks {@link Permissions#ADMINISTER_AI}, which decides what this
 * installation sends somebody else's servers and holds the key it pays with. Reading the trail does not
 * imply spending the money.
 *
 * <p>⚠️ <strong>At {@code GLOBAL}, and there is no other honest scope.</strong> The catalogue, the
 * counters and the provider belong to the installation; none of them is any project's, so none of them
 * can be gated per project.
 */
@Configuration
public class AiManagementAccess {

    @Bean
    public ExternalAccessRules aiManagementAccessRules() {
        Declaration reading      = Declaration.permission(Permissions.READ_AI).atScope(Scopes.GLOBAL);
        Declaration administering = Declaration.permission(Permissions.ADMINISTER_AI).atScope(Scopes.GLOBAL);

        return ExternalAccessRules.builder()
                .type(OverviewController.class,        reading)
                .type(ProviderController.class,        reading)
                .type(ToolCatalogController.class,     reading)
                .type(ToolCallHistoryController.class, reading)
                .type(UsageController.class,           reading)
                // ⚠️ The whole type, including its GET. Reading the stored configurations is reading
                // which keys exist and where they point, which belongs with the power to change them
                // rather than with the power to watch what the tools did.
                .type(ProviderAdministrationController.class, administering)
                // ⚠️ Administering too, and the whole type for the same reason. Its reads disclose every
                // agent in the installation, who owns each and which clients hold a credential — and its
                // writes switch an agent off or end somebody's client mid-session. Neither belongs with
                // the power to watch what the tools did.
                .type(AgentAdministrationController.class, administering)
                .build();
    }
}
