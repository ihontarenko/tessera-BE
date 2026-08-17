package net.innoventa.tessera.dto.shipped;

import net.innoventa.tessera.dto.issue.IssueRowResponse;

import java.util.List;

/**
 * One slice of finished work — a sprint, or a month — with its issues and its totals.
 *
 * <p>{@code key} is what the slice is ordered and de-duplicated by (a sprint id, or {@code 2026-08});
 * {@code title} is what a reader sees. They are separate because a sprint can be renamed and two months
 * can share a name across years, and a screen that ordered by the label would be wrong in both cases.
 */
public record ShippedGroupView(
    String key,
    String title,
    List<IssueRowResponse> issues,
    int issueCount,
    Double storyPoints
) {

    public static ShippedGroupView of(String key, String title, List<IssueRowResponse> issues) {
        double points = issues.stream()
            .map(IssueRowResponse::storyPoints)
            .filter(storyPoints -> storyPoints != null)
            .mapToDouble(Double::doubleValue)
            .sum();

        return new ShippedGroupView(key, title, issues, issues.size(), points);
    }

}
