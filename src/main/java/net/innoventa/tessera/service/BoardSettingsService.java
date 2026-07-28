package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.dto.board.BoardSettingsView;
import net.innoventa.tessera.dto.board.SetDoneThresholdRequest;
import net.innoventa.tessera.dto.board.SetSwimlaneStrategyRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The board's view settings (Phase-2 tickets 04/06) — the swimlane strategy and the done-threshold —
 * behind the same {@code ADMINISTER_PROJECT} gate column configuration uses
 * ({@link BoardService#requireAdministrableBoard}). Both are properties of the <em>board</em>, so every
 * member viewing it sees the same grouping and the same cutoff; neither is a per-viewer preference.
 * <p>
 * The two settings are set <strong>independently</strong>, never as one combined payload: a caller
 * that had to echo back the setting it wasn't changing would silently revert another administrator's
 * concurrent edit from its own stale copy of the board.
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

}
