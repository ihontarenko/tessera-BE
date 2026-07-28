package net.innoventa.tessera.dto.issue;

import net.innoventa.tessera.domain.Version;
import net.innoventa.tessera.domain.VersionState;

/** A version as referenced from an issue — id, name and lifecycle state. */
public record VersionRef(String id, String name, VersionState state) {

    public static VersionRef from(Version version) {
        return new VersionRef(version.getId(), version.getName(), version.getState());
    }

}
