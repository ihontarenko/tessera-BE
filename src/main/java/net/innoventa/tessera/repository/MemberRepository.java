package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Member;
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

    List<Member> findAllByOrderByDisplayNameAsc();

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
        WHERE lower(member.displayName) LIKE lower(concat('%', :fragment, '%'))
           OR lower(member.email)       LIKE lower(concat('%', :fragment, '%'))
           OR lower(member.subject)     LIKE lower(concat('%', :fragment, '%'))
        ORDER BY member.displayName ASC
        """)
    List<Member> search(@Param("fragment") String fragment);

}
