package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueLink;
import net.innoventa.tessera.domain.LinkType;
import net.innoventa.tessera.domain.LinkTypeEffect;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.repository.IssueLinkRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.LinkTypeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Refusing the link that would make a deadlock (TSSR-41).
 *
 * <p>⚠️ <strong>A blocking cycle is a deadlock the product created for itself.</strong> "A blocks B" and
 * "B blocks A" means neither can ever be started, by any route, and the only escape is retyping or
 * deleting one of the links. Nothing warns; the two issues simply stop being startable and somebody
 * works out why an hour later.
 *
 * <p>The link code checked only that the identical link did not already exist — it had no view of the
 * graph at all, so nothing stood between an ordinary afternoon and that state.
 *
 * <h2>⚠️ Only blocking links are walked</h2>
 *
 * <p>A cycle of {@code relates to} is not a problem; it is a group of related issues, which is a normal
 * and useful shape. Refusing it would make the tracker refuse a true statement about the work because of
 * a rule that does not apply to it.
 *
 * <h2>⚠️ And this is a graph walk, not depth one</h2>
 *
 * <p>{@link IssueBlockers} caps at depth one on purpose — a refusal naming something three hops away is
 * unactionable. A <em>cycle</em> is the opposite case: it is only ever visible from the whole path, and a
 * one-hop check would catch the two-issue case and let every longer one through.
 */
@Component
@RequiredArgsConstructor
public class BlockingCycles {

    private final IssueLinkRepository issueLinkRepository;
    private final IssueRepository     issueRepository;
    private final LinkTypeRepository  linkTypeRepository;

    /**
     * Refuses a link that would close a cycle of blocking links.
     *
     * <p>The proposed link runs {@code source → target} and means "source blocks target". It closes a
     * cycle exactly when the target already blocks the source — directly or through any chain of
     * blocking links — so the walk starts at the target and follows what it blocks.
     */
    @Transactional(readOnly = true)
    public void requireNoCycle(Issue source, Issue target, LinkType linkType) {
        if (!blocks(linkType)) {
            return;
        }

        List<String> path = pathFrom(target.getId(), source.getId());

        if (path.isEmpty()) {
            return;
        }

        String cycle = String.join(" → ", keysOf(path)) + " → " + target.getIssueKey();

        throw new BusinessRuleViolationException(
            "That would make a blocking cycle — " + cycle + " — and nothing in it could ever be started. "
            + "Break the chain first, or use a link type that does not block.");
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * The chain of blocking links from {@code startIssueId} to {@code goalIssueId}, or empty when there
     * is none. Breadth-first, so the reported cycle is the shortest one and therefore the one somebody
     * can actually hold in their head.
     */
    private List<String> pathFrom(String startIssueId, String goalIssueId) {
        Map<String, LinkType> linkTypes = linkTypeRepository.findAll().stream()
            .collect(Collectors.toMap(LinkType::getId, Function.identity()));

        Map<String, String> cameFrom = new java.util.HashMap<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        seen.add(startIssueId);
        queue.add(startIssueId);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            for (IssueLink link : issueLinkRepository.findBySourceIssueId(current)) {
                LinkType linkType = linkTypes.get(link.getLinkTypeId());

                if (!blocks(linkType) || !seen.add(link.getTargetIssueId())) {
                    continue;
                }

                cameFrom.put(link.getTargetIssueId(), current);

                if (link.getTargetIssueId().equals(goalIssueId)) {
                    return trace(cameFrom, startIssueId, goalIssueId);
                }

                queue.add(link.getTargetIssueId());
            }
        }

        return List.of();
    }

    private static List<String> trace(Map<String, String> cameFrom, String startIssueId, String goalIssueId) {
        Deque<String> path = new ArrayDeque<>();

        for (String at = goalIssueId; at != null; at = cameFrom.get(at)) {
            path.addFirst(at);

            if (at.equals(startIssueId)) {
                break;
            }
        }

        return List.copyOf(path);
    }

    private List<String> keysOf(List<String> issueIds) {
        Map<String, String> keysById = issueRepository.findAllById(issueIds).stream()
            .collect(Collectors.toMap(Issue::getId, Issue::getIssueKey));

        return issueIds.stream().map(issueId -> keysById.getOrDefault(issueId, issueId)).toList();
    }

    /** ⚠️ A warning is not a block — see {@link LinkTypeEffect} — so a chain of warnings is no cycle. */
    private static boolean blocks(LinkType linkType) {
        return linkType != null
            && (linkType.getEffect() == LinkTypeEffect.BLOCKS_START
                || linkType.getEffect() == LinkTypeEffect.BLOCKS_DONE);
    }

}
