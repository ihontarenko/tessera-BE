package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.ProjectRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRolePermissionRepository extends JpaRepository<ProjectRolePermission, String> {

    List<ProjectRolePermission> findByRoleId(String roleId);

    List<ProjectRolePermission> findByRoleIdIn(List<String> roleIds);

}
