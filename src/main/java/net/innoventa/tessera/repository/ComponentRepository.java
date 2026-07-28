package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Component;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComponentRepository extends JpaRepository<Component, String> {

    List<Component> findByProjectIdOrderByNameAsc(String projectId);

    List<Component> findByIdInAndProjectId(List<String> ids, String projectId);

    boolean existsByProjectIdAndName(String projectId, String name);

    boolean existsByProjectIdAndNameAndIdNot(String projectId, String name, String id);

}
