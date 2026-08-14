package net.innoventa.tessera.security.access;

/**
 * Tessera's three scopes, by name — for the one place a constant is needed and an enum cannot go.
 *
 * <p>{@code @RequiresAccess} lives in {@code jmouse-access-enforcement}, and an annotation attribute has
 * to be a constant of a type the annotation declares. A library cannot declare this product's scope
 * enum, so the attribute is a {@code String} and these are the strings.
 *
 * <p>What is lost is the compiler checking the value; what replaces it is {@code AccessRequirements}
 * resolving every name against the {@link org.jmouse.access.ScopeCatalog} as it reads the declaration.
 * A typo fails the boot rather than silently widening a route — later than the compiler, but before
 * anybody can call it.
 *
 * <p>Each constant is the {@link TesseraScope} of the same name, and they cannot drift: the catalogue is
 * built from {@code TesseraScope.values()}, so a name here that is not a constant there stops the
 * application.
 */
public final class Scopes {

    /** Everything. What a route aimed at no project in particular declares. */
    public static final String GLOBAL = "GLOBAL";

    /** One project — the only place Tessera holds a grant at. */
    public static final String PROJECT = "PROJECT";

    /** The rows the caller owns — and what a route that <em>creates</em> something declares. */
    public static final String SELF = "SELF";

    private Scopes() {
    }
}
