package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.domain.Component;

/** A component as referenced from an issue — id and name only. */
public record ComponentRef(String id, String name) {

    public static ComponentRef from(Component component) {
        return new ComponentRef(component.getId(), component.getName());
    }

}
