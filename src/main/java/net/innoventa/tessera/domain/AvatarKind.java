package net.innoventa.tessera.domain;

/**
 * Where a member's face comes from.
 *
 * <p>Three kinds rather than a nullable picture, because "no picture" and "a generated picture" are
 * genuinely different answers and a single nullable column cannot hold both. Somebody who has never
 * opened the account screen is {@link #INITIALS}; somebody who chose a generated face is
 * {@link #PRESET} and stays that way even though nothing was uploaded.
 *
 * <p>The kind names which of {@code Member}'s two avatar columns carries the answer, and the database
 * enforces that pairing rather than trusting this code to — see {@code members_check_avatar_shape} in
 * V000018.
 */
public enum AvatarKind {

    /**
     * No picture: the interface draws the member's initials. The default every member is provisioned
     * with, and what clearing a picture returns to.
     */
    INITIALS,

    /**
     * A generated pixel face, identified by the seed it was generated from.
     *
     * <p>⚠️ The seed is <strong>not</strong> a key into a catalog of pictures. The generator
     * ({@code UI/src/lib/pixelFace.ts}) is total — every string produces a face — so there is nothing
     * on this side to validate a seed against beyond its shape, and no list to keep in step with the
     * interface. What is curated is which seeds the picker offers.
     */
    PRESET,

    /** A picture the member uploaded, held by jmouse-storage and pointed at by {@code avatar_file_id}. */
    UPLOAD

}
