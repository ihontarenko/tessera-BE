package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusRepository extends JpaRepository<Status, String> {

    List<Status> findAllByOrderByNameAsc();

    /** Case-insensitively — see {@link PriorityRepository#existsByNameIgnoreCase} for why. */
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);

}
