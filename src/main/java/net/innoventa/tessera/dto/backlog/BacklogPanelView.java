package net.innoventa.tessera.dto.backlog;

import net.innoventa.tessera.dto.sprint.SprintSummary;

import java.util.List;

/**
 * One section of the backlog screen — a sprint's panel, or the product backlog itself ({@code sprint}
 * null). Its {@code issueCount} and {@code storyPointTotal} are computed server-side so a client never
 * has to agree with the server about how a panel is sized.
 * <p>
 * An unestimated issue counts as <strong>zero points but is still counted</strong> in
 * {@code issueCount} — a missing estimate should be obvious, not invisible (spec, story 42).
 */
public record BacklogPanelView(
    SprintSummary sprint,
    List<BacklogIssueView> issues,
    int issueCount,
    double storyPointTotal
) {

    public static BacklogPanelView of(SprintSummary sprint, List<BacklogIssueView> issues) {
        double storyPointTotal = issues.stream()
            .map(BacklogIssueView::storyPoints)
            .filter(storyPoints -> storyPoints != null)
            .mapToDouble(Double::doubleValue)
            .sum();

        return new BacklogPanelView(sprint, issues, issues.size(), storyPointTotal);
    }

}
