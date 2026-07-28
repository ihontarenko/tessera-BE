package net.innoventa.tessera.dto.component;

import net.innoventa.tessera.dto.MemberSummary;

/** A component with its (optional) resolved lead member (ticket 06). */
public record ComponentResponse(
    String id,
    String projectId,
    String name,
    MemberSummary lead,
    String description
) {
}
