package net.innoventa.tessera.dto.configuration;

/**
 * A filter whose expression mentions a catalog name as a literal — the reason renaming that row is
 * offered with a warning rather than silently done.
 *
 * <p>A filter compares on names, not on identifiers: {@code issue.type.name == 'Bug'} is what the
 * shipped "Only bugs" toggle actually says. Renaming Bug therefore does not break the filter, which
 * would at least be visible; it leaves a filter that parses, runs, and matches nothing.
 *
 * @param source     {@code builtIn} for one the product ships, {@code saved} for one a member kept —
 *                   the two need different advice, since only the second can be edited
 * @param id         the filter's identifier, so a client can link to it
 * @param name       what it is called
 * @param projectId  the project a saved filter belongs to, or null for a built-in and for a global preset
 * @param expression the jME predicate itself, so the mention can be shown in context rather than claimed
 */
public record FilterMention(
    String source,
    String id,
    String name,
    String projectId,
    String expression
) {

    public static final String BUILT_IN = "builtIn";
    public static final String SAVED    = "saved";

}
