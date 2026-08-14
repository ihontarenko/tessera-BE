package net.innoventa.tessera.dto.configuration;

import java.util.List;

/**
 * What moving an issue type to a different hierarchy level would do, answered before it is done.
 *
 * <p>The level decides what may be a parent of what — a parent must sit strictly higher — and what a
 * sprint may plan, since only level 0 is planned (ADR-0014). Moving a type therefore invalidates
 * hierarchies that are perfectly legal today, and may orphan sprint commitments.
 *
 * <p>⚠️ <strong>Reported and not repaired.</strong> Nothing is rewritten: existing parent/child pairs
 * are left exactly as they are, the same way a category change leaves issues alone. Fixing them is
 * issue-by-issue work through the parent control, by somebody who knows which of the two ends was
 * wrong.
 *
 * @param violatingPairs   the pairings that would stop satisfying "a parent sits strictly higher",
 *                         with how many issues are in each
 * @param issuesInSprints  how many issues of this type are committed to a sprint right now — reported
 *                         only when the type is leaving level 0, which is the level a sprint plans
 */
public record IssueTypeLevelImpact(
    String issueTypeId,
    String issueTypeName,
    int currentLevel,
    int proposedLevel,
    List<HierarchyPair> violatingPairs,
    long issuesInSprints
) {

    /**
     * One parent-type / child-type pairing that exists in the data and would become illegal.
     *
     * @param count how many child issues are in it — the size of the problem, not the shape of it
     */
    public record HierarchyPair(
        String parentIssueTypeId,
        String parentIssueTypeName,
        int parentLevel,
        String childIssueTypeId,
        String childIssueTypeName,
        int childLevel,
        long count
    ) {
    }
}
