package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, String> {

    List<Permission> findAllByOrderByNameAsc();

    Optional<Permission> findByName(String name);

}
