package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.EstimationSchemeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstimationSchemeItemRepository extends JpaRepository<EstimationSchemeItem, String> {

    List<EstimationSchemeItem> findBySchemeIdOrderBySequenceAsc(String schemeId);

    List<EstimationSchemeItem> findBySchemeIdInOrderBySequenceAsc(List<String> schemeIds);

}
