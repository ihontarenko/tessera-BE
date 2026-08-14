package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Resolution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResolutionRepository extends JpaRepository<Resolution, String> {

    List<Resolution> findAllByOrderByNameAsc();

    /** Case-insensitively — see {@link PriorityRepository#existsByNameIgnoreCase} for why. */
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);

}
