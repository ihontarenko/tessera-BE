package net.innoventa.tessera.dto.link;

import net.innoventa.tessera.domain.LinkType;

/** A seeded link type with its outward/inward labels — the picker source for creating links (ticket 12). */
public record LinkTypeResponse(
    String id,
    String name,
    String outwardLabel,
    String inwardLabel
) {

    public static LinkTypeResponse from(LinkType linkType) {
        return new LinkTypeResponse(linkType.getId(), linkType.getName(), linkType.getOutwardLabel(), linkType.getInwardLabel());
    }

}
