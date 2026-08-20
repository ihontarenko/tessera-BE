package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.MemberKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String> {

    Optional<Member> findBySubject(String subject);

    /**
     * ⚠️ Read only by {@link net.innoventa.tessera.security.access.MemberHandles}, and only as its last
     * attempt. The column is nullable and carries no unique constraint — a token without an email claim
     * leaves it null — so this is a convenience for naming a person in a policy document, never an
     * identity. Anything deciding who somebody <em>is</em> goes through {@code subject}.
     */
    Optional<Member> findByEmail(String email);

    /**
     * The whole directory, and ⚠️ <strong>people only</strong> (TSSR-33).
     *
     * <p>An agent is a member so that authorship is one reference with one face; it is not a member in
     * the sense a picker, an invite or a mention means. Decided 2026-08-17 — which is what makes this a
     * hard filter rather than a preference.
     */
    List<Member> findAllByKindOrderByDisplayNameAsc(MemberKind kind);

    /**
     * The people picker's search — one fragment against every name a person might be recognised by.
     *
     * <p>⚠️ <strong>Written out rather than derived, and the reason is that the derived name was
     * unreadable.</strong> Spring Data would spell this
     * {@code findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrSubjectContainingIgnoreCaseOrderByDisplayNameAsc},
     * which is a sentence pretending to be an identifier: it takes three parameters that are always the
     * same value, and adding a fourth column would make it longer still. A query says the same thing in
     * a form somebody can read, and takes the fragment once.
     *
     * <p>{@code lower(…)} on both sides rather than {@code ILIKE} or a collation: MySQL matches
     * case-insensitively by default and PostgreSQL does not, so leaning on either would give the two
     * databases different answers to the same search.
     */
    @Query("""
        SELECT member FROM Member member
        WHERE member.kind = net.innoventa.tessera.domain.MemberKind.PERSON
          AND (lower(member.displayName) LIKE lower(concat('%', :fragment, '%'))
            OR lower(member.email)       LIKE lower(concat('%', :fragment, '%'))
            OR lower(member.subject)     LIKE lower(concat('%', :fragment, '%')))
        ORDER BY member.displayName ASC
        """)
    List<Member> search(@Param("fragment") String fragment);

    /**
     * Members of one kind, or of every kind — the administration screen (TSSR-79).
     *
     * <h2>⚠️ A second query rather than a widened {@link #search}</h2>
     *
     * <p>{@code search} excludes agents deliberately (TSSR-33): an agent is a member so that authorship
     * has one face, not so that it can be invited, and every caller of that method is a picker, an
     * invite or a mention. Widening it to serve one screen would offer a client in a dozen places where
     * one cannot be chosen — each of which would then have to refuse it.
     *
     * <p>The administration screen is the one caller that legitimately wants both, so it asks its own
     * question. ⚠️ A retired client is <strong>included</strong>: it is exactly the row somebody comes
     * to this screen to understand, and hiding it would make what it wrote look like it came from
     * nobody.
     *
     * @param kind     the kind to return, or {@code null} for every kind
     * @param fragment a fragment of name, email or subject, or {@code null} for no filtering
     */
    @Query("""
        SELECT member FROM Member member
        WHERE (:kind IS NULL OR member.kind = :kind)
          AND (:fragment IS NULL
            OR lower(member.displayName) LIKE lower(concat('%', :fragment, '%'))
            OR lower(member.email)       LIKE lower(concat('%', :fragment, '%'))
            OR lower(member.subject)     LIKE lower(concat('%', :fragment, '%')))
        ORDER BY member.kind ASC, member.displayName ASC
        """)
    List<Member> administered(@Param("kind") MemberKind kind, @Param("fragment") String fragment);

    // ── Agents (TSSR-32, TSSR-33) ────────────────────────────────────────────────

    /**
     * One agent's mirror.
     *
     * <p>⚠️ <strong>The kind is part of the lookup, not a check afterwards.</strong> An identifier
     * arriving in a token's {@code aid} claim is a string, and a member identifier that happened to name
     * a <em>person</em> would otherwise resolve — attributing a comment to somebody who never wrote it.
     */
    Optional<Member> findByIdAndKind(String id, MemberKind kind);

    /** Every agent belonging to one person — what "mine" widens to (TSSR-35). */
    List<Member> findByKindAndParentId(MemberKind kind, String parentId);

    /**
     * How many rows of one kind there are.
     *
     * <p>⚠️ Exists because {@code MemberProvisioner} decides who gets the installation's way back in by
     * asking whether anybody came before them. That count has to mean <strong>people</strong>: an agent
     * provisioned first would otherwise answer a question about who arrived, and the answer decides who
     * administers the installation.
     */
    long countByKind(MemberKind kind);

}
