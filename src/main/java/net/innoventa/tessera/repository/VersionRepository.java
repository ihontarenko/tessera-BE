package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Version;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersionRepository extends JpaRepository<Version, String> {

    List<Version> findByProjectIdOrderBySequenceAscNameAsc(String projectId);

    List<Version> findByIdInAndProjectId(List<String> ids, String projectId);

    boolean existsByProjectIdAndName(String projectId, String name);

    boolean existsByProjectIdAndNameAndIdNot(String projectId, String name, String id);

}
