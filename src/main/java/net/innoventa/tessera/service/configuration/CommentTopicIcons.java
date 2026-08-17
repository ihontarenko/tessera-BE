package net.innoventa.tessera.service.configuration;

import java.util.List;

/**
 * The icon keys the client draws for a comment topic (TSSR-30).
 *
 * <p>⚠️ <strong>An unknown key is an invisible typo</strong>, exactly as in {@link IssueTypeIcons}: the
 * client falls back to a generic mark, so a misspelling produces no error and no missing image — just a
 * topic that looks like every other topic forever. The picker is built from this list, so nothing can be
 * offered that the server would refuse or the client could not draw.
 *
 * <p>⚠️ <strong>A separate list from {@link IssueTypeIcons}, on purpose.</strong> Those keys name kinds
 * of <em>work</em> — {@code epic}, {@code bug}, {@code spike} — and a topic names a kind of
 * <em>remark</em>. Sharing the list would offer "Epic" as the drawing for a comment about a root cause,
 * which is not a smaller list being generous but a wrong one being reused.
 *
 * <p>The keys say what a remark <em>is</em> rather than what it looks like — {@code cannot-reproduce}
 * rather than {@code magnifier} — so changing the drawing later is a change to the client map and to
 * nothing else.
 */
public final class CommentTopicIcons {

    /** The six the seed uses first, then the rest, which is the order the picker offers them in. */
    public static final List<String> ALL = List.of(
            // The seeded six.
            "cannot-reproduce", "code-review", "root-cause", "workaround", "decision", "test-evidence",
            // Kinds of remark a team distinguishes beyond those.
            "question", "blocker", "note", "reference", "agreement", "objection");

    /**
     * Whether this is something the field may hold — which includes <em>nothing</em>.
     *
     * <p>A blank icon key is not a known icon, it is the absence of one, and a topic without an icon
     * renders the generic mark on purpose.
     */
    public static boolean isAcceptable(String iconKey) {
        return iconKey == null || iconKey.isBlank() || ALL.contains(iconKey);
    }

    private CommentTopicIcons() {
    }

}
