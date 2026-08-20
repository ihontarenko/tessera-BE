package net.innoventa.tessera.dto.activity;

import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One history event (ticket 08): who changed what, when. A single multi-field edit is one event with
 * several {@link ActivityLogItemResponse} items. Rendered newest-first.
 *
 */
public record ActivityLogResponse(
    String id,
    MemberSummary actor,
    // ⚠️ `agentName` was here and is gone (TSSR-34). The author IS the agent now, so `MemberSummary`
    // carries the name, the face and `kind` — one reference instead of a chip plus a string beside it.
    LocalDateTime createdAt,
    List<ActivityLogItemResponse> items
) {
}
