package net.innoventa.tessera.service.key;

import net.innoventa.tessera.exception.BusinessRuleViolationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry resolving a project's {@code keyStrategy} discriminator to its {@link IssueKeyStrategy}.
 *
 * <p>Two sources, in this order. Every {@link IssueKeyFormat} contributes a
 * {@link PatternIssueKeyStrategy}, because the five shipped shapes differ in one string and writing
 * five classes to say so is how the fifth comes to differ in two. Anything genuinely algorithmic —
 * a key that consults something outside the pattern — is still a {@code @Component} implementing the
 * interface, injected here and free to override a format of the same name (Open/Closed, ADR-0003).
 */
@Component
public class IssueKeyStrategies {

    private final Map<String, IssueKeyStrategy> strategiesByName = new LinkedHashMap<>();

    public IssueKeyStrategies(List<IssueKeyStrategy> strategies, Clock issueKeyClock) {
        IssueKeyFormat.all().forEach(format ->
            strategiesByName.put(format.name(), new PatternIssueKeyStrategy(format, issueKeyClock)));

        strategies.forEach(strategy -> strategiesByName.put(strategy.name(), strategy));
    }

    public IssueKeyStrategy resolve(String strategyName) {
        IssueKeyStrategy strategy = strategiesByName.get(strategyName);

        if (strategy == null) {
            throw new BusinessRuleViolationException(
                "'" + strategyName + "' is not a key format this build knows. Choose one of: "
                + String.join(", ", strategiesByName.keySet()) + ".");
        }

        return strategy;
    }

    /**
     * ⚠️ The clock is a bean rather than a static call, for the same reason {@code idGenerator} is one:
     * a date token read from inside the formatter would make the year in a key untestable, and dates
     * are the part of this feature most worth asserting about.
     */
    @Configuration
    static class ClockConfiguration {

        @Bean
        Clock issueKeyClock() {
            return Clock.systemDefaultZone();
        }

    }

}
