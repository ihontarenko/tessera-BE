package net.innoventa.tessera.dto.configuration;

import java.util.List;

/**
 * Which boards would hold work no column shows, after a workflow change.
 *
 * <p>A board maps a status explicitly or catches it by category fallback (ADR-0010); a status no column
 * reaches is not an error — the backlog holds it instead, and unmapping one is how an administrator
 * decides what the backlog contains (ADR-0016). What makes it worth reporting is that adding a status to
 * a workflow can produce it <em>by accident</em>: work moves into a status the board was never told
 * about, and it lands in a backlog nobody is looking at.
 *
 * <p>⚠️ <strong>Reported, and nothing is re-provisioned.</strong> Board columns have always been the
 * project administrator's, and rewriting somebody's board because a shared workflow changed would be a
 * global edit reaching into a local decision. The report links to each board's settings instead.
 */
public record WorkflowBoardImpact(List<UnmappedBoard> boards) {

    public static WorkflowBoardImpact none() {
        return new WorkflowBoardImpact(List.of());
    }

    /** One project's board, and the statuses of this workflow it currently shows nowhere. */
    public record UnmappedBoard(
        String projectId,
        String projectKey,
        String projectName,
        String boardId,
        List<StatusRef> unmappedStatuses
    ) {
    }

    public record StatusRef(String id, String name) {
    }
}
