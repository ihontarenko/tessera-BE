package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, String> {

    Optional<Board> findByProjectId(String projectId);

    boolean existsByProjectId(String projectId);

}
