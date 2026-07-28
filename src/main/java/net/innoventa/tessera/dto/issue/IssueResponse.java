package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The full issue as the detail modal sees it (tickets 07–13): core fields plus hierarchy (parent +
 * children), organization (labels, components, affects/fix versions), links, and the legal transitions
 * available from the current status. {@code open} surfaces the {@code resolution IS NULL} invariant
 * (ADR-0004). Comments and history are loaded by their own endpoints, not embedded here.
 */
public record IssueResponse(
    String id,
    String projectId,
    String issueKey,
    int sequence,
    String summary,
    String description,
    IssueTypeSummary type,
    PrioritySummary priority,
    StatusSummary status,
    ResolutionSummary resolution,
    boolean open,
    MemberSummary reporter,
    MemberSummary assignee,
    IssueRef parent,
    List<IssueRef> children,
    Double storyPoints,
    String rank,
    List<String> labels,
    List<ComponentRef> components,
    List<VersionRef> affectsVersions,
    List<VersionRef> fixVersions,
    List<IssueLinkView> links,
    List<TransitionOption> availableTransitions,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
