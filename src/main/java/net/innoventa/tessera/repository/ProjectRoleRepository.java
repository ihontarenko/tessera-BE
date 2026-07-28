package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRoleRepository extends JpaRepository<ProjectRole, String> {

    List<ProjectRole> findAllByOrderByNameAsc();

    Optional<ProjectRole> findByName(String name);

}
