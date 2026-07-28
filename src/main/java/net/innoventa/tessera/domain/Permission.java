package net.innoventa.tessera.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A first-class, catalogued project capability — {@code BROWSE_PROJECT}, {@code CREATE_ISSUE},
 * {@code ADMINISTER_PROJECT}, … An entity/catalog so the set can grow, not a hard-coded enum
 * (CONTEXT.md, "Permission"). The {@code name} is the stable code checked in the authorization
 * resolver; {@code net.innoventa.tessera.security.Permissions} holds the constants for it.
 */
@Entity
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(name = "uq_permissions_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Permission {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

}
