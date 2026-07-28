package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.ProjectMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, String> {

    List<ProjectMembership> findByProjectIdAndMemberId(String projectId, String memberId);

    List<ProjectMembership> findByProjectId(String projectId);

    List<ProjectMembership> findByMemberId(String memberId);

    boolean existsByProjectIdAndMemberId(String projectId, String memberId);

    boolean existsByProjectIdAndMemberIdAndRoleId(String projectId, String memberId, String roleId);

    void deleteByProjectIdAndMemberId(String projectId, String memberId);

}
