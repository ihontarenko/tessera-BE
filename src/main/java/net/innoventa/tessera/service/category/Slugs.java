package net.innoventa.tessera.service.category;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * A name, as something that can appear in a URL: lowercase, ASCII, hyphen-separated.
 *
 * <p>⚠️ <strong>Accents are stripped, and every other alphabet collapses.</strong> Normalising to NFD
 * and dropping the combining marks turns {@code Café} into {@code cafe}, which is the point — but it
 * turns {@code Документація} into nothing at all, because Cyrillic letters are not decorated Latin ones.
 * A name that slugifies to nothing gets {@link #FALLBACK} plus whatever the caller's uniqueness rule
 * appends, so a project written in Ukrainian ends up with {@code section}, {@code section-2},
 * {@code section-3}. Ugly in the address bar and harmless everywhere else: the slug is an identifier,
 * and the name is what anybody actually reads.
 */
public final class Slugs {

    /** What a name made entirely of characters this cannot carry becomes. */
    public static final String FALLBACK = "section";

    private static final int MAXIMUM_LENGTH = 96;

    private Slugs() {
    }

    public static String of(String text) {
        if (text == null || text.isBlank()) {
            return FALLBACK;
        }

        String withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = withoutMarks.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+)|(-+$)", "");

        if (slug.isBlank()) {
            return FALLBACK;
        }

        return slug.length() > MAXIMUM_LENGTH ? trimToWord(slug) : slug;
    }

    /**
     * The same slug, made unique against whatever the caller already has.
     *
     * <p>The caller passes the test rather than a collection, because "taken" is a question only it can
     * answer — a category asks its project, a page asks its own table, and a rename has to consider its
     * own current slug not taken.
     */
    public static String unique(String text, Predicate<String> isTaken) {
        String candidate = of(text);

        if (!isTaken.test(candidate)) {
            return candidate;
        }

        for (int suffix = 2; suffix < 1000; suffix++) {
            String numbered = candidate + "-" + suffix;

            if (!isTaken.test(numbered)) {
                return numbered;
            }
        }

        throw new IllegalStateException("Could not find a free slug for '%s'".formatted(text));
    }

    /** Cuts at the last hyphen inside the limit, so a truncated slug does not end mid-word. */
    private static String trimToWord(String slug) {
        String clipped = slug.substring(0, MAXIMUM_LENGTH);
        int lastHyphen = clipped.lastIndexOf('-');

        return lastHyphen > 0 ? clipped.substring(0, lastHyphen) : clipped;
    }

}
