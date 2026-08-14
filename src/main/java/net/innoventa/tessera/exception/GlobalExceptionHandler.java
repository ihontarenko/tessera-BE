package net.innoventa.tessera.exception;

import net.innoventa.tessera.security.access.AccessReason;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps Tessera's domain exceptions to RFC 7807 {@link ProblemDetail} responses — the same error
 * contract Innoventa, Moneta and Central expose, so every product's frontend consumes one shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * ⚠️ The evidence travels beside the prose where the rule has any.
     *
     * <p>"Cannot delete In Review — 12 issues in 3 projects, 4 board columns" is a fact with parts, and a
     * client that wants to link to those projects should not have to parse the sentence to find them.
     * The {@code details} property is the same refusal in structure; a client may render either, and
     * they cannot disagree because the message is built from the object.
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());

        if (exception.getDetails() != null) {
            problem.setProperty("details", exception.getDetails());
        }

        return problem;
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail handleForbidden(ForbiddenException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    /**
     * Every refusal the access engine produces, with the status the axis that answered decided.
     *
     * <p>The {@code reason} and {@code axis} properties travel beside the prose so that an interface can
     * tell <em>ask to be added to this project</em> from <em>ask for this permission</em> without
     * parsing a sentence somebody may reword.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        AccessReason reason = exception.getReason();

        ProblemDetail problem = ProblemDetail.forStatus(reason.status());
        problem.setTitle(reason.title());
        problem.setDetail(exception.getMessage());
        problem.setProperty("reason", reason.wireName());
        problem.setProperty("axis", exception.getAxis().name().toLowerCase());

        return problem;
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .reduce((first, second) -> first + "; " + second)
            .orElse("Validation failed");

        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

}
