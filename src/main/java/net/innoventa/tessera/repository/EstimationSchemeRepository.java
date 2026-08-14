package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.EstimationScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstimationSchemeRepository extends JpaRepository<EstimationScheme, String> {

    List<EstimationScheme> findAllByOrderByNameAsc();

    /** Name collisions are a 409 with words rather than the unique constraint's 500 — see SchemeRules. */
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String schemeId);

}
