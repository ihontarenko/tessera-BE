package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.ProjectPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectPermissionOverrideRepository extends JpaRepository<ProjectPermissionOverride, String> {

    List<ProjectPermissionOverride> findByProjectIdAndMemberId(String projectId, String memberId);

    List<ProjectPermissionOverride> findByProjectId(String projectId);

    void deleteByProjectIdAndMemberId(String projectId, String memberId);

    void deleteByProjectIdAndMemberIdAndPermissionId(String projectId, String memberId, String permissionId);

}
