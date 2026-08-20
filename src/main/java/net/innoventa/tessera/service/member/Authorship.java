package net.innoventa.tessera.service.member;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.MemberKind;
import net.innoventa.tessera.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * <strong>Is this mine?</strong> — asked once, in one place (TSSR-35).
 *
 * <h2>⚠️ Why this exists at all, and why it is the real bill of the epic</h2>
 *
 * <p>Until an agent had a member row, a comment written through a tool was <em>the person's</em>: the
 * caller was the owner and {@code author_member_id} was theirs. After the mirror lands the author is the
 * <strong>agent</strong>, and every question of the form <em>whose is this</em> silently narrows by one
 * level.
 *
 * <p>The one that breaks the day it ships:
 *
 * <pre>{@code
 * if (!comment.getAuthorMemberId().equals(caller.getId())) {
 *     throw new ForbiddenException("You can only edit your own comment");
 * }
 * }</pre>
 *
 * <p>An owner would stop being able to edit — or delete — a comment <em>their own agent wrote for
 * them</em>. That is a behaviour regression rather than a follow-up, and it is why this ticket lands
 * before the columns are dropped.
 *
 * <h2>⚠️ This is attribution, never authorization</h2>
 *
 * <p>It reads {@code parent_id}, which nothing deciding whether a call is <em>allowed</em> may do. The
 * distinction is not pedantry and it is what makes reading the column safe here: this answers
 * <em>whose row is this</em>, after the engine has already answered whether the caller may be in the
 * room at all. Widening it can let somebody edit their own agent's comment; it can never let them into
 * a project.
 */
@Service
@RequiredArgsConstructor
public class Authorship {

    private final MemberRepository memberRepository;

    /**
     * Whether {@code memberId} is this caller, or one of their agents.
     *
     * <p>⚠️ <strong>The cheap half first.</strong> Most rows are the caller's own, and an equality check
     * answers those without touching the database — which matters because this is asked per comment
     * while rendering a thread.
     */
    @Transactional(readOnly = true)
    public boolean belongsTo(String memberId, Member caller) {
        if (memberId == null || caller == null) {
            return false;
        }

        if (memberId.equals(caller.getId())) {
            return true;
        }

        return memberRepository.findByIdAndKind(memberId, MemberKind.AGENT)
                .filter(agent -> caller.getId().equals(agent.getParentId()))
                .isPresent();
    }

    /**
     * The caller and every agent of theirs, as identifiers a query can be narrowed by.
     *
     * <p>For the filters that ask the question in the other direction — <em>assigned to me</em>,
     * <em>reported by me</em> — where widening one predicate is cheaper than testing each row.
     *
     * <p>⚠️ <strong>The caller is always first and always present</strong>, so a caller with no agents
     * yields exactly the single-identifier list the query used to take. Nothing has to branch on whether
     * somebody happens to own a client.
     */
    @Transactional(readOnly = true)
    public List<String> mine(Member caller) {
        List<String> identifiers = new ArrayList<>();

        identifiers.add(caller.getId());
        memberRepository.findByKindAndParentId(MemberKind.AGENT, caller.getId())
                .forEach(agent -> identifiers.add(agent.getId()));

        return identifiers;
    }
}
