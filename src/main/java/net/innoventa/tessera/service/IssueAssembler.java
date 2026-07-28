package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Component;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueLink;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.LinkType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.domain.Resolution;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.domain.Transition;
import net.innoventa.tessera.domain.Version;
import net.innoventa.tessera.domain.VersionLinkKind;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.issue.ComponentRef;
import net.innoventa.tessera.dto.issue.IssueLinkView;
import net.innoventa.tessera.dto.issue.IssueRef;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.LinkDirection;
import net.innoventa.tessera.dto.issue.PrioritySummary;
import net.innoventa.tessera.dto.issue.ResolutionSummary;
import net.innoventa.tessera.dto.issue.StatusSummary;
import net.innoventa.tessera.dto.issue.TransitionOption;
import net.innoventa.tessera.dto.issue.VersionRef;
import net.innoventa.tessera.domain.StatusCategory;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns {@link Issue} entities into the API's row and detail shapes. Kept out of the mutation services
 * so they stay focused on writes; assembling is read-only and batches the small global catalogs into
 * maps to avoid a query per row. The detail shape additionally loads an issue's satellites (labels,
 * components, versions, links, children) and the workflow-legal transitions available from its status.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueAssembler {

    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final PriorityRepository priorityRepository;
    private final StatusRepository statusRepository;
    private final ResolutionRepository resolutionRepository;
    private final MemberRepository memberRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final LabelRepository labelRepository;
    private final IssueComponentRepository issueComponentRepository;
    private final ComponentRepository componentRepository;
    private final IssueVersionRepository issueVersionRepository;
    private final VersionRepository versionRepository;
    private final IssueLinkRepository issueLinkRepository;
    private final LinkTypeRepository linkTypeRepository;
    private final WorkflowResolver workflowResolver;

    // ── Table rows ──────────────────────────────────────────────────────────────

    public List<IssueRowResponse> rows(List<Issue> issues) {
        Catalogs catalogs = loadCatalogs(issues);
        Map<String, String> parentKeys = parentKeysOf(issues);

        return issues.stream()
            .map(issue -> toRow(issue, catalogs, issue.getParentId() != null ? parentKeys.get(issue.getParentId()) : null))
            .toList();
    }

    private IssueRowResponse toRow(Issue issue, Catalogs catalogs, String parentKey) {
        return new IssueRowResponse(
            issue.getId(),
            issue.getIssueKey(),
            issue.getSequence(),
            issue.getSummary(),
            IssueTypeSummary.from(catalogs.types.get(issue.getIssueTypeId())),
            PrioritySummary.from(catalogs.priorities.get(issue.getPriorityId())),
            StatusSummary.from(catalogs.statuses.get(issue.getStatusId())),
            ResolutionSummary.from(catalogs.resolutions.get(issue.getResolutionId())),
            issue.getResolutionId() == null,
            memberSummary(catalogs, issue.getAssigneeMemberId()),
            memberSummary(catalogs, issue.getReporterMemberId()),
            issue.getStoryPoints(),
            parentKey,
            issue.getRank(),
            issue.getUpdatedAt()
        );
    }

    // ── Detail ──────────────────────────────────────────────────────────────────

    public IssueResponse detail(Issue issue, Project project) {
        Catalogs catalogs = loadCatalogs(List.of(issue));

        List<String> labels = issueLabelRepository.findByIssueId(issue.getId()).stream()
            .map(issueLabel -> labelRepository.findById(issueLabel.getLabelId()).orElse(null))
            .filter(label -> label != null)
            .map(net.innoventa.tessera.domain.Label::getName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();

        List<ComponentRef> components = issueComponentRepository.findByIssueId(issue.getId()).stream()
            .map(issueComponent -> componentRepository.findById(issueComponent.getComponentId()).orElse(null))
            .filter(component -> component != null)
            .sorted((first, second) -> first.getName().compareToIgnoreCase(second.getName()))
            .map(ComponentRef::from)
            .toList();

        List<VersionRef> affectsVersions = versionRefs(issue.getId(), VersionLinkKind.AFFECTS);
        List<VersionRef> fixVersions = versionRefs(issue.getId(), VersionLinkKind.FIX);

        IssueRef parent = issue.getParentId() == null
            ? null
            : issueRepository.findById(issue.getParentId()).map(this::refOf).orElse(null);

        List<IssueRef> children = issueRepository.findByParentIdOrderByRankAsc(issue.getId()).stream()
            .map(this::refOf)
            .toList();

        return new IssueResponse(
            issue.getId(),
            issue.getProjectId(),
            issue.getIssueKey(),
            issue.getSequence(),
            issue.getSummary(),
            issue.getDescription(),
            IssueTypeSummary.from(catalogs.types.get(issue.getIssueTypeId())),
            PrioritySummary.from(catalogs.priorities.get(issue.getPriorityId())),
            StatusSummary.from(catalogs.statuses.get(issue.getStatusId())),
            ResolutionSummary.from(catalogs.resolutions.get(issue.getResolutionId())),
            issue.getResolutionId() == null,
            memberSummary(catalogs, issue.getReporterMemberId()),
            memberSummary(catalogs, issue.getAssigneeMemberId()),
            parent,
            children,
            issue.getStoryPoints(),
            issue.getRank(),
            labels,
            components,
            affectsVersions,
            fixVersions,
            links(issue),
            availableTransitions(issue, project),
            issue.getCreatedAt(),
            issue.getUpdatedAt()
        );
    }

    private List<VersionRef> versionRefs(String issueId, VersionLinkKind kind) {
        return issueVersionRepository.findByIssueIdAndLinkKind(issueId, kind).stream()
            .map(issueVersion -> versionRepository.findById(issueVersion.getVersionId()).orElse(null))
            .filter(version -> version != null)
            .sorted((first, second) -> Integer.compare(first.getSequence(), second.getSequence()))
            .map(VersionRef::from)
            .toList();
    }

    private List<IssueLinkView> links(Issue issue) {
        Map<String, LinkType> linkTypes = linkTypeRepository.findAll().stream()
            .collect(Collectors.toMap(LinkType::getId, Function.identity()));

        List<IssueLinkView> outward = issueLinkRepository.findBySourceIssueId(issue.getId()).stream()
            .map(link -> linkView(link, linkTypes.get(link.getLinkTypeId()), LinkDirection.OUTWARD, link.getTargetIssueId()))
            .filter(view -> view != null)
            .toList();

        List<IssueLinkView> inward = issueLinkRepository.findByTargetIssueId(issue.getId()).stream()
            .map(link -> linkView(link, linkTypes.get(link.getLinkTypeId()), LinkDirection.INWARD, link.getSourceIssueId()))
            .filter(view -> view != null)
            .toList();

        return java.util.stream.Stream.concat(outward.stream(), inward.stream()).toList();
    }

    private IssueLinkView linkView(IssueLink link, LinkType linkType, LinkDirection direction, String otherIssueId) {
        if (linkType == null) {
            return null;
        }
        Issue other = issueRepository.findById(otherIssueId).orElse(null);
        if (other == null) {
            return null;
        }
        String label = direction == LinkDirection.OUTWARD ? linkType.getOutwardLabel() : linkType.getInwardLabel();
        return new IssueLinkView(link.getId(), linkType.getId(), linkType.getName(), direction, label, refOf(other));
    }

    private List<TransitionOption> availableTransitions(Issue issue, Project project) {
        String workflowId = workflowResolver.resolveWorkflowId(project, issue.getIssueTypeId());
        Map<String, Status> statuses = statusRepository.findAll().stream()
            .collect(Collectors.toMap(Status::getId, Function.identity()));

        return workflowResolver.availableTransitions(workflowId, issue.getStatusId()).stream()
            .map(transition -> toOption(transition, statuses.get(transition.getToStatusId())))
            .filter(option -> option != null)
            .toList();
    }

    private TransitionOption toOption(Transition transition, Status target) {
        if (target == null) {
            return null;
        }
        return new TransitionOption(
            transition.getId(),
            transition.getName(),
            target.getId(),
            target.getName(),
            target.getCategory(),
            target.getCategory() == StatusCategory.DONE
        );
    }

    private IssueRef refOf(Issue issue) {
        IssueType type = issueTypeRepository.findById(issue.getIssueTypeId()).orElse(null);
        Status status = statusRepository.findById(issue.getStatusId()).orElse(null);
        return new IssueRef(
            issue.getId(),
            issue.getIssueKey(),
            issue.getSummary(),
            IssueTypeSummary.from(type),
            StatusSummary.from(status),
            issue.getResolutionId() == null
        );
    }

    private MemberSummary memberSummary(Catalogs catalogs, String memberId) {
        if (memberId == null) {
            return null;
        }
        Member member = catalogs.members.get(memberId);
        return member == null ? null : MemberSummary.from(member);
    }

    private Map<String, String> parentKeysOf(List<Issue> issues) {
        List<String> parentIds = issues.stream()
            .map(Issue::getParentId)
            .filter(parentId -> parentId != null)
            .distinct()
            .toList();

        if (parentIds.isEmpty()) {
            return Map.of();
        }

        return issueRepository.findAllById(parentIds).stream()
            .collect(Collectors.toMap(Issue::getId, Issue::getIssueKey));
    }

    private Catalogs loadCatalogs(List<Issue> issues) {
        List<String> memberIds = issues.stream()
            .flatMap(issue -> java.util.stream.Stream.of(issue.getReporterMemberId(), issue.getAssigneeMemberId()))
            .filter(memberId -> memberId != null)
            .distinct()
            .toList();

        return new Catalogs(
            issueTypeRepository.findAll().stream().collect(Collectors.toMap(IssueType::getId, Function.identity())),
            priorityRepository.findAll().stream().collect(Collectors.toMap(Priority::getId, Function.identity())),
            statusRepository.findAll().stream().collect(Collectors.toMap(Status::getId, Function.identity())),
            resolutionRepository.findAll().stream().collect(Collectors.toMap(Resolution::getId, Function.identity())),
            memberRepository.findAllById(memberIds).stream().collect(Collectors.toMap(Member::getId, Function.identity()))
        );
    }

    /** The batched global catalogs plus the members referenced by the issues being assembled. */
    private record Catalogs(
        Map<String, IssueType> types,
        Map<String, Priority> priorities,
        Map<String, Status> statuses,
        Map<String, Resolution> resolutions,
        Map<String, Member> members
    ) {
    }

}
