package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.domain.Priority;

/** Compact priority projection for issue payloads. */
public record PrioritySummary(String id, String name, int sequence, String color) {

    public static PrioritySummary from(Priority priority) {
        return priority == null
            ? null
            : new PrioritySummary(priority.getId(), priority.getName(), priority.getSequence(), priority.getColor());
    }

}
