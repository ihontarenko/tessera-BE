package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
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
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.issue.IssueLinkView;
import net.innoventa.tessera.dto.issue.IssueReference;
import net.innoventa.tessera.dto.issue.IssueResponse;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.LinkDirection;
import net.innoventa.tessera.dto.issue.PrioritySummary;
import net.innoventa.tessera.dto.issue.ResolutionSummary;
import net.innoventa.tessera.dto.issue.StatusSummary;
import net.innoventa.tessera.dto.issue.TransitionOption;
import net.innoventa.tessera.domain.StatusCategory;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns {@link Issue} entities into the API's row and detail shapes. Kept out of the mutation services
 * so they stay focused on writes; assembling is read-only and batches the small global catalogs into
 * maps to avoid a query per row. The detail shape additionally loads an issue's satellites (labels,
 * links, children) and the workflow-legal transitions available from its status.
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
    private final IssueLinkRepository issueLinkRepository;
    private final LinkTypeRepository linkTypeRepository;
    private final WorkflowResolver workflowResolver;
    private final IssueBlockers issueBlockers;
    private final BrowsableProjects browsableProjects;

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
            issue.getResolvedAt(),
            issue.getArchivedAt(),
            issue.getUpdatedAt()
        );
    }

    // ── Detail ──────────────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>The caller is a parameter now, and it is not optional</strong> (TSSR-43). An issue used
     * to read the same to everybody allowed to read it at all, which was true while every reference it
     * carried was inside its own project. Links are not: a tracking hub can name work anywhere, so what
     * this answer may contain depends on who asked. There is no caller-less overload on purpose — a
     * default would be a silent decision about disclosure at whichever call site forgot.
     *
     * <p>⚠️ And the caller is threaded in rather than read from the security context: a tool thread has
     * neither a {@code SecurityContext} nor a request scope, so reaching for one would work in a browser
     * and fail over the protocol.
     */
    public IssueResponse detail(Issue issue, Project project, Member caller) {
        Catalogs catalogs = loadCatalogs(List.of(issue));

        List<String> labels = issueLabelRepository.findByIssueId(issue.getId()).stream()
            .map(issueLabel -> labelRepository.findById(issueLabel.getLabelId()).orElse(null))
            .filter(label -> label != null)
            .map(net.innoventa.tessera.domain.Label::getName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();

        IssueReference parent = issue.getParentId() == null
            ? null
            : issueRepository.findById(issue.getParentId()).map(this::referenceOf).orElse(null);

        List<IssueReference> children = issueRepository.findByParentIdOrderByRankAsc(issue.getId()).stream()
            .map(this::referenceOf)
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
            links(issue, caller),
            // Starting is what a blocked issue cannot do, and the one the seeded type ships at — so
            // this is the list the screen puts above the transition controls.
            issueBlockers.blocking(issue.getId(), StatusCategory.IN_PROGRESS),
            availableTransitions(issue, project),
            issue.getCreatedAt(),
            issue.getResolvedAt(),
            issue.getArchivedAt(),
            issue.getUpdatedAt()
        );
    }

    /**
     * ⚠️ <strong>This is the one read in the product that can reach outside the caller's projects</strong>
     * (TSSR-43). Links have no project constraint — deliberately, since gathering an effort that spans
     * projects is what a tracking issue is for — so the far end of one may sit somewhere the reader is
     * not a member of. Every reference therefore goes through {@code visibleReferenceOf}, which
     * redacts rather than drops.
     *
     * <p>⚠️ <strong>Public so that the second reader of links reuses this one</strong> (TSSR-45). The
     * registers screen renders the same links in a table instead of a rail, and a copy of the loop above
     * would be a second disclosure rule able to disagree with this one — the failure mode being a summary
     * shown on one screen and withheld on the other. There is one place links become views.
     */
    public List<IssueLinkView> links(Issue issue, Member caller) {
        Map<String, LinkType> linkTypes = linkTypeRepository.findAll().stream()
            .collect(Collectors.toMap(LinkType::getId, Function.identity()));

        // ⚠️ `browsableProjects`, NEVER `projectAccess.visibleProjectIds` directly. That method answers with
        // an EMPTY list for a caller who browses every project installation-wide — see its javadoc — so
        // asking it here redacted every link, including links inside the caller's own project, for the one
        // caller entitled to see the most. Silently, since a redacted reference is a normal answer. This is
        // the third place that trap has been paid for (`TesseraToolAuthorizer`, `IssueTool` were the others),
        // which is why the question now lives in one component with the `browsesEveryProject` case inside it.
        Set<String> visibleProjectIds = Set.copyOf(browsableProjects.idsFor(caller));

        List<IssueLinkView> outward = issueLinkRepository.findBySourceIssueId(issue.getId()).stream()
            .map(link -> linkView(link, linkTypes.get(link.getLinkTypeId()), LinkDirection.OUTWARD, link.getTargetIssueId(), visibleProjectIds))
            .filter(view -> view != null)
            .toList();

        List<IssueLinkView> inward = issueLinkRepository.findByTargetIssueId(issue.getId()).stream()
            .map(link -> linkView(link, linkTypes.get(link.getLinkTypeId()), LinkDirection.INWARD, link.getSourceIssueId(), visibleProjectIds))
            .filter(view -> view != null)
            .toList();

        return java.util.stream.Stream.concat(outward.stream(), inward.stream()).toList();
    }

    private IssueLinkView linkView(
        IssueLink link,
        LinkType linkType,
        LinkDirection direction,
        String otherIssueId,
        Set<String> visibleProjectIds
    ) {
        if (linkType == null) {
            return null;
        }
        Issue other = issueRepository.findById(otherIssueId).orElse(null);
        if (other == null) {
            return null;
        }
        String label = direction == LinkDirection.OUTWARD ? linkType.getOutwardLabel() : linkType.getInwardLabel();
        return new IssueLinkView(
            link.getId(),
            linkType.getId(),
            linkType.getName(),
            direction,
            label,
            visibleReferenceOf(other, visibleProjectIds));
    }

    /**
     * ⚠️ <strong>A blocked move is not offered here, and that is what stops {@code canMoveTo} lying.</strong>
     *
     * <p>This list is what the interface draws buttons from and what the protocol reports as
     * {@code canMoveTo}. If the block were enforced only on the write, both would advertise a move that
     * always fails — a button that errors every time is worse than no button, and a model told a
     * transition is available will keep trying it.
     */
    private List<TransitionOption> availableTransitions(Issue issue, Project project) {
        String workflowId = workflowResolver.resolveWorkflowId(project, issue.getIssueTypeId());
        Map<String, Status> statuses = statusRepository.findAll().stream()
            .collect(Collectors.toMap(Status::getId, Function.identity()));

        return workflowResolver.availableTransitions(workflowId, issue.getStatusId()).stream()
            .map(transition -> toOption(transition, statuses.get(transition.getToStatusId())))
            .filter(option -> option != null)
            .filter(option -> issueBlockers.blocking(issue.getId(), option.toCategory()).isEmpty())
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

    /**
     * The far side of a link, as much of it as this caller may see (TSSR-43).
     *
     * <p>⚠️ <strong>Only links are redacted, never parents or children.</strong> A parent must be in the
     * same project as its child ({@code IssueHierarchyService.validateParent}), so hierarchy can never
     * reach outside what the reader can already browse — running it through this would be a check that
     * always passes, pretending to be a rule.
     */
    private IssueReference visibleReferenceOf(Issue issue, Set<String> visibleProjectIds) {
        IssueReference full = referenceOf(issue);

        return visibleProjectIds.contains(issue.getProjectId()) ? full : IssueReference.redacted(full);
    }

    private IssueReference referenceOf(Issue issue) {
        IssueType type = issueTypeRepository.findById(issue.getIssueTypeId()).orElse(null);
        Status status = statusRepository.findById(issue.getStatusId()).orElse(null);
        return new IssueReference(
            issue.getId(),
            issue.getIssueKey(),
            issue.getSummary(),
            IssueTypeSummary.from(type),
            StatusSummary.from(status),
            issue.getResolutionId() == null,
            true
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
