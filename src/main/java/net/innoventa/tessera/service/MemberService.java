package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.MemberKind;
import org.jmouse.ai.agent.AgentDirectory;
import net.innoventa.tessera.dto.CurrentMemberResponse;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.security.access.InstallationAccess;
import net.innoventa.tessera.service.file.MemberFileTrees;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * The authentication → {@link Member} seam every project-scoped feature resolves the caller through.
 * A validated Identity token carries a {@code sub} (and cached name/email claims) but no Tessera
 * identity; {@link #resolveMember(Jwt)} turns that subject into the local {@code Member} row,
 * provisioning it the first time and refreshing its cached claims thereafter.
 * <p>
 * Naming: this is a single find-or-provision method named without And/Or — {@code resolveMember},
 * <em>not</em> {@code getOrCreateForSubject} (Ivan disallows And/Or inside method names for Tessera,
 * even though Moneta uses that older form).
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository    memberRepository;
    private final MemberProvisioner   memberProvisioner;
    private final InstallationAccess  installationAccess;
    /**
     * ⚠️ The <strong>port</strong>, whose Tessera implementation is {@code AgentMembers} — renaming
     * through it is what keeps the directory and the member mirror agreeing. See {@link #rename}.
     */
    private final AgentDirectory      agentDirectory;

    /** Makes a new member's own file cabinet, once, on the way through provisioning them. */
    private final MemberFileTrees     memberFileTrees;

    /**
     * Find-or-provision the local {@link Member} for a validated token. New subjects are provisioned
     * as {@link SystemRole#USER}; cached {@code displayName}/{@code email} are refreshed whenever the
     * token's claims have changed. Idempotent under concurrent first calls: the unique constraint on
     * {@code subject} lets a losing racer fall back to the winner's row rather than duplicating it.
     */
    @Transactional
    public Member resolveMember(Jwt jwt) {
        String subject = jwt.getSubject();
        String displayName = displayNameFrom(jwt);
        String email = emailFrom(jwt);

        return memberRepository.findBySubject(subject)
            .map(member -> refreshCachedClaims(member, displayName, email))
            .orElseGet(() -> provision(subject, displayName, email));
    }

    /**
     * The caller as the shell renders them, with what they hold installation-wide.
     *
     * <p>The permissions are resolved here rather than left to the client to guess, because the sidebar
     * has to decide whether to offer Administration before anything is clicked. ⚠️ It is a courtesy and
     * never the authorization — see {@link net.innoventa.tessera.security.access.InstallationAccess}.
     */
    @Transactional(readOnly = true)
    public CurrentMemberResponse describe(Member member) {
        List<String> globalPermissions = installationAccess.permissionsOf(member).stream()
            .sorted()
            .toList();

        return CurrentMemberResponse.from(member, globalPermissions);
    }

    /**
     * The administration list — people, clients, or both (TSSR-79).
     *
     * <h2>⚠️ Its own query, never a widened {@link #search}</h2>
     *
     * <p>{@code search} is people-only on purpose (TSSR-33) and every caller of it is a picker, an
     * invite or a mention — a place where a client cannot be chosen. Relaxing it to serve one screen
     * would offer an agent in a dozen places that would then refuse it.
     *
     * <p>The administration screen is the one place that legitimately wants both, because it is the
     * screen that <em>administers the rows</em> rather than the one that picks somebody out of them.
     *
     * @param kind the kind to return, or {@code null} for every kind
     */
    @Transactional(readOnly = true)
    public List<MemberSummary> administered(String query, MemberKind kind) {
        String fragment = (query == null || query.isBlank()) ? null : query.trim();

        return memberRepository.administered(kind, fragment).stream().map(MemberSummary::from).toList();
    }

    /**
     * Renames a member — and for a client, renames the <strong>agent</strong> (TSSR-80).
     *
     * <h2>⚠️ An agent's name is a foreign key in disguise</h2>
     *
     * <p>The member row is a <em>mirror</em> of an entry in the agent directory and the direction is
     * one-way on purpose: {@link AgentMembers#rename} renames the agent and lets the mirror follow, so a
     * by-line says what the agent is called <em>now</em> rather than what it was. Writing
     * {@code displayName} on the row directly leaves the two disagreeing — the exact failure that
     * {@code agent_name}-as-a-snapshot-column was replaced to escape.
     *
     * <p>⚠️ <strong>An administrator may rename somebody else's client</strong>, decided 2026-08-18.
     * Nothing here checks ownership: the route asks for {@code member:administer} installation-wide and
     * that is the whole gate. ⚠️ It settles one verb — nothing about deleting or re-authorising
     * somebody else's client follows from it. And ⚠️ Tessera keeps <strong>no record</strong> of the
     * rename: `TSSR-81` proposed an audited directory here and was ruled Won't Do, which is an accepted
     * answer rather than an oversight.
     */
    @Transactional
    public MemberSummary rename(String memberId, String displayName) {
        Member member = requireMember(memberId);

        if (member.getKind() == MemberKind.AGENT) {
            agentDirectory.rename(member.getId(), displayName);

            return MemberSummary.from(requireMember(memberId));
        }

        member.setDisplayName(displayName);

        return MemberSummary.from(memberRepository.save(member));
    }

    @Transactional(readOnly = true)
    public List<MemberSummary> search(String query) {
        // ⚠️ People, on both branches (TSSR-33). This is the directory a picker, an invite and a mention
        // read; an agent is a member so that authorship has one face, not so that it can be invited.
        List<Member> members = (query == null || query.isBlank())
            ? memberRepository.findAllByKindOrderByDisplayNameAsc(MemberKind.PERSON)
            : memberRepository.search(query);

        return members.stream().map(MemberSummary::from).toList();
    }

    /**
     * The already-provisioned {@link Member} for an id — used when another feature references a
     * person (a project lead, a member being added to a project). A member must have signed in at
     * least once to exist.
     */
    @Transactional(readOnly = true)
    public Member requireMember(String memberId) {
        return memberRepository.findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
    }

    /**
     * The member behind an identity-provider subject, for a caller that is not an HTTP request.
     *
     * <p>⚠️ <strong>Requires rather than provisions, unlike {@link #resolveMember(Jwt)}.</strong> A
     * {@code Jwt} carries the claims a new row would be built from — a display name, an email — and a
     * bare subject carries none of them. Provisioning here would create a member called by its own
     * subject identifier, which is a row somebody has to go and fix.
     *
     * <p>Nothing reaches this without having signed in first: a tool call runs under a session the
     * person already holds, so the row is always there. If it ever is not, that is the interesting
     * fact and it should surface rather than be papered over with an empty member.
     */
    @Transactional(readOnly = true)
    public Member requireBySubject(String subject) {
        return memberRepository.findBySubject(subject)
            // ⚠️ Belt, and the braces are the schema (TSSR-32). An agent's mirror carries the synthetic
            // subject `agent:<id>`, which Identity can never mint — so no token can reach one and this
            // filter can never fire. It is here because "cannot happen" is a claim about a system that
            // keeps changing, and the cost of stating it is one line, while the cost of it becoming
            // false is a caller acting as somebody's agent.
            .filter(member -> !member.isAgent())
            .orElseThrow(() -> new ResourceNotFoundException(
                "No member has signed in under the subject '" + subject + "'"));
    }

    private Member provision(String subject, String displayName, String email) {
        Member member = insert(subject, displayName, email);

        // ⚠️ Here rather than in resolveMember, which runs on EVERY authenticated request. A member's own
        // file cabinet is made once in their lifetime, so asking for it per request would be an indexed
        // read on the hot path to discover, almost always, that there was nothing to do. Whoever predates
        // this line is given one by FileRootSeedStep instead.
        memberFileTrees.cabinetOf(member);

        return member;
    }

    private Member insert(String subject, String displayName, String email) {
        try {
            // Insert in its own transaction (REQUIRES_NEW) so a losing racer's constraint violation
            // rolls back only that insert, not our transaction.
            return memberProvisioner.provision(subject, displayName, email);
        } catch (DataIntegrityViolationException concurrentProvision) {
            // Another request provisioned the same subject between our find and our save. The unique
            // constraint on `subject` is the source of truth — fall back to the winner's committed row.
            //
            // ⚠️ READ IN A NEW TRANSACTION, not in this one. Under MySQL's REPEATABLE READ this
            // transaction's snapshot predates the winner's commit, so `memberRepository.findBySubject`
            // here answers EMPTY for a row whose very existence is what just failed the insert — and the
            // exception this catch exists to swallow gets rethrown. Two parallel first requests are all
            // it takes; one screen opening with two queries is two parallel requests.
            return memberProvisioner.findCommitted(subject)
                .orElseThrow(() -> concurrentProvision);
        }
    }

    private Member refreshCachedClaims(Member member, String displayName, String email) {
        boolean changed = !Objects.equals(member.getDisplayName(), displayName)
            || !Objects.equals(member.getEmail(), email);

        if (changed) {
            member.setDisplayName(displayName);
            member.setEmail(email);
        }

        return member;
    }

    /**
     * A human-facing name from whatever the token offers, most-specific first. Identity puts the
     * login email in {@code sub}; {@code name}/{@code preferred_username} may or may not be present.
     */
    private String displayNameFrom(Jwt jwt) {
        return firstPresent(
            jwt.getClaimAsString("name"),
            jwt.getClaimAsString("preferred_username"),
            jwt.getClaimAsString("email"),
            jwt.getSubject());
    }

    private String emailFrom(Jwt jwt) {
        String email = jwt.getClaimAsString("email");

        if (email != null) {
            return email;
        }

        String subject = jwt.getSubject();
        return subject != null && subject.contains("@") ? subject : null;
    }

    private String firstPresent(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }

        return null;
    }

}
