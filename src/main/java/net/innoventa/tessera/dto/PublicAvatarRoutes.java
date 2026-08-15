package net.innoventa.tessera.dto;

/**
 * Where an uploaded avatar's bytes are served from.
 *
 * <p>Three places need to agree on this address and disagreeing is silent: the payload that hands it
 * to a browser ({@code MemberAvatarView}), the route that answers it ({@code PublicAvatarController}),
 * and the one filter-chain entry that lets an unauthenticated request reach it
 * ({@code SecurityConfiguration}). A literal in each is three copies of one decision, and the failure
 * mode of them drifting is every avatar in the product turning into a broken image.
 *
 * <p>It lives beside the DTO rather than beside the controller because the address is part of the API
 * contract: the payload promises a URL, and the controller is what makes the promise true.
 *
 * <p>⚠️ <strong>Relative, never absolute.</strong> Tessera's interface is a separate application on its
 * own origin whose dev server proxies {@code /api} here, and in a deployment a reverse proxy does the
 * same. A URL built from this server's own idea of its host is reachable from the server and from
 * nowhere else.
 */
public final class PublicAvatarRoutes {

    /** The Spring path pattern, for the route and for the filter chain. */
    public static final String PATTERN = "/api/public/avatars/**";

    private static final String PREFIX = "/api/public/avatars/";

    private PublicAvatarRoutes() {
    }

    /** Where the bytes of the stored object {@code storedFileId} are served. */
    public static String of(String storedFileId) {
        return PREFIX + storedFileId;
    }

}
