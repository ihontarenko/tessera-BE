package net.innoventa.tessera.config;

import net.innoventa.tessera.service.filter.IssueLinkTestExtension;
import org.jmouse.el.ExpressionLanguage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The jMouse-EL engine board filters are evaluated with (ADR-0008).
 * <p>
 * It is built with <strong>no {@code BeanLookup}</strong>, so a filter expression cannot reach a
 * container bean — the app-side half of the sandbox, mirroring Innoventa's
 * {@code ExpressionEvaluator}. That matters more here than it does there: Tessera is a resource
 * server and, from Phase 4 on, members author these expressions themselves. Everything a filter is
 * allowed to touch arrives as a plain context variable
 * ({@code issue}, {@code currentMember}, {@code now}); anything heavier ships as a registered jME
 * test, whose implementation we own — {@link IssueLinkTestExtension} being the first of them.
 * Curating the parser surface further ({@code class()}, static method imports) is owned on the
 * jMouse-EL side.
 */
@Configuration
public class JmeConfiguration {

    public static final String FILTER_EXPRESSION_LANGUAGE = "filterExpressionLanguage";

    /**
     * One engine for the whole application: it caches compiled expressions, so the board's handful of
     * built-in filters are parsed once across every request rather than once per render.
     */
    @Bean(FILTER_EXPRESSION_LANGUAGE)
    public ExpressionLanguage filterExpressionLanguage() {
        ExpressionLanguage expressionLanguage = new ExpressionLanguage();
        expressionLanguage.getExtensions().importExtension(new IssueLinkTestExtension());
        return expressionLanguage;
    }

}
