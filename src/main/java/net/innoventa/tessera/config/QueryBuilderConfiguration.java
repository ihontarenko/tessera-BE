package net.innoventa.tessera.config;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.service.query.CurrentMembers;
import org.jmouse.access.enforcement.ExternalAccessRules;
import org.jmouse.access.enforcement.ExternalAccessRules.Declaration;
import org.jmouse.query.spring.builder.QueryBuilderController;
import org.jmouse.query.spring.builder.QueryCallers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mounts the shared filter builder, and says who may reach it.
 *
 * <h2>⚠️ Two gates at two levels, and both are needed</h2>
 *
 * <p>{@link ExternalAccessRules} declares that the library's controller needs somebody <strong>signed
 * in</strong> — it cannot say more, because one address family serves every listing a product publishes
 * and they do not share a permission. What each listing needs is declared by its own
 * {@code QuerySubject}, which knows what it is.</p>
 *
 * <h2>⚠️ Declared, never enforced here</h2>
 *
 * <p>The engine decides, on the same axes and with the same refusals and audit as every route Tessera
 * wrote itself. A URL-prefix rule in a security configuration would be a second, quieter access model
 * beside the one that exists to prevent them.</p>
 *
 * @author Ivan Hontarenko (Mr. Jerry Mouse)
 * @author ihontarenko@gmail.com
 */
@Configuration
@RequiredArgsConstructor
public class QueryBuilderConfiguration {

    private final CurrentMembers members;

    @Bean
    public ExternalAccessRules queryBuilderAccessRules() {
        return ExternalAccessRules.builder()
                .type(QueryBuilderController.class, Declaration.authenticated())
                .build();
    }

    /**
     * What {@code currentMember} means.
     *
     * <p>⚠️ An identifier, never the member. The library holding {@code Member} would make every
     * product's member the same type, which is the coupling the whole module exists without.</p>
     */
    @Bean
    public QueryCallers queryCallers() {
        return members::identifier;
    }
}
