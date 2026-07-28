package net.innoventa.tessera.dto.board;

import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.SwimlaneStrategy;

/**
 * The board's view settings as they now stand — the echo of an
 * {@link UpdateBoardSettingsRequest}. The same two fields ride along on every
 * {@link BoardResponse}, so the client can apply them without a second read.
 */
public record BoardSettingsView(SwimlaneStrategy swimlaneStrategy, Integer hideDoneOlderThanDays) {

    public static BoardSettingsView from(Board board) {
        return new BoardSettingsView(board.getSwimlaneStrategy(), board.getHideDoneOlderThanDays());
    }

}
