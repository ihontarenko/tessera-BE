package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriorityRepository extends JpaRepository<Priority, String> {

    List<Priority> findAllByOrderBySequenceAsc();

}
