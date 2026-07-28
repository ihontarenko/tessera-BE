package net.innoventa.tessera.service.filter;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueLabel;
import net.innoventa.tessera.domain.IssueLink;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Label;
import net.innoventa.tessera.domain.LinkType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Resolution;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.repository.IssueLabelRepository;
import net.innoventa.tessera.repository.IssueLinkRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.LabelRepository;
import net.innoventa.tessera.repository.LinkTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.ResolutionRepository;
import net.innoventa.tessera.repository.StatusRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Hydrates a slice of issues into the {@link IssueFilterView}s a predicate is evaluated against.
 * <p>
 * Every lookup is batched: the small global catalogs in one {@code findAll} each, the referenced
 * members and the label/link joins in one query per relation for the whole slice. There is no
 * per-issue query, and the whole factory only runs when a request actually carries a filter — an
 * unfiltered board render never pays for any of it.
 */
@Service
@RequiredArgsConstructor
public class IssueFilterViewFactory {

    private final StatusRepository         statusRepository;
    private final IssueTypeRepository      issueTypeRepository;
    private final PriorityRepository       priorityRepository;
    private final ResolutionRepository     resolutionRepository;
    private final MemberRepository         memberRepository;
    private final IssueLabelRepository     issueLabelRepository;
    private final LabelRepository          labelRepository;
    private final IssueLinkRepository      issueLinkRepository;
    private final LinkTypeRepository       linkTypeRepository;
    private final IssueRepository          issueRepository;

    /**
     * @param issues   the loaded board slice, in board order
     * @param epicKeys issue id → nearest Epic ancestor key, already resolved by the caller (the board
     *                 render needs the same map for its cards, so it is never resolved twice)
     */
    public List<IssueFilterView> build(List<Issue> issues, Map<String, String> epicKeys) {
        if (issues.isEmpty()) {
            return List.of();
        }

        Set<String> issueIds = issues.stream().map(Issue::getId).collect(Collectors.toSet());

        Map<String, Status> statuses = byId(statusRepository.findAll(), Status::getId);
        Map<String, IssueType> types = byId(issueTypeRepository.findAll(), IssueType::getId);
        Map<String, Priority> priorities = byId(priorityRepository.findAll(), Priority::getId);
        Map<String, Resolution> resolutions = byId(resolutionRepository.findAll(), Resolution::getId);
        Map<String, Member> members = byId(memberRepository.findAllById(referencedMemberIds(issues)), Member::getId);

        Map<String, List<String>> labels = labelsByIssue(issueIds);
        Map<String, List<IssueFilterView.LinkReference>> links = linksByIssue(issueIds);

        return issues.stream()
            .map(issue -> new IssueFilterView(
                issue.getId(),
                issue.getIssueKey(),
                issue.getSummary(),
                typeReference(types.get(issue.getIssueTypeId())),
                priorityReference(priorities.get(issue.getPriorityId())),
                statusReference(statuses.get(issue.getStatusId())),
                resolutionReference(resolutions.get(issue.getResolutionId())),
                memberReference(members.get(issue.getAssigneeMemberId())),
                memberReference(members.get(issue.getReporterMemberId())),
                epicReference(epicKeys.get(issue.getId())),
                labels.getOrDefault(issue.getId(), List.of()),
                links.getOrDefault(issue.getId(), List.of()),
                instant(issue.getCreatedAt()),
                instant(issue.getUpdatedAt()),
                instant(issue.getResolvedAt())
            ))
            .toList();
    }

