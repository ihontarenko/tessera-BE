package net.innoventa.tessera.service.configuration;

import net.innoventa.tessera.exception.BusinessRuleViolationException;

import java.util.Collection;
import java.util.List;

/**
 * The refusals both scheme kinds share, said once — the sibling of {@link CatalogRules} one level up.
 *
 * <p>Pure and static for the same reason: each is a sentence about values the caller has already
 * loaded. The two write services differ entirely in what they load and not at all in what they do
 * about it.
 *
 * <h2>Why a scheme cannot lose its last row</h2>
 *
 * <p>Same shape as the catalog rule, one level up. An installation with no issue-type schemes does not
 * break any existing project — every one of them points at a scheme that still exists, because the
 * database would not have let it go. It breaks the <em>next</em> project, which has nothing to be
 * created on, and it breaks it at the settings screen's expense rather than the deleter's.
 */
final class SchemeRules {

    private SchemeRules() {
    }

    /** ⚠️ A collision is a 409 with words, never the unique constraint's own error. See {@link CatalogRules}. */
    static void requireNameAvailable(boolean taken, String kind, String name) {
        if (taken) {
            throw new BusinessRuleViolationException(
                "A " + kind + " called '" + name + "' already exists. A scheme is chosen by name on "
                + "every project's settings, so two cannot share one.");
        }
    }

    /** @param remaining how many schemes of the kind there would be after the deletion */
    static void requireKindSurvives(long remaining, String kind) {
        if (remaining <= 0) {
            throw new BusinessRuleViolationException(
                "This is the last " + kind + ", and it cannot be deleted: every project points at one, "
                + "and a new project would have nothing to be created on.");
        }
    }

    /**
     * ⚠️ The rule that makes a whole-scheme write worth the payload.
     *
     * <p>A scheme's default must be one of its own members. Split across add / remove / set-default
     * routes this is unenforceable in the middle — removing the member that is the default has to
     * either be refused, which makes replacing it a dance, or leave a scheme in a state no rule
     * describes and a {@code NOT NULL} column nulled. Sent whole, it is one comparison.
     */
    static void requireDefaultIsMember(Collection<String> memberIds, String defaultId, String noun) {
        if (memberIds.contains(defaultId)) {
            return;
        }

        throw new BusinessRuleViolationException(
            "The scheme's default " + noun + " has to be one the scheme grants. Add it to the scheme, "
            + "or choose a default from what is already in it.");
    }

    /** ⚠️ Two rows for one member is a scheme with an ambiguous order, and the unique constraint's 500. */
    static void requireNoDuplicates(List<String> ids, String noun) {
        if (ids.size() == ids.stream().distinct().count()) {
            return;
        }

        throw new BusinessRuleViolationException(
            "The same " + noun + " is in this scheme twice. Each may appear once.");
    }

    /** A name that is only whitespace would render as a blank row in every scheme picker. */
    static String requireName(String name, String kind) {
        String trimmed = name == null ? "" : name.trim();

        if (trimmed.isEmpty()) {
            throw new BusinessRuleViolationException("A " + kind + " needs a name.");
        }

        return trimmed;
    }

}
