package net.innoventa.tessera.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.BoardScopeStrategy;

/**
 * Create a project. The key must be uppercase (validated here) and unique instance-wide (validated in
 * the service, since it needs the database). {@code leadMemberId} is optional — the creator leads by
 * default. Key strategy/pattern are not chosen at create time in Phase 1; the default prefixed-sequence
 * strategy is applied.
 * <p>
 * {@code boardScopeStrategy} carries the "Scrum or Kanban?" answer, and it is stored as exactly that —
 * the new board's scope strategy — rather than as a separate project type that could later disagree
 * with it (ADR-0015). The words Scrum and Kanban never reach the backend; the interface derives them
 * back from this one field.
 */
public record CreateProjectRequest(
    @NotBlank @Size(max = 128) String name,
    @NotBlank @Size(max = 32) @Pattern(
        regexp = "^[A-Z][A-Z0-9]*$",
        message = "must be uppercase letters and digits, starting with a letter") String key,
    @NotNull BoardScopeStrategy boardScopeStrategy,
    String leadMemberId,
    /**
     * One emoji, or nothing (TSSR-7). ⚠️ {@code @Size} is the column's bound, not the rule — "exactly one
     * emoji" is a grapheme-cluster count, which no annotation can express; {@code ProjectIcon} refuses the
     * rest with a sentence.
     */
    @Size(max = 16) String icon
) {
}
