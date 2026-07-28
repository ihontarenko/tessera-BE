package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.WorkflowScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowSchemeRepository extends JpaRepository<WorkflowScheme, String> {

    List<WorkflowScheme> findAllByOrderByNameAsc();

}
