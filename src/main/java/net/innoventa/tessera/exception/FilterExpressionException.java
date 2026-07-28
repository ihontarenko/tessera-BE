package net.innoventa.tessera.exception;

/**
 * A filter expression the engine could not use — it failed to parse, reached something that is not
 * part of the filter surface, or produced something other than a boolean. A {@code 400} through
 * {@link GlobalExceptionHandler}'s {@link InvalidRequestException} mapping: the request is malformed,
 * nothing on the server is broken. The message is written for the person who typed the expression, so
 * it must never carry a stack trace or an engine-internal class name.
 */
public class FilterExpressionException extends InvalidRequestException {

    public FilterExpressionException(String message) {
        super(message);
    }

}
