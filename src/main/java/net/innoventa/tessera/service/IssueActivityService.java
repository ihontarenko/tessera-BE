package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.ActivityLog;
import net.innoventa.tessera.domain.ActivityLogItem;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.activity.ActivityLogItemResponse;
import net.innoventa.tessera.dto.activity.ActivityLogResponse;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.ActivityLogItemRepository;
import net.innoventa.tessera.repository.ActivityLogRepository;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.MemberRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The read side of an issue's change history (ticket 08) — the History tab's feed. Events come back
 * newest-first, each grouping its per-field items (ADR-0007). The item values are the point-in-time
 * snapshots the mutation recorded, so a later rename never rewrites what the history shows.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueActivityService {

    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogItemRepository activityLogItemRepository;
    private final IssueRepository issueRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;

    public List<ActivityLogResponse> list(Jwt jwt, String issueId) {
        memberService.resolveMember(jwt);
        requireIssue(issueId);

        List<ActivityLog> events = activityLogRepository.findByIssueIdOrderByCreatedAtDesc(issueId);
        if (events.isEmpty()) {
            return List.of();
        }

        Map<String, List<ActivityLogItem>> itemsByEvent = activityLogItemRepository
            .findByActivityLogIdIn(events.stream().map(ActivityLog::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(ActivityLogItem::getActivityLogId));

        return events.stream()
            .map(event -> new ActivityLogResponse(
                event.getId(),
                memberRepository.findById(event.getActorMemberId()).map(MemberSummary::from).orElse(null),
                event.getAgentName(),
                event.getCreatedAt(),
                itemsByEvent.getOrDefault(event.getId(), List.of()).stream()
                    .map(item -> new ActivityLogItemResponse(item.getField(), item.getOldValue(), item.getNewValue()))
                    .toList()))
            .toList();
    }

    private Issue requireIssue(String issueId) {
        return issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
    }

}
