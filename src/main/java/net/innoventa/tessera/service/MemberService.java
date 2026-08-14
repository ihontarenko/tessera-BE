package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.CurrentMemberResponse;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.security.access.InstallationAccess;
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

    @Transactional(readOnly = true)
    public List<MemberSummary> search(String query) {
        List<Member> members = (query == null || query.isBlank())
            ? memberRepository.findAllByOrderByDisplayNameAsc()
            : memberRepository.findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrSubjectContainingIgnoreCaseOrderByDisplayNameAsc(
                query, query, query);

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
            .orElseThrow(() -> new ResourceNotFoundException(
                "No member has signed in under the subject '" + subject + "'"));
    }

    private Member provision(String subject, String displayName, String email) {
        try {
            // Insert in its own transaction (REQUIRES_NEW) so a losing racer's constraint violation
            // rolls back only that insert, not our transaction.
            return memberProvisioner.provision(subject, displayName, email);
        } catch (DataIntegrityViolationException concurrentProvision) {
            // Another request provisioned the same subject between our find and our save. The unique
            // constraint on `subject` is the source of truth — fall back to the winner's committed row.
            return memberRepository.findBySubject(subject)
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
