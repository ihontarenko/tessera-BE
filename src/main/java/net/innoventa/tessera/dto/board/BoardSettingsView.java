package net.innoventa.tessera.dto.board;

import net.innoventa.tessera.domain.Board;
import net.innoventa.tessera.domain.BoardScopeStrategy;
import net.innoventa.tessera.domain.SwimlaneStrategy;

/**
 * The board's view settings as they now stand — the echo every setter returns, whichever one was set.
 * The same fields ride along on each {@link BoardResponse}, so a client can fold the echo into the
 * board it already holds rather than re-reading it.
 * <p>
 * It is deliberately the <em>whole</em> settings state on the way back while each setter takes only its
 * own field on the way in: reading them together is free, and writing them together is what would let a
 * caller revert another administrator's concurrent edit from its own stale copy.
 */
public record BoardSettingsView(
    SwimlaneStrategy swimlaneStrategy,
    BoardScopeStrategy scopeStrategy,
    Integer hideDoneOlderThanDays
) {

    public static BoardSettingsView from(Board board) {
        return new BoardSettingsView(board.getSwimlaneStrategy(), board.getScopeStrategy(), board.getHideDoneOlderThanDays());
    }

}
