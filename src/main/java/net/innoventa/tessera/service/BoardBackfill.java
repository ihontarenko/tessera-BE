package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.repository.BoardRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Backfills a {@link net.innoventa.tessera.domain.Board} for every project that predates the board
 * feature (ADR-0009). Boards are provisioned in application code rather than the {@code V000007}
 * migration because a per-board/-column id needs a UUID, and there is no portable cross-dialect UUID
 * generator (H2 {@code RANDOM_UUID()} / PostgreSQL {@code gen_random_uuid()} / MySQL {@code UUID()}
 * all differ) that would keep the {@code h2==postgresql} migration rule intact — see the migration
 * header. New projects seed their board on the creation path ({@link ProjectService}); this catches
 * the ones created before the feature landed.
 * <p>
 * Runs once at startup and is idempotent (each call goes through {@link BoardProvisioner}, which skips
 * a project that already has a board), so a second boot backfills nothing.
 */
@Component
@RequiredArgsConstructor
public class BoardBackfill implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(BoardBackfill.class);

    private final ProjectRepository projectRepository;
    private final BoardRepository boardRepository;
    private final BoardProvisioner boardProvisioner;

    @Override
    public void run(ApplicationArguments arguments) {
        List<Project> boardless = projectRepository.findAll().stream()
            .filter(project -> !boardRepository.existsByProjectId(project.getId()))
            .toList();

        if (boardless.isEmpty()) {
            return;
        }

        boardless.forEach(boardProvisioner::provision);
        logger.info("Provisioned boards for {} pre-existing project(s)", boardless.size());
    }

}
