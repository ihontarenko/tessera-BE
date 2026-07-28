package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.ProjectType;
import net.innoventa.tessera.domain.ProjectTypeDefaultScheme;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTypeDefaultSchemeRepository extends JpaRepository<ProjectTypeDefaultScheme, ProjectType> {

}
