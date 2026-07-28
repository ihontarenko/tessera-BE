package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabelRepository extends JpaRepository<Label, String> {

    Optional<Label> findByName(String name);

    List<Label> findByNameInOrderByNameAsc(List<String> names);

}
