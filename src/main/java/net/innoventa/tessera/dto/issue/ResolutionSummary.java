package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.domain.Resolution;

/** Compact resolution projection for issue payloads; null on an open issue (ADR-0004). */
public record ResolutionSummary(String id, String name) {

    public static ResolutionSummary from(Resolution resolution) {
        return resolution == null
            ? null
            : new ResolutionSummary(resolution.getId(), resolution.getName());
    }

}
