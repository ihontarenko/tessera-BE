package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueTypeScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueTypeSchemeRepository extends JpaRepository<IssueTypeScheme, String> {

    List<IssueTypeScheme> findAllByOrderByNameAsc();

}
