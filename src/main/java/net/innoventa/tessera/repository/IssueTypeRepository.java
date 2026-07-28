package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueTypeRepository extends JpaRepository<IssueType, String> {

    List<IssueType> findAllByOrderByHierarchyLevelDescNameAsc();

}
