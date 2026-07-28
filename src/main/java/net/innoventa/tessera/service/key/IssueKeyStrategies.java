package net.innoventa.tessera.service.key;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The registry resolving a project's {@code keyStrategy} discriminator to its {@link IssueKeyStrategy}
 * bean. Adding a strategy is dropping in a new {@code @Component} — Spring injects the full set here,
 * so nothing else changes (Open/Closed, ADR-0003).
 */
@Component
public class IssueKeyStrategies {

    private final Map<String, IssueKeyStrategy> strategiesByName;

    public IssueKeyStrategies(List<IssueKeyStrategy> strategies) {
        this.strategiesByName = strategies.stream()
            .collect(Collectors.toMap(IssueKeyStrategy::name, Function.identity()));
    }

    public IssueKeyStrategy resolve(String strategyName) {
        IssueKeyStrategy strategy = strategiesByName.get(strategyName);
        if (strategy == null) {
            throw new IllegalStateException("No issue key strategy registered for '" + strategyName + "'");
        }
        return strategy;
    }

}
