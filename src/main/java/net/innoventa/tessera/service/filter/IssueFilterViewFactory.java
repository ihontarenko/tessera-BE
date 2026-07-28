package net.innoventa.tessera.service.filter;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Component;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueComponent;
import net.innoventa.tessera.domain.IssueLabel;
import net.innoventa.tessera.domain.IssueLink;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.IssueVersion;
import net.innoventa.tessera.domain.Label;
import net.innoventa.tessera.domain.LinkType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Resolution;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.Version;
import net.innoventa.tessera.repository.ComponentRepository;
import net.innoventa.tessera.repository.IssueComponentRepository;
import net.innoventa.tessera.repository.IssueLabelRepository;
import net.innoventa.tessera.repository.IssueLinkRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.IssueVersionRepository;
import net.innoventa.tessera.repository.LabelRepository;
import net.innoventa.tessera.repository.LinkTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.ResolutionRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.repository.VersionRepository;
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
 * members and the label/component/version/link joins in one query per relation for the whole slice.
 * There is no per-issue query, and the whole factory only runs when a request actually carries a
 * filter — an unfiltered board render never pays for any of it.
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
    private final IssueComponentRepository issueComponentRepository;
    private final ComponentRepository      componentRepository;
    private final IssueVersionRepository   issueVersionRepository;
    private final VersionRepository        versionRepository;
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
        Map<String, List<String>> components = componentsByIssue(issueIds);
        Map<String, List<String>> versions = versionsByIssue(issueIds);
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
                components.getOrDefault(issue.getId(), List.of()),
                versions.getOrDefault(issue.getId(), List.of()),
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

    private Map<String, List<String>> labelsByIssue(Set<String> issueIds) {
        return namesByIssue(
            issueLabelRepository.findByIssueIdIn(issueIds),
            IssueLabel::getIssueId, IssueLabel::getLabelId,
            labelRepository::findAllById, Label::getId, Label::getName);
    }

    private Map<String, List<String>> componentsByIssue(Set<String> issueIds) {
        return namesByIssue(
            issueComponentRepository.findByIssueIdIn(issueIds),
            IssueComponent::getIssueId, IssueComponent::getComponentId,
            componentRepository::findAllById, Component::getId, Component::getName);
    }

    private Map<String, List<String>> versionsByIssue(Set<String> issueIds) {
        return namesByIssue(
            issueVersionRepository.findByIssueIdIn(issueIds),
            IssueVersion::getIssueId, IssueVersion::getVersionId,
            versionRepository::findAllById, Version::getId, Version::getName);
    }

    /**
     * The shape every name-valued relation shares: read the join rows for the slice, batch-load the
     * catalog entries they point at, and group the names by issue. Labels, components and versions
     * differ only in which repositories and accessors are handed in, so they are one method with three
     * call sites rather than three copies that could drift apart.
     *
     * @param joins       the join rows for the whole slice, already fetched
     * @param catalogById the batched catalog lookup — one query for every entry the joins reference
     */
    private <J, C> Map<String, List<String>> namesByIssue(
        List<J> joins,
        Function<J, String> issueIdOf,
        Function<J, String> catalogIdOf,
        Function<List<String>, Collection<C>> catalogById,
        Function<C, String> catalogIdentity,
        Function<C, String> catalogName
    ) {
        if (joins.isEmpty()) {
            return Map.of();
        }

        List<String> catalogIds = joins.stream().map(catalogIdOf).distinct().toList();
        Map<String, C> catalog = byId(catalogById.apply(catalogIds), catalogIdentity);

        return groupNames(joins.stream()
            .map(join -> new NameLink(issueIdOf.apply(join), name(catalog.get(catalogIdOf.apply(join)), catalogName))));
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

    private Map<String, List<String>> groupNames(Stream<NameLink> links) {
        return links
            .filter(link -> link.name() != null)
            .collect(Collectors.groupingBy(NameLink::issueId, Collectors.mapping(NameLink::name, Collectors.toList())));
    }

    private <T> String name(T catalogEntry, Function<T, String> accessor) {
        return catalogEntry == null ? null : accessor.apply(catalogEntry);
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

    /** One join row flattened to the pair the grouping needs. */
    private record NameLink(String issueId, String name) {
    }

}
