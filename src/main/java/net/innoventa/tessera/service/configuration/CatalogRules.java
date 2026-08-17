package net.innoventa.tessera.service.configuration;

import net.innoventa.tessera.dto.configuration.ConfigurationUsageReport;
import net.innoventa.tessera.exception.BusinessRuleViolationException;

/**
 * The three refusals every catalog shares, said once.
 *
 * <p>Pure and static: each is a sentence about values the caller has already loaded, and giving them a
 * repository would turn "what this rule says" into "what this rule fetches". The write services differ
 * in what they load and agree entirely on what to do about it.
 *
 * <h2>Why the last-row rule exists at all</h2>
 *
 * <p>The database refuses a delete that would orphan a row, and that covers almost everything. It cannot
 * express the one rule that is about the <em>future</em>: an empty priority catalog does not break any
 * existing issue, it breaks the next issue anybody tries to create. Same for resolutions — the failure
 * arrives at the next transition into a Done-category status, which has nothing to offer.
 *
 * <p>⚠️ Link types are deliberately exempt, and that is not an oversight to be tidied up later: a link
 * is optional, so an installation with no link types is coherent and merely means nobody links issues.
 */
final class CatalogRules {

    private CatalogRules() {
    }

    /**
     * ⚠️ A collision is a {@code 409} with words, never the database's own error.
     *
     * <p>A unique-constraint violation surfaces as a 500 with a constraint name in it, which tells an
     * administrator nothing and tells a support engineer only where to look. The catalog is small and
     * the check is one query.
     */
    static void requireNameAvailable(boolean taken, String catalog, String name) {
        if (taken) {
            throw new BusinessRuleViolationException(
                "A " + catalog + " called '" + name + "' already exists. Names are what filters compare "
                + "on, so two rows cannot share one.");
        }
    }

    /**
     * The rule the database cannot express: some catalogs may not become empty.
     *
     * @param remaining how many rows there would be after the deletion
     */
    static void requireCatalogSurvives(long remaining, String catalog, String because) {
        if (remaining <= 0) {
            throw new BusinessRuleViolationException(
                "This is the last " + catalog + ", and it cannot be deleted: " + because);
        }
    }

    /**
     * Deletion refused, with the report rather than in place of it.
     *
     * <p>The same {@link ConfigurationUsageReport} the screen showed a moment ago — see
     * {@link ConfigurationUsage} for why that identity matters — travels on the problem detail, so a
     * client can link to the holders instead of parsing the sentence.
     */
    static void requireNothingHoldsIt(ConfigurationUsageReport usage, String catalog, String name) {
        if (usage.isEmpty()) {
            return;
        }

        throw new BusinessRuleViolationException(
            "'" + name + "' cannot be deleted: it is held by " + usage.describe()
            + ". Remove it from those first, or leave it where it is.",
            usage);
    }

    /**
     * An optional field as it is stored: trimmed, and {@code null} when there is nothing there.
     *
     * <p>⚠️ <strong>An empty string is not the same value as null, and only one of them means
     * "default".</strong> A cleared input posts {@code ""}, which would store as a colour that renders
     * as nothing, or an icon key no drawing answers to. Emptiness is decided once, here, instead of at
     * each of the places that later ask "is there one?".
     *
     * <p>It validates no <em>shape</em>, deliberately. A colour column holds a CSS colour, and
     * {@code #8b5cf6}, {@code rebeccapurple} and {@code hsl(258 90% 66%)} are all one; a check that
     * recognised only the hex spelling would refuse valid values while still passing {@code #zzzzzz}.
     * An icon key does have a closed list, and that check belongs with the list rather than here.
     */
    static String storedOptional(String value) {
        String trimmed = value == null ? "" : value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    /** A name that is only whitespace is not a name; it would render as a blank row nobody can pick. */
    static String requireName(String name, String catalog) {
        String trimmed = name == null ? "" : name.trim();

        if (trimmed.isEmpty()) {
            throw new BusinessRuleViolationException("A " + catalog + " needs a name.");
        }

        return trimmed;
    }

}
