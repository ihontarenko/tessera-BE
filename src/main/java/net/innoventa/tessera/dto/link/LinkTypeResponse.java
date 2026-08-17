package net.innoventa.tessera.dto.link;

import net.innoventa.tessera.domain.LinkType;
import net.innoventa.tessera.domain.LinkTypeEffect;

/** A link type with its outward/inward labels — the picker source for creating links (ticket 12). */
public record LinkTypeResponse(
    String id,
    String name,
    String outwardLabel,
    String inwardLabel,
    /** What a link of this type does, or {@code NONE} for one that is only for a reader (TSSR-40). */
    LinkTypeEffect effect
) {

    public static LinkTypeResponse from(LinkType linkType) {
        return new LinkTypeResponse(
            linkType.getId(),
            linkType.getName(),
            linkType.getOutwardLabel(),
            linkType.getInwardLabel(),
            linkType.getEffect());
    }

}
