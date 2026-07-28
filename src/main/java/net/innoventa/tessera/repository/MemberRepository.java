package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String> {

    Optional<Member> findBySubject(String subject);

    List<Member> findAllByOrderByDisplayNameAsc();

    List<Member> findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrSubjectContainingIgnoreCaseOrderByDisplayNameAsc(
        String displayNameFragment, String emailFragment, String subjectFragment);

}
