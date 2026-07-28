package net.innoventa.tessera.service.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one new pure seam of Phase 3 (ADR-0013), and the direct analogue of Phase-2's
 * {@code BoardColumnResolver}: every awkward case a sprint report has to survive is a
 * <strong>row of input data</strong> here rather than a multi-step scenario driven through HTTP with a
 * wound-forward clock. That is precisely why the projection performs no I/O.
 * <p>
 * The window throughout is a five-day sprint, Mar 2 → Mar 6 2026, started at 09:00 on the first day and
 * closed at 17:00 on the last. Two issues are committed at the start: {@code A} worth 5 points, resolved
 * on the second day, and {@code B} worth 3, never resolved.
 */
class SprintReportProjectionTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 3, 2, 9, 0);
    private static final LocalDate END_DATE = LocalDate.of(2026, 3, 6);
    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 3, 6, 17, 0);

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 3, 2);
    private static final LocalDate DAY_TWO = LocalDate.of(2026, 3, 3);
    private static final LocalDate DAY_THREE = LocalDate.of(2026, 3, 4);
    private static final LocalDate DAY_FOUR = LocalDate.of(2026, 3, 5);
    private static final LocalDate DAY_FIVE = LocalDate.of(2026, 3, 6);

    private static final SprintMemberFact COMPLETED_FIVE_POINTER =
        new SprintMemberFact("A", STARTED_AT, null, 5.0, LocalDateTime.of(2026, 3, 3, 14, 0));
    private static final SprintMemberFact OPEN_THREE_POINTER =
        new SprintMemberFact("B", STARTED_AT, null, 3.0, null);

    private final SprintReportProjection projection = new SprintReportProjection();

    private SprintProjection project(SprintMemberFact... members) {
        return projection.project(new SprintWindow(STARTED_AT, END_DATE, CLOSED_AT), List.of(members));
    }

    @Nested
    @DisplayName("the burndown series")
    class Burndown {

        @Test
        @DisplayName("buckets one point per calendar day from the start to the end date, weekends included")
        void bucketsEveryCalendarDay() {
            SprintProjection result = project(COMPLETED_FIVE_POINTER, OPEN_THREE_POINTER);

            assertThat(result.burndown())
                .extracting(BurndownPoint::date)
                .containsExactly(DAY_ONE, DAY_TWO, DAY_THREE, DAY_FOUR, DAY_FIVE);
        }

        @Test
        @DisplayName("counts a day's remaining work at the end of that day, at the frozen estimate")
        void remainingFallsAsWorkIsResolved() {
            SprintProjection result = project(COMPLETED_FIVE_POINTER, OPEN_THREE_POINTER);

            // A is resolved during day two, so it leaves the line from day two's close onwards.
            assertThat(result.burndown())
                .extracting(BurndownPoint::remaining)
                .containsExactly(8.0, 3.0, 3.0, 3.0, 3.0);
        }

        @Test
        @DisplayName("runs the ideal line straight from the committed total to zero at the end date")
        void idealRunsFromCommittedToZero() {
            SprintProjection result = project(COMPLETED_FIVE_POINTER, OPEN_THREE_POINTER);

            assertThat(result.burndown())
                .extracting(BurndownPoint::ideal)
                .containsExactly(8.0, 6.0, 4.0, 2.0, 0.0);
        }

        @Test
        @DisplayName("tracks scope separately, so a sprint that grew is not a team that slowed down")
        void scopeTracksTheCommittedTotalPerDay() {
            SprintMemberFact pulledInOnDayThree =
                new SprintMemberFact("C", LocalDateTime.of(2026, 3, 4, 10, 0), null, 2.0, null);

            SprintProjection result = project(COMPLETED_FIVE_POINTER, OPEN_THREE_POINTER, pulledInOnDayThree);

            assertThat(result.burndown()).extracting(BurndownPoint::scope).containsExactly(8.0, 8.0, 10.0, 10.0, 10.0);
            assertThat(result.burndown()).extracting(BurndownPoint::remaining).containsExactly(8.0, 3.0, 5.0, 5.0, 5.0);
            // The ideal line is drawn against what was committed at the start, not against what the
            // sprint grew into — otherwise scope creep would quietly move the goalposts with it.
            assertThat(result.committedPoints()).isEqualTo(8.0);
        }

        @Test
        @DisplayName("de-scoping an issue drops it out of both lines from the day it left")
        void removingAnIssueDropsItFromTheLines() {
            SprintMemberFact droppedOnDayThree = new SprintMemberFact(
                "C", STARTED_AT, LocalDateTime.of(2026, 3, 4, 10, 0), 4.0, null);

            SprintProjection result = project(OPEN_THREE_POINTER, droppedOnDayThree);

            assertThat(result.burndown()).extracting(BurndownPoint::scope).containsExactly(7.0, 7.0, 3.0, 3.0, 3.0);
            assertThat(result.burndown()).extracting(BurndownPoint::remaining).containsExactly(7.0, 7.0, 3.0, 3.0, 3.0);
        }

        @Test
        @DisplayName("counts a re-added issue only over its latest stint, which is all the row records")
        void aReAddedIssueCountsFromItsLatestStint() {
            // Re-adding revives the row: `addedAt` is reset to the new act and `removedAt` cleared, so
            // the earlier stint is genuinely not in the data — the accepted cost recorded in ADR-0013.
            SprintMemberFact removedThenReAdded =
                new SprintMemberFact("C", LocalDateTime.of(2026, 3, 5, 10, 0), null, 2.0, null);

            SprintProjection result = project(OPEN_THREE_POINTER, removedThenReAdded);

            assertThat(result.burndown()).extracting(BurndownPoint::scope).containsExactly(3.0, 3.0, 3.0, 5.0, 5.0);
        }

        @Test
        @DisplayName("never counts an issue resolved before the sprint even started as remaining work")
        void workResolvedBeforeTheStartIsNeverRemaining() {
            SprintMemberFact resolvedBeforehand = new SprintMemberFact(
                "C", STARTED_AT, null, 4.0, LocalDateTime.of(2026, 2, 27, 11, 0));

            SprintProjection result = project(OPEN_THREE_POINTER, resolvedBeforehand);

            assertThat(result.burndown()).extracting(BurndownPoint::scope).containsExactly(7.0, 7.0, 7.0, 7.0, 7.0);
            assertThat(result.burndown()).extracting(BurndownPoint::remaining).containsExactly(3.0, 3.0, 3.0, 3.0, 3.0);
            assertThat(result.completed()).extracting(SprintMemberFact::issueId).containsExactly("C");
        }

        @Test
        @DisplayName("counts an unestimated issue as an issue worth zero points, never as an absence")
        void anUnestimatedIssueIsWorthZeroPoints() {
            SprintMemberFact unestimated = new SprintMemberFact("C", STARTED_AT, null, null, null);

            SprintProjection result = project(OPEN_THREE_POINTER, unestimated);

            assertThat(result.burndown()).extracting(BurndownPoint::remaining).containsExactly(3.0, 3.0, 3.0, 3.0, 3.0);
            assertThat(result.committedIssues()).isEqualTo(2);
            assertThat(result.committedPoints()).isEqualTo(3.0);
            assertThat(result.incomplete()).extracting(SprintMemberFact::issueId).containsExactly("B", "C");
        }

        @Test
        @DisplayName("stops the remaining line where a sprint closed early, and draws the axis out in full")
        void aSprintClosedEarlyStopsTheRemainingLine() {
            LocalDateTime closedOnDayThree = LocalDateTime.of(2026, 3, 4, 12, 0);

            SprintProjection result = projection.project(
                new SprintWindow(STARTED_AT, END_DATE, closedOnDayThree),
                List.of(COMPLETED_FIVE_POINTER, OPEN_THREE_POINTER));

            // Days after the close hold no measurement — a flat tail would read as a team that stalled.
            assertThat(result.burndown())
                .extracting(BurndownPoint::remaining)
                .containsExactly(8.0, 3.0, 3.0, null, null);
            // The axis is the sprint's planned window either way, so the ideal still lands on zero.
            assertThat(result.burndown()).extracting(BurndownPoint::ideal).containsExactly(8.0, 6.0, 4.0, 2.0, 0.0);
            assertThat(result.burndown()).extracting(BurndownPoint::scope).containsExactly(8.0, 8.0, 8.0, 8.0, 8.0);
        }

        @Test
        @DisplayName("collapses to a single end-of-day bucket for a one-day sprint")
        void aOneDaySprintHasOneBucket() {
            SprintProjection result = projection.project(
                new SprintWindow(STARTED_AT, DAY_ONE, LocalDateTime.of(2026, 3, 2, 18, 0)),
                List.of(OPEN_THREE_POINTER));

            assertThat(result.burndown()).singleElement().satisfies(point -> {
                assertThat(point.date()).isEqualTo(DAY_ONE);
                assertThat(point.remaining()).isEqualTo(3.0);
                assertThat(point.ideal()).isEqualTo(0.0);
            });
        }

    }

    @Nested
    @DisplayName("the report's buckets")
    class Buckets {

        @Test
        @DisplayName("splits members into completed, incomplete and removed, disjointly")
        void splitsMembersIntoThreeBuckets() {
            SprintMemberFact droppedMidSprint = new SprintMemberFact(
                "C", STARTED_AT, LocalDateTime.of(2026, 3, 4, 10, 0), 4.0, null);

            SprintProjection result = project(COMPLETED_FIVE_POINTER, OPEN_THREE_POINTER, droppedMidSprint);

            assertThat(result.completed()).extracting(SprintMemberFact::issueId).containsExactly("A");
            assertThat(result.incomplete()).extracting(SprintMemberFact::issueId).containsExactly("B");
            assertThat(result.removed()).extracting(SprintMemberFact::issueId).containsExactly("C");
        }

        @Test
        @DisplayName("counts an issue pulled in mid-sprint and finished as completed, but never as committed")
        void midSprintWorkCountsAsCompletedNotCommitted() {
            SprintMemberFact pulledInAndFinished = new SprintMemberFact(
                "C",
                LocalDateTime.of(2026, 3, 4, 10, 0),
                null,
                2.0,
                LocalDateTime.of(2026, 3, 5, 16, 0));

            SprintProjection result = project(COMPLETED_FIVE_POINTER, OPEN_THREE_POINTER, pulledInAndFinished);

            assertThat(result.committedIssues()).isEqualTo(2);
            assertThat(result.committedPoints()).isEqualTo(8.0);
            assertThat(result.completedIssues()).isEqualTo(2);
            assertThat(result.completedPoints()).isEqualTo(7.0);
        }

        @Test
        @DisplayName("leaves an issue that had already left before the sprint started out of every bucket")
        void workRemovedBeforeTheStartIsInNoBucket() {
            SprintMemberFact leftWhileStillPlanned = new SprintMemberFact(
                "C",
                LocalDateTime.of(2026, 2, 20, 9, 0),
                LocalDateTime.of(2026, 2, 28, 9, 0),
                4.0,
                null);

            SprintProjection result = project(OPEN_THREE_POINTER, leftWhileStillPlanned);

            assertThat(result.completed()).isEmpty();
            assertThat(result.removed()).isEmpty();
            assertThat(result.incomplete()).extracting(SprintMemberFact::issueId).containsExactly("B");
            assertThat(result.committedIssues()).isEqualTo(1);
        }

        @Test
        @DisplayName("counts an issue resolved after the sprint closed as incomplete, not completed")
        void workResolvedAfterTheCloseIsIncomplete() {
            SprintMemberFact resolvedTooLate = new SprintMemberFact(
                "C", STARTED_AT, null, 4.0, LocalDateTime.of(2026, 3, 9, 11, 0));

            SprintProjection result = project(OPEN_THREE_POINTER, resolvedTooLate);

            assertThat(result.incomplete()).extracting(SprintMemberFact::issueId).containsExactly("B", "C");
            assertThat(result.completed()).isEmpty();
        }

    }

}
