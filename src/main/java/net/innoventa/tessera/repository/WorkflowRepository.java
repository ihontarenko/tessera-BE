package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRepository extends JpaRepository<Workflow, String> {

    List<Workflow> findAllByOrderByNameAsc();

    /** Case-insensitively — see {@link PriorityRepository#existsByNameIgnoreCase} for why. */
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);

}
