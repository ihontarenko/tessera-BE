package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.domain.IssueType;

/** Compact issue-type projection for issue payloads — carries {@code hierarchyLevel} so the UI can render nesting. */
public record IssueTypeSummary(String id, String name, int hierarchyLevel, String iconKey) {

    public static IssueTypeSummary from(IssueType issueType) {
        return issueType == null
            ? null
            : new IssueTypeSummary(issueType.getId(), issueType.getName(), issueType.getHierarchyLevel(), issueType.getIconKey());
    }

}
