package net.innoventa.tessera.service.key;

import org.springframework.stereotype.Component;

/**
 * The default {@link IssueKeyStrategy}: {@code {projectKey}-{sequence}} → {@code TIC-1}, {@code TIC-2}
 * (ADR-0003). Every project seeded in Phase 1 uses this. The {@code pattern} is honoured if supplied
 * (tokens {@code {key}} and {@code {sequence}}); a null pattern falls back to the canonical
 * {@code {key}-{sequence}} form.
 */
@Component
public class PrefixedSequenceKeyStrategy implements IssueKeyStrategy {

    public static final String NAME = "PREFIXED_SEQUENCE";

    private static final String DEFAULT_PATTERN = "{key}-{sequence}";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String format(String projectKey, int sequence, String pattern) {
        String template = (pattern == null || pattern.isBlank()) ? DEFAULT_PATTERN : pattern;
        return template
            .replace("{key}", projectKey)
            .replace("{sequence}", Integer.toString(sequence));
    }

}
