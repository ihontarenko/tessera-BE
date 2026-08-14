package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueTypeScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueTypeSchemeRepository extends JpaRepository<IssueTypeScheme, String> {

    List<IssueTypeScheme> findAllByOrderByNameAsc();

    /** Every scheme that preselects this issue type — part of what refuses the type's deletion. */
    List<IssueTypeScheme> findByDefaultIssueTypeId(String issueTypeId);

}
