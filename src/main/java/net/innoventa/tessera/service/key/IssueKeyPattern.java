package net.innoventa.tessera.service.key;

import net.innoventa.tessera.exception.BusinessRuleViolationException;
import org.jmouse.core.PlaceholderReplacer;
import org.jmouse.core.StandardPlaceholderReplacer;

import java.time.LocalDate;

/**
 * A key pattern, rendered — pattern and variables in, key out, and nothing else.
 *
 * <p>⚠️ <strong>Pure, and that is the point.</strong> A key is rendered inside the transaction that
 * allocated its number and again, twice a keystroke, by a settings screen previewing a format nobody
 * has saved. A function that reads no repository and holds no clock can be called from both without
 * either one having to care.
 *
 * <p>⚠️ <strong>The placeholders are jmouse's {@code ${…}}, not a hand-rolled pair of
 * {@code String.replace} calls.</strong> The old strategy substituted exactly {@code {key}} and
 * {@code {sequence}} and silently left anything else in the output, so a typo shipped as part of every
 * issue key in the project. The replacer resolves what it knows and the unknown-variable case is a
 * refusal with the name in it.
 *
 * <h2>The default slot carries the padding</h2>
 *
 * <p>{@code ${sequence:0000}} renders {@code 0042}. A placeholder's default value is normally what to
 * use when the variable is absent, and the sequence is never absent — so the slot is free, and its
 * <em>width</em> is exactly the one thing a numeric variable needs said about it. {@code ${sequence}}
 * with no slot is unpadded.
 */
public final class IssueKeyPattern {

    /** ⚠️ The one variable a pattern may not do without — see {@link #requireSequence}. */
    public static final String SEQUENCE = "sequence";

    public static final String KEY = "key";
    public static final String YEAR = "year";
    public static final String MONTH = "month";
    public static final String DAY = "day";

    private static final PlaceholderReplacer REPLACER = new StandardPlaceholderReplacer();

    private IssueKeyPattern() {
    }

    /**
     * The key this pattern produces for one allocated sequence.
     *
     * @param date the day the key is minted on — passed in rather than read, so a preview can render
     *             the same key the allocator would and a test can render any day it likes
     */
    public static String render(String pattern, String projectKey, int sequence, LocalDate date) {
        return REPLACER.replace(pattern, (variable, padding) -> switch (variable) {
            case KEY      -> projectKey;
            case SEQUENCE -> pad(sequence, padding);
            case YEAR     -> String.valueOf(date.getYear());
            case MONTH    -> two(date.getMonthValue());
            case DAY      -> two(date.getDayOfMonth());
            default       -> throw new BusinessRuleViolationException(
                "'${" + variable + "}' is not something a key pattern can contain. The variables are: "
                + String.join(", ", KEY, SEQUENCE, YEAR, MONTH, DAY) + ".");
        });
    }

    /**
     * ⚠️ <strong>A pattern without {@code ${sequence}} mints duplicate keys, so it is refused.</strong>
     *
     * <p>The per-project counter is the sole source of uniqueness (ADR-0003) — there is no second check
     * anywhere, because there has never needed to be one. A pattern of {@code ${key}-${year}} is a
     * project where every issue raised in a year has the same key, and nothing downstream would notice
     * until somebody followed a link to the wrong issue.
     */
    public static String requireSequence(String pattern) {
        String trimmed = pattern == null ? "" : pattern.trim();

        if (trimmed.isEmpty()) {
            throw new BusinessRuleViolationException("A custom key pattern cannot be empty.");
        }

        if (!trimmed.contains(REPLACER.prefix() + SEQUENCE)) {
            throw new BusinessRuleViolationException(
                "A key pattern has to contain ${sequence}. The project's counter is what makes a key "
                + "unique, and a pattern without it would give several issues the same key.");
        }

        return trimmed;
    }

    /** ⚠️ Left-padded to the width of the default slot; {@code ${sequence}} with no slot is unpadded. */
    private static String pad(int sequence, String padding) {
        String rendered = Integer.toString(sequence);

        if (padding == null || padding.isBlank() || rendered.length() >= padding.length()) {
            return rendered;
        }

        return "0".repeat(padding.length() - rendered.length()) + rendered;
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

}
