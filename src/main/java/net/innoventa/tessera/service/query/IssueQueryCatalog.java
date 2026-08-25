package net.innoventa.tessera.service.query;

import org.jmouse.query.schema.QuerySchema;
import org.jmouse.query.store.SchemaCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * What a saved query may be checked against — this product's sources, by name.
 *
 * <h2>⚠️ The library refuses to check without this, rather than checking nothing</h2>
 *
 * <p>Only the product knows what {@code issues} is. A catalogue supplied by the library would have to
 * answer "no schema, so it is fine" for every source it had never heard of — turning the check into a
 * formality, which is worse than an absence because it reads as a guarantee. So
 * {@code QueryStoreAutoConfiguration} makes {@code QueryLibrary} conditional on a bean of this type, and
 * this is that bean.</p>
 *
 * <h2>⚠️ Tessera's schema does not depend on the caller, and that is what makes checking-on-save right
 * here</h2>
 *
 * <p>{@code issue.points} means the same thing to everybody, so a query that parses against the schema
 * when it is saved parses against it when it is run. A product whose vocabulary widens per caller — a
 * form chosen, a workspace entered — cannot check on save without deciding which caller's vocabulary is
 * the real one, and has to check where the query is applied instead.</p>
 *
 * @author Ivan Hontarenko (Mr. Jerry Mouse)
 * @author ihontarenko@gmail.com
 */
@Configuration(proxyBeanMethods = false)
public class IssueQueryCatalog {

    @Bean
    public SchemaCatalog issueSchemaCatalog() {
        return SchemaCatalog.of(Map.<String, QuerySchema>of(IssueSchema.ISSUES, IssueSchema.schema()));
    }
}
