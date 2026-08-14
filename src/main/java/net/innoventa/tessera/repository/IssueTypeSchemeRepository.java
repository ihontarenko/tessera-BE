package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueTypeScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueTypeSchemeRepository extends JpaRepository<IssueTypeScheme, String> {

    List<IssueTypeScheme> findAllByOrderByNameAsc();

    /** Name collisions are a 409 with words rather than the unique constraint's 500 — see SchemeRules. */
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String schemeId);

    /** Every scheme that preselects this issue type — part of what refuses the type's deletion. */
    List<IssueTypeScheme> findByDefaultIssueTypeId(String issueTypeId);

}
