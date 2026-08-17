package net.innoventa.tessera.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.Issue;

/**
 * Create an issue (ticket 07). Type and priority are required (an issue always has both); the initial
 * status comes from the workflow, the reporter from the caller — neither is accepted from input. An
 * optional {@code parentId} forms hierarchy (validated strictly-higher, ticket 10) and an optional
 * assignee/story points may be set at creation.
 */
public record CreateIssueRequest(
    @NotBlank @Size(max = 255) String summary,
    @Size(max = Issue.MAXIMUM_DESCRIPTION_LENGTH) String description,
    @NotBlank String issueTypeId,
    @NotBlank String priorityId,
    String assigneeMemberId,
    String parentId,
    @PositiveOrZero Double storyPoints
) {
}
