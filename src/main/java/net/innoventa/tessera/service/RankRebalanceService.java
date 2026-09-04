package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The other half of ADR-0006's "a rare rebalance handles the pathological case" — the half that was
 * never wired up. {@link RankService#rebalancedRanks(int)} existed and nothing called it, so a
 * project's ranks only ever grew, and the busiest project filled the column and stopped accepting
 * issues (TSSR-155).
 *
 * <h2>Why a rebalance and not simply a wider column</h2>
 *
 * <p>Widening buys time and nothing else: the growth does not stop, it restarts from a higher wall.
 * Redistributing a project's ranks evenly is what actually resets the space — the order is preserved
 * exactly, because the new set is handed out in the order the old ranks already sorted in.
 *
 * <p>⚠️ <strong>Every issue in the project is rewritten, archived ones included.</strong> Rank is a
 * single global ordering (ADR-0006) and the archived rows are still in it; skipping them would leave
 * them interleaved at their old, now-meaningless positions the moment one is brought back.
 *
 * <p>⚠️ <strong>It runs before a write, never after a failure.</strong> A tracker that answers a SQL
 * truncation error to "raise an issue" tells its user nothing they can act on, so the check happens
 * while there is still room to act on it — see {@link RankService#MAXIMUM_HEALTHY_LENGTH}.
 */
@Service
@RequiredArgsConstructor
public class RankRebalanceService {

    private static final Logger logger = LoggerFactory.getLogger(RankRebalanceService.class);

    private final IssueRepository issueRepository;
    private final RankService rankService;

    /**
     * Rebalances the project when its longest rank has outgrown what the space should ever need, and
     * does nothing at all otherwise — which is every call but a handful in a project's lifetime.
     *
     * @return whether anything was rewritten
     */
    @Transactional
    public boolean rebalanceIfNeeded(String projectId) {
        Integer longest = issueRepository.findLongestRankLength(projectId);

        if (longest == null || longest <= RankService.MAXIMUM_HEALTHY_LENGTH) {
            return false;
        }

        int rewritten = rebalance(projectId);
        logger.info("Rebalanced {} ranks in project {} — the longest was {} characters", rewritten, projectId, longest);

        return true;
    }

    /**
     * Rewrites every rank in the project as an evenly-spaced set, in the order the current ranks sort
     * in. Flushed before returning, so a caller that goes on to read a neighbour's rank in the same
     * transaction reads the new one.
     *
     * @return how many issues were rewritten
     */
    @Transactional
    public int rebalance(String projectId) {
        List<Issue> issues = issueRepository.findByProjectIdOrderByRankAsc(projectId);
        List<String> ranks = rankService.rebalancedRanks(issues.size());

        for (int position = 0; position < issues.size(); position++) {
            issues.get(position).setRank(ranks.get(position));
        }

        issueRepository.saveAllAndFlush(issues);

        return issues.size();
    }

}
