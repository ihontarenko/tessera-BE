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
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.ResolutionRepository;
import net.innoventa.tessera.repository.StatusRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.ProjectAccess;
import net.innoventa.tessera.service.block.spi.BlockRequest;
import net.innoventa.tessera.service.block.spi.PageBlockResolver;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

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

    @Override
    public String directive() {
        return "issue";
    }

    @Override
    public PageBlockView resolve(BlockRequest request) {
        String argument = request.argument();

        // Keys are stored uppercase and somebody writing prose may not have been. Normalised here rather
        // than left to a collation, which MySQL and PostgreSQL would decide differently.
        Issue issue = issueRepository.findByIssueKey(argument.toUpperCase(Locale.ROOT)).orElse(null);

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
