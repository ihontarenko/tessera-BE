package net.innoventa.tessera.service.key;

import java.time.Clock;
import java.time.LocalDate;

/**
 * One {@link IssueKeyStrategy} per {@link IssueKeyFormat}, all of them this class.
 *
 * <p>⚠️ <strong>Five formats, one implementation.</strong> The registry still resolves a bean per
 * discriminator, exactly as ADR-0003 describes — but writing five near-identical classes to differ in
 * one string is how the fifth one comes to differ in two.
 *
 * <p>⚠️ <strong>The clock is injected, mirroring the {@code Supplier<String> idGenerator} this codebase
 * already passes around.</strong> A date token read from {@code LocalDate.now()} inside the formatter
 * would make the year in a key untestable, and a key format is precisely the kind of thing somebody
 * wants to assert about a year that is not this one.
 */
public class PatternIssueKeyStrategy implements IssueKeyStrategy {

    private final IssueKeyFormat format;
    private final Clock          clock;

    public PatternIssueKeyStrategy(IssueKeyFormat format, Clock clock) {
        this.format = format;
        this.clock = clock;
    }

    @Override
    public String name() {
        return format.name();
    }

    /**
     * ⚠️ The project's pattern is used only by {@link IssueKeyFormat#CUSTOM}; every other format
     * ignores it, so a project that switches away from CUSTOM does not have to have its pattern
     * cleared to stop it taking effect.
     */
    @Override
    public String format(String projectKey, int sequence, String pattern) {
        String template = format.pattern() != null ? format.pattern() : IssueKeyPattern.requireSequence(pattern);

        return IssueKeyPattern.render(template, projectKey, sequence, LocalDate.now(clock));
    }

}
