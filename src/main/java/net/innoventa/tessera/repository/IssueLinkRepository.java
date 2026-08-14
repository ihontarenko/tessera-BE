package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.IssueLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface IssueLinkRepository extends JpaRepository<IssueLink, String> {

    List<IssueLink> findBySourceIssueId(String sourceIssueId);

    List<IssueLink> findByTargetIssueId(String targetIssueId);

    /**
     * Both halves of a slice's links in one query — the board's filter view needs the outward and the
     * inward end of every row, because {@code is blocked} and {@code is blocks(key)} are the same
     * record read from opposite sides.
     */
    List<IssueLink> findBySourceIssueIdInOrTargetIssueIdIn(
        Collection<String> sourceIssueIds,
        Collection<String> targetIssueIds
    );

    boolean existsBySourceIssueIdAndTargetIssueIdAndLinkTypeId(String sourceIssueId, String targetIssueId, String linkTypeId);

    long countByLinkTypeId(String linkTypeId);

    /** Every link type's usage in one query — the Administration page shows a count beside each. */
    @Query("select new net.innoventa.tessera.repository.CountByKey(link.linkTypeId, count(link)) "
           + "from IssueLink link group by link.linkTypeId")
    List<CountByKey> countLinksByLinkType();

}
