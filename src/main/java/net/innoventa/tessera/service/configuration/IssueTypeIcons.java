package net.innoventa.tessera.service.configuration;

import java.util.List;

/**
 * The icon keys the client actually draws.
 *
 * <p>⚠️ <strong>An unknown key is an invisible typo.</strong> {@code IssueTypeIcon} falls back to a
 * generic checkbox for anything it does not recognise, so {@code "buug"} produces no error, no warning
 * and no missing image — just a type that quietly looks like every other type forever. Free text was
 * therefore the wrong shape for this field, and the picker is built from this list so nothing can be
 * offered that the server would refuse or the client could not draw.
 *
 * <p>⚠️ <strong>It is a second copy of the client's map, and there is no way around that.</strong> The
 * icons are React components; the server cannot hold them and cannot derive their names. What it can do
 * is be the list the picker is built from, so the two are compared by an administrator looking at the
 * picker rather than by nobody. Adding an icon is two edits — here, and {@code issueVisuals.tsx} — and a
 * key added here alone shows up immediately as the generic fallback in the picker itself.
 */
public final class IssueTypeIcons {

    /**
     * The five the seed uses first, in hierarchy order, then the rest grouped by what they are for —
     * which is the order the picker offers them in.
     *
     * <p>The keys are what a type <em>is</em> rather than what it looks like: {@code incident} rather
     * than {@code siren}. An administrator picking one is describing their work, and a name describing
     * a drawing would go stale the moment the drawing changed.
     */
    public static final List<String> ALL = List.of(
            // The seeded five, in hierarchy order.
            "epic", "story", "task", "bug", "sub-task",
            // Wider containers — an Initiative at level 2 was always anticipated.
            // ⚠️ A hub is the one of these that does not contain its members. It gathers work that
            // lives in other projects, which parenthood cannot express today, so the glyph is spokes
            // meeting at a centre rather than a stack — the difference is the point of the type.
            "initiative", "milestone", "hub",
            // Kinds of work a team actually distinguishes.
            "improvement", "feature", "spike", "chore", "documentation", "design", "research",
            // Things that arrive rather than get planned.
            "incident", "support", "question", "risk", "security", "debt",
            // The two smallest, which are neither planned nor arriving — they are noticed. A papercut
            // is wrong and merely small; a nit was never wrong at all.
            "papercut", "nit");

    /**
     * Whether this is something the field may hold — which includes <em>nothing</em>.
     *
     * <p>Named for what it decides rather than for the list it consults: a blank icon key is not a
     * known icon, it is the absence of one, and a type without an icon renders the generic mark on
     * purpose. A method called {@code isKnown} answering true to null would be a small lie at the one
     * call site that matters.
     */
    public static boolean isAcceptable(String iconKey) {
        return iconKey == null || iconKey.isBlank() || ALL.contains(iconKey);
    }

    private IssueTypeIcons() {
    }

}
