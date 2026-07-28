package net.innoventa.tessera.service.key;

/**
 * The per-project algorithm turning a {@code (projectKey, sequence)} pair into the human issue key
 * (ADR-0003, Strategy pattern). A project names its active strategy via {@code Project.keyStrategy}; a
 * new format (date-based, custom prefix) is a new bean with no schema change (Open/Closed). The
 * optional {@code pattern} is {@code Project.keyPattern}, interpreted by the strategy.
 */
public interface IssueKeyStrategy {

    /** The discriminator stored in {@code Project.keyStrategy} selecting this strategy. */
    String name();

    /** Format the key for an allocated sequence, e.g. {@code (TIC, 1) → "TIC-1"}. */
    String format(String projectKey, int sequence, String pattern);

}