    /**
     * Timestamps are stored as wall-clock {@code LocalDateTime} but a filter compares them against
     * {@code now}, so they are pinned to the same zone the application writes them in — the one
     * {@code LocalDateTime.now()} used on the way in. Reading them back through any other zone would
     * shift every card's age by the offset.
     */
    private Instant instant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZone(ZoneId.systemDefault()).toInstant();
    }

    private List<String> referencedMemberIds(List<Issue> issues) {
        return issues.stream()
            .flatMap(issue -> Stream.of(issue.getAssigneeMemberId(), issue.getReporterMemberId()))
            .filter(memberId -> memberId != null)
            .distinct()
            .toList();
    }

    /**
     * The label names carried by the slice, grouped by issue: one query for the join rows, a second for
     * the labels they point at, and nothing per-issue in between.
     * <p>
     * This was a generic helper while components and versions shared its shape. They are gone
     * (ADR-0017), and labels is the only name-valued relation left, so the concrete form says the same
     * thing without six functional parameters standing in for two field accesses.
     */
    private Map<String, List<String>> labelsByIssue(Set<String> issueIds) {
        List<IssueLabel> joins = issueLabelRepository.findByIssueIdIn(issueIds);

        if (joins.isEmpty()) {
            return Map.of();
        }

        Map<String, Label> labels = byId(
            labelRepository.findAllById(joins.stream().map(IssueLabel::getLabelId).distinct().toList()),
            Label::getId);

        return joins.stream()
            .filter(join -> labels.containsKey(join.getLabelId()))
            .collect(Collectors.groupingBy(
                IssueLabel::getIssueId,
                Collectors.mapping(join -> labels.get(join.getLabelId()).getName(), Collectors.toList())));
    }

    /**
     * Both ends of every link touching the slice. A link is stored once, source → target, so each row
     * lands on <em>two</em> issues under opposite directions — that is what lets {@code is blocked}
     * (inward) and {@code is blocks(key)} (outward) read the same record and mean different things.
     * <p>
     * The far end is named by issue key rather than id, matching how {@code blocks('TIC-42')} is
     * written; a link whose far end is outside the slice still counts, since being blocked by an issue
     * that is not on this board is still being blocked.
     */
    private Map<String, List<IssueFilterView.LinkReference>> linksByIssue(Set<String> issueIds) {
        List<IssueLink> links = issueLinkRepository.findBySourceIssueIdInOrTargetIssueIdIn(issueIds, issueIds);

        if (links.isEmpty()) {
            return Map.of();
        }

        Map<String, LinkType> linkTypes = byId(linkTypeRepository.findAllById(
            links.stream().map(IssueLink::getLinkTypeId).distinct().toList()), LinkType::getId);

        Set<String> endpointIds = links.stream()
            .flatMap(link -> Stream.of(link.getSourceIssueId(), link.getTargetIssueId()))
            .collect(Collectors.toSet());

        Map<String, String> keysById = keysById(endpointIds);

        Map<String, List<IssueFilterView.LinkReference>> linksByIssue = new HashMap<>();

        for (IssueLink link : links) {
            LinkType linkType = linkTypes.get(link.getLinkTypeId());

            if (linkType == null) {
                continue;
            }

            addLink(linksByIssue, link.getSourceIssueId(), linkType.getName(),
                IssueFilterView.LinkReference.OUTWARD, keysById.get(link.getTargetIssueId()), issueIds);
            addLink(linksByIssue, link.getTargetIssueId(), linkType.getName(),
                IssueFilterView.LinkReference.INWARD, keysById.get(link.getSourceIssueId()), issueIds);
        }

        return linksByIssue;
    }

    private void addLink(
        Map<String, List<IssueFilterView.LinkReference>> linksByIssue,
        String issueId,
        String linkTypeName,
        String direction,
        String farEndKey,
        Set<String> issueIds
    ) {
        if (!issueIds.contains(issueId) || farEndKey == null) {
            return;
        }

        linksByIssue
            .computeIfAbsent(issueId, key -> new ArrayList<>())
            .add(new IssueFilterView.LinkReference(linkTypeName, direction, farEndKey));
    }

    /** Issue keys for both ends of the slice's links, including ends outside the board. */
    private Map<String, String> keysById(Set<String> issueIds) {
        return issueRepository.findAllById(issueIds).stream()
            .collect(Collectors.toMap(Issue::getId, Issue::getIssueKey));
    }

    private <T> Map<String, T> byId(Collection<T> entries, Function<T, String> identity) {
        return entries.stream().collect(Collectors.toMap(identity, Function.identity()));
    }

    private IssueFilterView.TypeReference typeReference(IssueType type) {
        return type == null ? null : new IssueFilterView.TypeReference(type.getName(), type.getHierarchyLevel());
    }

    private IssueFilterView.PriorityReference priorityReference(Priority priority) {
        return priority == null ? null : new IssueFilterView.PriorityReference(priority.getName());
    }

    private IssueFilterView.StatusReference statusReference(Status status) {
        return status == null
            ? null
            : new IssueFilterView.StatusReference(status.getName(), status.getCategory().name());
    }

    private IssueFilterView.ResolutionReference resolutionReference(Resolution resolution) {
        return resolution == null ? null : new IssueFilterView.ResolutionReference(resolution.getName());
    }

    private IssueFilterView.MemberReference memberReference(Member member) {
        return member == null ? null : new IssueFilterView.MemberReference(member.getId(), member.getDisplayName());
    }

    private IssueFilterView.EpicReference epicReference(String epicKey) {
        return epicKey == null ? null : new IssueFilterView.EpicReference(epicKey);
    }

}
