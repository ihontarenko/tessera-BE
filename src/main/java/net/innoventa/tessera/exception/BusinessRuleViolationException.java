package net.innoventa.tessera.exception;

/**
 * A rule the product enforces, refused as {@code 409}.
 *
 * <p>⚠️ <strong>It may carry the evidence, not only the sentence.</strong> A refusal like "cannot delete
 * In Review — 12 issues in 3 projects, 4 board columns, 3 transitions in 2 workflows" is a fact with
 * parts, and an interface that wants to link to those projects has to parse English to find them unless
 * the parts travel too. {@link GlobalExceptionHandler} puts {@code details} on the problem as a property
 * beside the prose, so a client may use either and neither is a second source of truth.
 */
public class BusinessRuleViolationException extends RuntimeException {

    /**
     * The structured form of the same refusal, or null where there is nothing to structure.
     *
     * <p>Typed as {@code Object} because it is whatever the rule has to show — a usage report here, a
     * list of offending edges there — and a common supertype for "evidence" would be an interface with
     * no method on it.
     */
    private final transient Object details;

    public BusinessRuleViolationException(String message) {
        this(message, null);
    }

    public BusinessRuleViolationException(String message, Object details) {
        super(message);
        this.details = details;
    }

    public Object getDetails() {
        return details;
    }

}
