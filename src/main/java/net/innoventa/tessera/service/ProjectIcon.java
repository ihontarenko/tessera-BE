package net.innoventa.tessera.service;

import net.innoventa.tessera.exception.BusinessRuleViolationException;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * What may be stored as a project's icon: <strong>one emoji, or nothing</strong> (TSSR-7).
 *
 * <p>⚠️ <strong>One emoji is not one character, and that is the whole reason this class exists.</strong>
 * A flag is two regional indicators; a family is up to seven code points joined by zero-width joiners; a
 * thumbs-up with a skin tone is two. Every one of those draws as a single glyph, and every one of them
 * would fail a {@code length() == 1} test — which is exactly the check somebody writes when this rule is
 * left inline at a call site. The unit the rule is about is a <em>grapheme cluster</em>, which is what
 * {@link BreakIterator#getCharacterInstance} counts.
 *
 * <p>⚠️ <strong>And one grapheme cluster is not enough on its own:</strong> {@code T} is one too. So the
 * value must additionally contain something the platform recognises as emoji ({@link Character#isEmoji}),
 * which is what stops the field quietly becoming a one-letter text label — the thing a "just a short
 * string" column always turns into.
 *
 * <p>Blank in, null out. No icon is the normal state of a project, not a missing value, and a form that
 * submits an empty field is clearing it rather than failing.
 */
public final class ProjectIcon {

    /**
     * The column's ceiling, in characters — mirrored from {@code V000021} so a value that would be
     * truncated by the database is refused here with a sentence instead.
     */
    public static final int MAXIMUM_LENGTH = 16;

    /**
     * The stored form of {@code candidate}, or null where there is none.
     *
     * @throws BusinessRuleViolationException when it is longer than one emoji, or is not emoji at all
     */
    public static String normalize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        String icon = candidate.trim();

        if (icon.length() > MAXIMUM_LENGTH) {
            throw new BusinessRuleViolationException(
                "A project icon is a single emoji; this is " + icon.length() + " characters long");
        }

        if (!isSingleGraphemeCluster(icon)) {
            throw new BusinessRuleViolationException("A project icon is a single emoji, not several");
        }

        if (!containsEmoji(icon)) {
            throw new BusinessRuleViolationException("A project icon has to be an emoji — letters and digits are the key's job");
        }

        return icon;
    }

    /**
     * Does the whole string draw as one glyph? {@code BreakIterator} is asked rather than a regular
     * expression because the answer changes with every Unicode release, and the platform's own table is
     * the one that will be right next year.
     */
    private static boolean isSingleGraphemeCluster(String icon) {
        BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
        clusters.setText(icon);

        return clusters.next() == icon.length();
    }

    /** Whether any code point in it is emoji — what separates 😀 from {@code T}, both single clusters. */
    private static boolean containsEmoji(String icon) {
        return icon.codePoints().anyMatch(Character::isEmoji);
    }

    private ProjectIcon() {
    }

}
