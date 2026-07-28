package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.SavedFilter;
import net.innoventa.tessera.domain.SavedFilterVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, String> {

    /**
     * What a member may see in a project: the shipped presets, everything the project shares, and their
     * own private ones. Written out rather than derived — the property-name form of this condition is
     * unreadable.
     * <p>
     * The presets are matched on visibility alone, with no project clause, which is what lets one
     * seeded row serve every project instead of being copied into each.
     */
    @Query("""
        SELECT savedFilter
          FROM SavedFilter savedFilter
         WHERE savedFilter.visibility = :globalVisibility
            OR (savedFilter.projectId = :projectId
                AND (savedFilter.visibility = :sharedVisibility OR savedFilter.ownerMemberId = :memberId))
         ORDER BY savedFilter.name ASC
        """)
    List<SavedFilter> findVisibleTo(
        @Param("projectId") String projectId,
        @Param("memberId") String memberId,
        @Param("sharedVisibility") SavedFilterVisibility sharedVisibility,
        @Param("globalVisibility") SavedFilterVisibility globalVisibility
    );

    boolean existsByProjectIdAndOwnerMemberIdAndName(String projectId, String ownerMemberId, String name);

}
