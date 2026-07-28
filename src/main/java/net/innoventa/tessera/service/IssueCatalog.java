package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Resolution;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.ResolutionRepository;
import net.innoventa.tessera.repository.StatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a domain id to the human display string the activity log snapshots (ADR-0007). Every
 * mutation records history as point-in-time strings, and this is where those strings come from — one
 * place, so "who was the assignee" and "what was the status name" read the same everywhere. Lookups
 * are single-row and only run on the few fields a mutation actually touched.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueCatalog {

    private final IssueTypeRepository issueTypeRepository;
    private final PriorityRepository priorityRepository;
    private final StatusRepository statusRepository;
    private final ResolutionRepository resolutionRepository;
    private final MemberRepository memberRepository;
    private final IssueRepository issueRepository;

    public String typeName(String issueTypeId) {
        return issueTypeId == null ? null
            : issueTypeRepository.findById(issueTypeId).map(IssueType::getName).orElse(null);
    }

    public String priorityName(String priorityId) {
        return priorityId == null ? null
            : priorityRepository.findById(priorityId).map(Priority::getName).orElse(null);
    }

    public String statusName(String statusId) {
        return statusId == null ? null
            : statusRepository.findById(statusId).map(Status::getName).orElse(null);
    }

    public String resolutionName(String resolutionId) {
        return resolutionId == null ? null
            : resolutionRepository.findById(resolutionId).map(Resolution::getName).orElse(null);
    }

    public String memberName(String memberId) {
        return memberId == null ? null
            : memberRepository.findById(memberId).map(Member::getDisplayName).orElse(null);
    }

    public String issueKey(String issueId) {
        return issueId == null ? null
            : issueRepository.findById(issueId).map(Issue::getIssueKey).orElse(null);
    }

    public String storyPoints(Double storyPoints) {
        if (storyPoints == null) {
            return null;
        }
        return storyPoints == Math.floor(storyPoints)
            ? Integer.toString(storyPoints.intValue())
            : storyPoints.toString();
    }

}
