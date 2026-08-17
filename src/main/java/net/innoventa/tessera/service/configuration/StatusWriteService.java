package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.dto.configuration.StatusCategoryImpact;
import net.innoventa.tessera.dto.configuration.StatusCategoryImpact.BoardMove;
import net.innoventa.tessera.dto.configuration.StatusRequest;
import net.innoventa.tessera.dto.configuration.StatusResponse;
import net.innoventa.tessera.repository.BoardRepository;
import net.innoventa.tessera.repository.CountByKey;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.service.BoardColumnResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Writing the status catalog, and saying first what a change to it would do.
 *
 * <p>Three of the four moves are ordinary — create, rename, delete — and inherit
 * {@link FlatCatalogWriteService}'s shape exactly. The fourth is not: a status's
 * {@link StatusCategory} is the field the rest of the product reads. It decides whether an issue here
 * counts as closed (ADR-0004) and which board column holds it when nothing maps it explicitly
 * (ADR-0010), so changing it moves cards and changes what work already sitting there is taken to mean.
 *
 * <p>⚠️ <strong>Allowed, and reported with counts first.</strong> A team that decides "In Review" is
 * really in progress is describing their process better, and refusing them would be the product
 * insisting it knows their workflow. What it does instead is answer, before the write, exactly how many
 * issues and how many cards it is about to affect — through the same {@link BoardColumnResolver} the
 * board itself renders with, run against the proposed category rather than the stored one, so the
 * prediction and the outcome cannot differ.
 *
 * <p>⚠️ <strong>Nothing here writes to {@code issues}.</strong> Back-filling resolutions when a status
 * becomes Done would be inventing an answer on somebody's behalf; the report says how many issues are
 * about to be open-in-the-Done-column, and leaves the decision where it belongs.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StatusWriteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatusWriteService.class);

    private final StatusRepository    statusRepository;
    private final IssueRepository     issueRepository;
    private final ProjectRepository   projectRepository;
    private final BoardRepository     boardRepository;
    private final BoardMapping        boardMapping;
    private final BoardColumnResolver boardColumnResolver;
    private final ConfigurationUsage  configurationUsage;
    private final Statuses            statuses;
    private final Supplier<String>    idGenerator;

    /**
     * ⚠️ A new status belongs to <strong>no workflow, and therefore to no project</strong>.
     *
     * <p>Membership is not stored — a status is in a workflow exactly when some transition names it
     * (there is no {@code workflow_statuses} table) — so creating one puts it in the catalog and nowhere
     * else. The Administration screen says so rather than leaving an administrator to wonder where their
     * new status went; this log line is the server's half of the same sentence.
     */
    public StatusResponse create(StatusRequest request) {
        String name = CatalogRules.requireName(request.name(), "status");
        CatalogRules.requireNameAvailable(statusRepository.existsByNameIgnoreCase(name), "status", name);

        Status status = statusRepository.save(Status.builder()
            .id(idGenerator.get())
            .name(name)
            .category(request.category())
            .color(CatalogRules.storedOptional(request.color()))
            .build());

        LOGGER.info("Status '{}' created in category {} — it belongs to no workflow until a transition "
                    + "names it", name, request.category());

        return toResponse(status);
    }

    /**
     * Renaming and re-categorising are one call, because they are one form.
     *
     * <p>Splitting them would mean an administrator who changed both saw the second half fail after the
     * first had already landed — and a screen that half-applies is worse than one that asks twice.
     */
    public StatusResponse update(String statusId, StatusRequest request) {
        Status status = requireStatus(statusId);
        String name = CatalogRules.requireName(request.name(), "status");
        CatalogRules.requireNameAvailable(
            statusRepository.existsByNameIgnoreCaseAndIdNot(name, statusId), "status", name);

        if (status.getCategory() != request.category()) {
            LOGGER.info("Status '{}' moves from category {} to {} — {} issue(s) hold it",
                status.getName(), status.getCategory(), request.category(),
                issueRepository.countByStatusId(statusId));
        }

        if (!status.getName().equals(name)) {
            LOGGER.info("Status '{}' renamed to '{}'", status.getName(), name);
        }

        status.setName(name);
        status.setCategory(request.category());
        status.setColor(CatalogRules.storedOptional(request.color()));

        return toResponse(status);
    }

    public void delete(String statusId) {
        Status status = requireStatus(statusId);

        CatalogRules.requireCatalogSurvives(statusRepository.count() - 1, "status",
            "every issue is in one, and a workflow with nowhere to send an issue cannot be built.");
        CatalogRules.requireNothingHoldsIt(
            configurationUsage.ofStatus(statusId), "status", status.getName());

        statusRepository.delete(status);

        LOGGER.info("Status '{}' deleted", status.getName());
    }

    /**
     * What moving this status into {@code proposedCategory} would do — the read the screen makes before
     * it offers Save.
     *
     * <p>Answered per project rather than in total, because a board belongs to a project and each one
     * may map the status differently: an explicit {@link BoardColumnStatus} outranks the category
     * fallback, so a board that names this status moves nothing at all while its neighbour moves every
     * card.
     */
    @Transactional(readOnly = true)
    public StatusCategoryImpact categoryImpact(String statusId, StatusCategory proposedCategory) {
        Status status = requireStatus(statusId);

        List<BoardMove> moves = movesPerProject(status, proposedCategory);
        long cardsMoving = moves.stream().mapToLong(BoardMove::issueCount).sum();

        return new StatusCategoryImpact(
            status.getId(),
            status.getName(),
            status.getCategory(),
            proposedCategory,
            issueRepository.countByStatusId(statusId),
            cardsMoving,
            moves,
            proposedCategory == StatusCategory.DONE
                ? issueRepository.countByStatusIdAndResolutionIdIsNull(statusId)
                : 0L);
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * One entry per project whose board would render this status somewhere else.
     *
     * <p>Only projects that actually hold issues in the status are considered: a board is a rendering of
     * issues, so a board with none of them cannot show a card moving.
     */
    private List<BoardMove> movesPerProject(Status status, StatusCategory proposedCategory) {
        List<CountByKey> issuesByProject = issueRepository.countIssuesInStatusByProject(status.getId());

        if (issuesByProject.isEmpty()) {
            return List.of();
        }

        Map<String, Project> projects = projectRepository
            .findByIdInOrderByKeyAsc(issuesByProject.stream().map(CountByKey::key).toList()).stream()
            .collect(Collectors.toMap(Project::getId, project -> project));

        List<BoardMove> moves = new ArrayList<>();

        for (CountByKey held : issuesByProject) {
            Project project = projects.get(held.key());
            Optional<Board> board = project == null ? Optional.empty() : boardRepository.findByProjectId(project.getId());

            if (board.isEmpty()) {
                continue;
            }

            moveOnBoard(status, proposedCategory, project, board.get(), held.count()).ifPresent(moves::add);
        }

        return List.copyOf(moves);
    }

    /**
     * The same resolution the board renders with, asked twice: once about the stored category and once
     * about the proposed one. A status that lands in the same column either way is not a move.
     */
    private Optional<BoardMove> moveOnBoard(
        Status status, StatusCategory proposedCategory, Project project, Board board, long issueCount) {

        BoardMapping.Mapping mapping = boardMapping.of(board.getId());

        String currentColumnId = boardColumnResolver.resolveColumnId(
            status, mapping.columns(), mapping.statusToColumn());
        String proposedColumnId = boardColumnResolver.resolveColumnId(
            proposedCategoryOf(status, proposedCategory), mapping.columns(), mapping.statusToColumn());

        if (Objects.equals(currentColumnId, proposedColumnId)) {
            return Optional.empty();
        }

        return Optional.of(new BoardMove(
            project.getId(),
            project.getKey(),
            project.getName(),
            board.getId(),
            mapping.columnName(currentColumnId),
            mapping.columnName(proposedColumnId),
            issueCount));
    }

    /**
     * The same status as it would be after the change — a value, never the managed row.
     *
     * <p>⚠️ Mutating the loaded entity to ask the question would <em>be</em> the change: this runs inside
     * a transaction and Hibernate flushes what it finds dirty, so a report would quietly write itself.
     */
    private static Status proposedCategoryOf(Status status, StatusCategory proposedCategory) {
        return Status.builder()
            .id(status.getId())
            .name(status.getName())
            .category(proposedCategory)
            .color(status.getColor())
            .build();
    }

    private Status requireStatus(String statusId) {
        return statuses.require(statusId);
    }

    private static StatusResponse toResponse(Status status) {
        return new StatusResponse(status.getId(), status.getName(), status.getCategory(), status.getColor());
    }

}
