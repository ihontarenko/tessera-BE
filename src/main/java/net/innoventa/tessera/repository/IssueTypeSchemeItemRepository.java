package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueTypeSchemeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueTypeSchemeItemRepository extends JpaRepository<IssueTypeSchemeItem, String> {

    List<IssueTypeSchemeItem> findBySchemeIdOrderBySequenceAsc(String schemeId);

    List<IssueTypeSchemeItem> findBySchemeIdInOrderBySequenceAsc(List<String> schemeIds);

    /** Every scheme granting this issue type — what refuses its deletion, and the "granted by" panel. */
    List<IssueTypeSchemeItem> findByIssueTypeId(String issueTypeId);

}
