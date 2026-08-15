package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.SystemRole;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.security.Roles;
import net.innoventa.tessera.security.access.MemberHandles;
import net.innoventa.tessera.security.access.Targets;
import org.jmouse.access.jpa.AccessAdministration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Inserts a brand-new {@link Member} in its <strong>own</strong> transaction so that a unique-constraint
 * collision under concurrent first calls rolls back only this insert — leaving the caller's transaction
 * intact to recover by re-reading the winner's row. A same-transaction catch-and-refind cannot do this:
 * once a constraint violation fires, Spring marks the surrounding transaction rollback-only and any
 * further query in it fails. Kept a separate bean (not a self-invoked method) so {@code REQUIRES_NEW}
 * actually goes through the proxy.
 *
 * <h2>⚠️ It is also where an installation's way back in is handed out</h2>
 *
 * <p>Authorization has a chicken-and-egg at its root: somebody needs rights before anybody can be
 * granted rights. Innoventa answers it in its policy document, {@code assign subject ${…owner}}, because
 * it seeds a superuser row in a migration and therefore knows an identifier before anybody signs in.
 * Tessera's members arrive from Identity on first sign-in, so at the moment the document is read there
 * is no identifier to name — a placeholder there would be a grant conferring nothing, quietly.
 *
 * <p>So the <strong>first member ever provisioned</strong> receives
 * {@code GLOBAL_ACCESS_ADMINISTRATOR}. It is a row like any other: it says who and when, it can be
 * handed on, and it can be taken away — unlike a grant a file keeps re-asserting.
 */
@Component
@RequiredArgsConstructor
public class MemberProvisioner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberProvisioner.class);

    private final MemberRepository      memberRepository;
    private final AccessAdministration  access;
    private final MemberHandles         handles;
    private final Supplier<String>      idGenerator;

    /**
     * Who this installation's access administrator is, as a handle a person can write down before
     * anybody has signed in — a member id, an identity-provider subject, or an email.
     *
     * <p>The same property {@code policy/tessera.jmp} names, so the document and this cannot disagree
     * about who it is. Unset means "whoever arrives first", which is right for a machine somebody is
     * trying out and wrong for anything else.
     */
    @Value("${tessera.bootstrap.owner:}")
    private String owner;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member provision(String subject, String displayName, String email) {
        boolean theFirst = memberRepository.count() == 0;

        Member member = memberRepository.save(Member.builder()
            .id(idGenerator.get())
            .subject(subject)
            .displayName(displayName)
            .email(email)
            .systemRole(SystemRole.USER)
            .build());

        if (isTheOwner(member, theFirst)) {
            grantTheWayBackIn(member);
        }

        return member;
    }

    /**
     * The row a concurrent first call has just committed, read where it can actually be seen.
     *
     * <p>⚠️ <strong>A new transaction, and that is the whole of this method.</strong> MySQL's default
     * isolation is {@code REPEATABLE READ}: the losing racer's transaction took its snapshot before the
     * winner committed, so re-reading inside it finds <em>nothing</em> — the row provably exists, the
     * insert failed <em>because</em> it exists, and the recovery query cannot see it. That is how a
     * duplicate-key error escapes a catch block written specifically to swallow it, and what it looks
     * like from outside is a {@code 500} on somebody's first page load with no second attempt to blame.
     *
     * <p>Two parallel first requests are not exotic: one screen opening with two queries is enough, which
     * is exactly how this surfaced. ⚠️ The row comes back <strong>detached</strong> — it belongs to this
     * transaction, which is over — so it is a set of values to read, not an entity to modify. Every caller
     * on this path only reads it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Member> findCommitted(String subject) {
        return memberRepository.findBySubject(subject);
    }

    /**
     * Whether this brand-new member is the one who gets the way back in.
     *
     * <p><strong>One rule with two trigger points, not two rules.</strong> The rule is
     * <em>{@code tessera.bootstrap.owner} holds it</em>. {@code PolicySeedStep} applies it when that
     * member already exists at seed time; this applies it when they sign in afterwards — which is the
     * ordinary case, because members arrive from Identity and the seed runs at the first start.
     *
     * <p>⚠️ <strong>The first-member fallback is only for an installation that configured no owner.</strong>
     * With an owner named, first-in-the-door must NOT get it: on a fresh installation the first person
     * to sign in is whoever tried the URL, and handing them the access screen because they were quick is
     * not a way in — it is a race.
     */
    private boolean isTheOwner(Member member, boolean theFirst) {
        if (owner == null || owner.isBlank()) {
            return theFirst;
        }

        return handles.names(member, owner);
    }

    /**
     * ⚠️ <strong>Deliberately not a superuser.</strong> It opens no project, reads no issue and
     * transitions nothing. Everything the tracker actually <em>does</em> still comes from a project role
     * somebody can edit and take away.
     *
     * <p>What it carries is the two installation-wide jobs: editing the roles every project shares — all
     * anybody needs to hand the powers back out — and editing the catalogs every project runs on. The
     * second is a widening, and {@code policy/tessera.jmp} says why it lives here rather than in a role
     * of its own: nothing in this product assigns a global role from a screen, so a second one would sit
     * in the catalog held by nobody.
     *
     * <p>Inside {@code REQUIRES_NEW}, so the member and their grant commit together: a first member with
     * no way into the access screen is an installation nobody can administer without a database session.
     */
    private void grantTheWayBackIn(Member member) {
        access.assign(
            member.getId(),
            Roles.GLOBAL_ACCESS_ADMINISTRATOR,
            Targets.installationScope(),
            "BOOTSTRAP",
            "member-provisioner",
            null);

        LOGGER.info("'{}' is the first member this installation has ever seen and now holds {} — the "
                    + "way back in. Hand it on or take it away through the access screen; it is an "
                    + "ordinary row.",
            member.getEmail() == null ? member.getId() : member.getEmail(),
            Roles.GLOBAL_ACCESS_ADMINISTRATOR);
    }

}
