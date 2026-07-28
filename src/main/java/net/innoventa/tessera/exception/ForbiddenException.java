package net.innoventa.tessera.exception;

/**
 * Raised when an authenticated member lacks the permission required for a project-scoped action —
 * mapped to {@code 403 Forbidden} by {@link GlobalExceptionHandler}. Distinct from a missing token
 * (Spring returns {@code 401} before any controller runs): the caller is known, they simply may not
 * do this. The local authorization model (project roles, permission overrides, deny-wins resolution)
 * is what decides that — see {@code net.innoventa.tessera.service.ProjectPermissionService}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

}
