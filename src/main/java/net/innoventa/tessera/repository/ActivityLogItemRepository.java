package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.ActivityLogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogItemRepository extends JpaRepository<ActivityLogItem, String> {

    List<ActivityLogItem> findByActivityLogIdIn(List<String> activityLogIds);

    /**
     * How many issues entered each status inside the window — the dashboard's movement chart.
     *
     * <h2>⚠️ This counts MOVES, not issues</h2>
     *
     * <p>An issue that went to review, came back and went again is two entries into review, and that is
     * the honest answer to "what moved this week". Counting distinct issues instead would quietly turn
     * a chart about throughput into a chart about standing, which the board already shows.
     *
     * <h2>⚠️ The bucket is the status NAME, because that is what the log stores</h2>
     *
     * <p>{@code TransitionService} records the transition as {@code compare("status", oldName, newName)}
     * — a name, deliberately: the history has to stay readable when a status is deleted, and an
     * identifier in a log nothing can resolve reads as a blank. The cost lands here: rename a status and
     * this splits its week in two, under the old name and the new. Nothing can repair that from the log
     * alone, so {@code DashboardService} resolves what it can against the catalogue and leaves the rest
     * named but uncategorised rather than dropping it.
     *
     * <p>Three entities joined on their identifiers rather than through associations, as everywhere in
     * this schema: an item names its log, a log names its issue, an issue names its project — and the
     * project is what confines the answer to what the caller may see.
     */
    @Query("select new net.innoventa.tessera.repository.CountByKey(item.newValue, count(item)) "
           + "from ActivityLogItem item "
           + "join ActivityLog log on log.id = item.activityLogId "
           + "join Issue issue on issue.id = log.issueId "
           + "where item.field = 'status' "
           + "  and item.newValue is not null "
           + "  and log.createdAt >= :from "
           + "  and issue.projectId in :projectIds "
           + "group by item.newValue")
    List<CountByKey> countMovesIntoStatusSince(
        @Param("projectIds") List<String> projectIds, @Param("from") LocalDateTime from);


    /**
     * When each open issue last changed status — where the ageing clock starts.
     *
     * <p>⚠️ <strong>Joined through the issue rather than taking a list of identifiers.</strong> The
     * population is "every open issue the caller may browse", which on a real installation is hundreds;
     * an {@code in} clause of that many identifiers is a query nobody can read and some drivers refuse.
     * The three conditions here are the same three the issue query applies, stated once more because
     * this must not silently include a resolved issue whose clock stopped.
     *
     * <p>⚠️ An issue with no row here has never moved. That is a real answer rather than a gap — see
     * {@link LastStatusChange} — and it is exactly the set that has been still the longest.
     */
    @Query("select new net.innoventa.tessera.repository.LastStatusChange(log.issueId, max(log.createdAt)) "
           + "from ActivityLogItem item "
           + "join ActivityLog log on log.id = item.activityLogId "
           + "join Issue issue on issue.id = log.issueId "
           + "where item.field = 'status' "
           + "  and issue.projectId in :projectIds "
           + "  and issue.resolutionId is null "
           + "  and issue.archivedAt is null "
           + "group by log.issueId")
    List<LastStatusChange> lastStatusChangePerOpenIssue(@Param("projectIds") List<String> projectIds);

}
