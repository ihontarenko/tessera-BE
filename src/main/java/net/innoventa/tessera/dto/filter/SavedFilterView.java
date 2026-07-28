package net.innoventa.tessera.dto.filter;

import net.innoventa.tessera.domain.SavedFilter;
import net.innoventa.tessera.domain.SavedFilterVisibility;
import net.innoventa.tessera.dto.MemberSummary;

import java.time.LocalDateTime;

/**
 * A saved board filter as the client sees it. {@code expression} travels in full: the same string the
 * board endpoint takes as {@code ?filter=}, so applying a saved filter is the client sending back
 * what it was given, with nothing derived in between.
 * <p>
 * {@code editable} answers the one question the UI would otherwise have to reconstruct from the owner
 * id and the caller's identity — whoever owns it may change it, everyone else may only use it.
 */
public record SavedFilterView(
    String id,
    String projectId,
    String name,
    String description,
    String expression,
    SavedFilterVisibility visibility,
    MemberSummary owner,
    boolean editable,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static SavedFilterView from(SavedFilter savedFilter, MemberSummary owner, boolean editable) {
        return new SavedFilterView(
            savedFilter.getId(),
            savedFilter.getProjectId(),
            savedFilter.getName(),
            savedFilter.getDescription(),
            savedFilter.getExpression(),
            savedFilter.getVisibility(),
            owner,
            editable,
            savedFilter.getCreatedAt(),
            savedFilter.getUpdatedAt()
        );
    }

}
