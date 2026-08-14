package net.innoventa.tessera.dto.configuration;

/**
 * What removing an issue type from a scheme would mean, before it is removed.
 *
 * <p>⚠️ <strong>Reported, never a refusal.</strong> The issues counted here keep their type and stay
 * readable, editable and browsable; what stops is raising new ones on this scheme. A number is offered
 * because narrowing a scheme somebody has been using for a year deserves one — but refusing it would
 * make a scheme unnarrowable the moment anybody used it.
 *
 * @param issues   issues of the type in the projects on this scheme — ⚠️ scoped to those projects, since
 *                 the same type elsewhere is not affected by this edit at all
 * @param projects how many projects are on the scheme, so the count above has a denominator
 */
public record SchemeMemberImpact(String issueTypeId, String issueTypeName, long issues, int projects) {
}
