package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Resolution;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.block.BlockStatus;
import net.innoventa.tessera.dto.block.PageBlockView;
import net.innoventa.tessera.dto.issue.IssueRowResponse;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.dto.issue.IssueTypeSummary;
import net.innoventa.tessera.dto.issue.StatusSummary;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.ResolutionRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import net.innoventa.tessera.service.block.spi.BlockRequest;
import net.innoventa.tessera.service.block.spi.BlockSuggestRequest;
import net.innoventa.tessera.service.block.spi.BlockSuggestion;
import net.innoventa.tessera.service.block.spi.PageBlockResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@code :::issue TSSR-4} — one issue, as it stands right now (TSSR-18).
 *
 * <p>The block that makes a written page worth keeping: a runbook that names the issue it came out of
 * shows that issue's <em>current</em> status, not the one it had the day somebody typed it.
 *
 * <p>⚠️ <strong>It lives here, beside the issues, and not in the wiki.</strong> The block engine has no
 * idea what an issue is and must not learn — that is the whole point of the SPI, and it is what lets
 * TSSR-19 move pages to WiQi while this class stays exactly where it is.
 *
 * <p>⚠️ <strong>The argument is a key or a permanent hash</strong>, and the writer does not have to say
 * which. A key is what somebody types into a page; a hash is what a picker inserts and what goes on
 * resolving after the key has been re-minted. See {@link #byKeyOrHash} for why the order of the two
 * lookups is not arbitrary.
 *
 * <p>⚠️ <strong>An issue the reader may not see is a miss, indistinguishable from one that does not
 * exist.</strong> Issue keys are guessable — {@code TSSR-1} through {@code TSSR-n} — so a resolver that
 * said "you may not see this" would let anybody count another project's issues by writing directives
 * into a page of their own. {@code IssueReferenceService} states the same rule for inline mentions; this
 * is that rule again, in the one other place a key can be written.
 */
@Component
@RequiredArgsConstructor
public class IssueBlockResolver implements PageBlockResolver {

    private final IssueRepository issueRepository;
    private final StatusRepository statusRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final PriorityRepository priorityRepository;
    private final ResolutionRepository resolutionRepository;
    private final MemberRepository memberRepository;
    private final ProjectAccess projectAccess;
    private final IssueSearchService issueSearchService;

    @Override
    public String directive() {
        return "issue";
    }

    @Override
    public PageBlockView resolve(BlockRequest request) {
        String argument = request.argument();
        Issue  issue    = byKeyOrHash(argument);

        if (issue == null || !visible(request.caller(), issue)) {
            return PageBlockView.miss(directive(), argument, BlockStatus.NOT_FOUND);
        }

        Status status = statusRepository.findById(issue.getStatusId()).orElse(null);

        return PageBlockView.of(directive(), argument, new PageBlockView.IssueBlock(
            issue.getIssueKey(),
            issue.getSummary(),
            nameOf(issueTypeRepository.findById(issue.getIssueTypeId()).map(IssueType::getName)),
            status == null ? null : status.getName(),
            status == null || status.getCategory() == null ? null : status.getCategory().name(),
            status == null ? null : status.getColor(),
            nameOf(priorityRepository.findById(issue.getPriorityId()).map(Priority::getName)),
            assigneeName(issue),
            issue.getStoryPoints(),
            // ⚠️ The invariant, not the status name (ADR-0004) — it keeps working when somebody adds a
            // status this code has never heard of.
            issue.getResolutionId() == null,
            issue.getResolutionId() == null
                ? null
                : nameOf(resolutionRepository.findById(issue.getResolutionId()).map(Resolution::getName))));
    }

    /**
     * The issues somebody could refer to — what the link dialog's Issues tab is a view of.
     *
     * <p>⚠️ <strong>The search decides visibility, and nothing here re-decides it.</strong>
     * {@code IssueSearchService} already runs over the projects this member may browse and never
     * widens; a second filter added at this layer would be a second thing to keep in step, and the day
     * they disagreed one of them would be wrong about who may see what.
     *
     * <p>⚠️ <strong>Open work first, and archived work not at all.</strong> A reference is written while
     * somebody is describing work in flight — a picker whose first page is last year's closed tickets is
     * a picker people stop opening.
     *
     * <p>⚠️ And the reference carries the <strong>hash</strong>. A picker that inserted the key would
     * write the fragile form on every use, which is the one thing this whole path exists to stop.
     */
    @Override
    public List<BlockSuggestion> suggest(BlockSuggestRequest request) {
        IssueSearchResponse found = issueSearchService.search(
            request.caller(),
            request.isBrowsing() ? null : request.query(),
            null,
            null,
            null,
            true,
            false,
            0,
            request.limit());

        return found.items().stream().map(item -> describe(item.issue())).toList();
    }

    private BlockSuggestion describe(IssueRowResponse issue) {
        return new BlockSuggestion(
            "issue:" + issue.hash(),
            issue.issueKey(),
            issue.summary(),
            line(nameOf(Optional.ofNullable(issue.type()).map(IssueTypeSummary::name)),
                 nameOf(Optional.ofNullable(issue.status()).map(StatusSummary::name))),
            "/issues/" + issue.issueKey());
    }

    /** The state line, blanks dropped — a suggestion reading {@code Bug ·  } looks like a defect. */
    private static String line(String... parts) {
        return Stream.of(parts).filter(part -> part != null && !part.isBlank())
                .reduce((left, right) -> left + " · " + right)
                .orElse(null);
    }

    /**
     * The issue an argument names, whichever of the two forms it is written in.
     *
     * <p>⚠️ <strong>Key first, hash second, and the order is load-bearing.</strong> Six hex characters
     * is also a perfectly ordinary issue key to anything that only looks at shape, so asking the hash
     * first would let a hash-shaped key shadow the issue whose hash that is. A key is what people write;
     * a hash is what a stored link carries.
     *
     * <p>Case is applied per lookup rather than once: keys are stored uppercase and hashes lowercase,
     * and leaving either to a collation is how the same page comes to render differently on MySQL and
     * on PostgreSQL.
     */
    private Issue byKeyOrHash(String argument) {
        return issueRepository.findByIssueKey(argument.toUpperCase(Locale.ROOT))
                .or(() -> issueRepository.findByHash(argument.toLowerCase(Locale.ROOT)))
                .orElse(null);
    }

    private boolean visible(Member caller, Issue issue) {
        return projectAccess.holds(caller, issue.getProjectId(), Permissions.BROWSE_PROJECT);
    }

    private String assigneeName(Issue issue) {
        if (issue.getAssigneeMemberId() == null) {
            return null;
        }

        return nameOf(memberRepository.findById(issue.getAssigneeMemberId()).map(Member::getDisplayName));
    }

    private static String nameOf(Optional<String> name) {
        return name.orElse(null);
    }

}
