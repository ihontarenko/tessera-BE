package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.dto.board.BoardSettingsView;
import net.innoventa.tessera.dto.board.SetDoneThresholdRequest;
import net.innoventa.tessera.dto.board.SetScopeStrategyRequest;
import net.innoventa.tessera.dto.board.SetSwimlaneStrategyRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The board's view settings (Phase-2 tickets 04/06, Phase-3 ticket 08) — the swimlane strategy, the
 * done-threshold and the scope strategy — behind the same {@code ADMINISTER_PROJECT} gate column
 * configuration uses ({@link BoardService#requireAdministrableBoard}). Each is a property of the
 * <em>board</em>, so every member viewing it sees the same grouping, the same cutoff and the same slice
 * of work; none of them is a per-viewer preference.
 * <p>
 * Every setting is set <strong>independently, through its own endpoint</strong>, never as one combined
 * payload: a caller that had to echo back the settings it wasn't changing would silently revert another
 * administrator's concurrent edit from its own stale copy of the board.
 * <p>
 * Nothing here interprets the settings: grouping and hiding both happen client-side over the flat
 * payload (ADR-0009), and this service only persists the choice.
 */
@Service
@RequiredArgsConstructor
public class BoardSettingsService {

    private final BoardService boardService;

    @Transactional
    public BoardSettingsView setSwimlaneStrategy(Jwt jwt, String projectId, SetSwimlaneStrategyRequest request) {
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
        board.setSwimlaneStrategy(request.strategy());

        return BoardSettingsView.from(board);
    }

    @Transactional
    public BoardSettingsView setDoneThreshold(Jwt jwt, String projectId, SetDoneThresholdRequest request) {
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
        board.setHideDoneOlderThanDays(request.days());

        return BoardSettingsView.from(board);
    }

    /**
     * Switch the project between Scrum and a board of everything (Phase-3 ticket 08). One field decides
     * both the board's issue source and whether the Backlog and Reports views exist at all (ADR-0012),
     * which is the whole reason changing process is a setting rather than a migration: nothing branches
     * on the project's type, so there is no type to change.
     * <p>
     * It changes <strong>only what the board renders</strong>. Switching away from sprint scope while one
     * is running does not close, alter or delete the sprint or a single membership row — the sprint keeps
     * running, unwatched, and switching back shows it exactly as it was. Switching towards sprint scope
     * with no sprint yet is equally uneventful: the board holds nothing and says so, which is the
     * start-a-sprint empty state rather than an error.
     */
    @Transactional
    public BoardSettingsView setScopeStrategy(Jwt jwt, String projectId, SetScopeStrategyRequest request) {
        Board board = boardService.requireAdministrableBoard(jwt, projectId);
        board.setScopeStrategy(request.strategy());

        return BoardSettingsView.from(board);
    }

}
