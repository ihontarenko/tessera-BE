package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueLink;
import net.innoventa.tessera.domain.LinkType;
import net.innoventa.tessera.domain.LinkTypeEffect;
import net.innoventa.tessera.domain.StatusCategory;
import net.innoventa.tessera.repository.IssueLinkRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.LinkTypeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What is holding an issue up, and whether that stops a given move (TSSR-41).
 *
 * <h2>⚠️ One answer, read from three places</h2>
 *
 * <p>A transition can be attempted three ways — the list the interface offers
 * ({@code IssueAssembler.availableTransitions}), the write it performs ({@code TransitionService}), and
 * a card dragged across a board ({@code BoardMoveService}). If a blocking rule lived in only the last
 * two, the interface would draw a button that always fails and {@code canMoveTo} over the protocol would
 * name a move that does not work. If it lived only in the first, a drag would walk straight past it.
 * So the rule is here and all three ask.
 *
 * <h2>What counts as blocking</h2>
 *
 * <p>An <strong>inward</strong> link — the end that reads "is blocked by" — whose type carries a
 * blocking {@link LinkTypeEffect}, and whose far end is still open.
 *
 * <p>⚠️ <strong>"Open" is the canonical invariant</strong> (ADR-0004): no resolution set. Not a status
 * name, not a category — a team that finishes work in a status called anything at all still resolves it.
 *
 * <p>⚠️ <strong>An archived blocker does not block, and that needs no rule of its own.</strong> Only a
 * closed issue may be archived, so the open check above has already excluded every archived one. Adding
 * {@code archivedAt == null} beside it would look like a second rule and could never fire.
 *
 * <p>⚠️ <strong>Depth is one, deliberately.</strong> An issue blocked by a blocked issue is blocked by
 * its own blocker and nothing further. The transitive version produces a refusal naming something three
 * hops away, which nobody can act on and nobody can trace — the same reason reply depth is capped
 * (TSSR-26).
 *
 * <p>⚠️ <strong>A warning is not a block.</strong> {@link LinkTypeEffect#WARNS_START} exists precisely
 * so a team can keep the relationship visible without the gate, and folding it in here would remove the
 * only difference between the two levels.
 */
@Component
@RequiredArgsConstructor
public class IssueBlockers {

    private final IssueLinkRepository issueLinkRepository;
    private final IssueRepository     issueRepository;
    private final LinkTypeRepository  linkTypeRepository;

    /**
     * The issue keys holding this one up for the given move, or empty when nothing does.
     *
     * <p>⚠️ <strong>Keys, never summaries.</strong> Links cross project boundaries and issues do not: a
     * blocker may sit in a project the caller cannot open. A key is enough to ask a colleague about;
     * a summary would be somebody else's backlog read out to a stranger.
     */
    @Transactional(readOnly = true)
    public List<String> blocking(String issueId, StatusCategory targetCategory) {
        LinkTypeEffect relevant = effectFor(targetCategory);

        if (relevant == null) {
            return List.of();
        }

        List<IssueLink> inward = issueLinkRepository.findByTargetIssueId(issueId);

        if (inward.isEmpty()) {
            return List.of();
        }

        Map<String, LinkType> linkTypes = linkTypeRepository.findAll().stream()
            .collect(Collectors.toMap(LinkType::getId, Function.identity()));

        List<String> blockerIssueIds = new ArrayList<>();

        for (IssueLink link : inward) {
            LinkType linkType = linkTypes.get(link.getLinkTypeId());

            if (linkType != null && linkType.getEffect() == relevant) {
                blockerIssueIds.add(link.getSourceIssueId());
            }
        }

        if (blockerIssueIds.isEmpty()) {
            return List.of();
        }

        // ⚠️ One filter, not two. An archived blocker cannot block either — but only a CLOSED issue may
        // be archived in the first place, so the open check has already excluded every archived one.
        // A second `archivedAt == null` here would read as a rule and could never fire.
        return issueRepository.findAllById(blockerIssueIds).stream()
            .filter(blocker -> blocker.getResolutionId() == null)
            .map(Issue::getIssueKey)
            .sorted()
            .toList();
    }

    /**
     * Which of these issues are blocked, answered for a whole slice at once.
     *
     * <p>⚠️ <strong>A board is why this exists.</strong> Asking {@link #blocking} per card is three
     * queries per card, and a full board is every issue in the project — so the same question is asked
     * once here and the answer is a set the render reads. A card only needs to know <em>whether</em>,
     * which is also why this returns ids rather than the keys {@link #blocking} does.
     */
    @Transactional(readOnly = true)
    public Set<String> blockedAmong(Collection<String> issueIds, StatusCategory targetCategory) {
        LinkTypeEffect relevant = effectFor(targetCategory);

        if (relevant == null || issueIds.isEmpty()) {
            return Set.of();
        }

        List<IssueLink> links = issueLinkRepository.findBySourceIssueIdInOrTargetIssueIdIn(issueIds, issueIds);

        if (links.isEmpty()) {
            return Set.of();
        }

        Map<String, LinkType> linkTypes = linkTypeRepository.findAll().stream()
            .collect(Collectors.toMap(LinkType::getId, Function.identity()));

        // The blocked end is the TARGET of a blocking link, so only links pointing into this slice
        // count — one whose target is elsewhere says nothing about any card being rendered.
        Set<String> candidateIssueIds = new HashSet<>(issueIds);
        Map<String, Set<String>> blockersByIssue = new HashMap<>();

        for (IssueLink link : links) {
            LinkType linkType = linkTypes.get(link.getLinkTypeId());

            if (linkType != null && linkType.getEffect() == relevant && candidateIssueIds.contains(link.getTargetIssueId())) {
                blockersByIssue
                    .computeIfAbsent(link.getTargetIssueId(), key -> new HashSet<>())
                    .add(link.getSourceIssueId());
            }
        }

        if (blockersByIssue.isEmpty()) {
            return Set.of();
        }

        Set<String> openBlockerIds = issueRepository
            .findAllById(blockersByIssue.values().stream().flatMap(Set::stream).distinct().toList()).stream()
            .filter(blocker -> blocker.getResolutionId() == null)
            .map(Issue::getId)
            .collect(Collectors.toSet());

        return blockersByIssue.entrySet().stream()
            .filter(entry -> entry.getValue().stream().anyMatch(openBlockerIds::contains))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /** The refusal this product would give, or null when nothing is blocking. */
    @Transactional(readOnly = true)
    public String refusalFor(Issue issue, StatusCategory targetCategory) {
        List<String> blockers = blocking(issue.getId(), targetCategory);

        if (blockers.isEmpty()) {
            return null;
        }

        // Names them, the way every other refusal in this product names what would have worked. "This
        // issue is blocked" leaves somebody to go and find out by what; the whole value is the list.
        return issue.getIssueKey() + " is blocked by " + String.join(" and ", blockers)
               + ". Resolve them, or change the link if they are not really blocking this.";
    }

    /**
     * Which effect a move into this category has to answer to.
     *
     * <p>Starting is what "blocked" means in ordinary use, so that is the level the seeded type ships
     * at. Blocking the <em>finish</em> is a separate rule for a team that wants it — refusing there
     * means arguing with somebody about work they have already done.
     */
    private static LinkTypeEffect effectFor(StatusCategory targetCategory) {
        if (targetCategory == StatusCategory.IN_PROGRESS) {
            return LinkTypeEffect.BLOCKS_START;
        }

        if (targetCategory == StatusCategory.DONE) {
            return LinkTypeEffect.BLOCKS_DONE;
        }

        return null;
    }

}
