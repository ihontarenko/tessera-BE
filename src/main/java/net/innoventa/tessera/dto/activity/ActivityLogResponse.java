package net.innoventa.tessera.dto.activity;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One history event (ticket 08): who changed what, when. A single multi-field edit is one event with
 * several {@link ActivityLogItemResponse} items. Rendered newest-first.
 *
 * <p>{@code agentName} is the agent that made the change, where one did, and null where the person did it
 * themselves. ⚠️ Beside the actor, never instead of one — the change is still theirs.
 */
public record ActivityLogResponse(
    String id,
    MemberSummary actor,
    String agentName,
    LocalDateTime createdAt,
    List<ActivityLogItemResponse> items
) {
}
