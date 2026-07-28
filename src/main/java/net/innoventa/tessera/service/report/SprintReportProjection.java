package net.innoventa.tessera.service.report;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The reporting core (ADR-0013): a sprint's burndown series, its three report buckets and its headline
 * totals, derived in one pass from the membership rows ordinary use already wrote. <strong>Pure</strong>
 * — no repositories, no clock, no I/O of any kind. Everything time-dependent arrives in
 * {@link SprintWindow#closedAt()}, which is why a running sprint and a finished one need no separate
 * code path, and why the awkward cases are rows of test data rather than scenarios wound forward through
 * HTTP.
 * <p>
 * It is deliberately the same shape of seam as Phase-2's {@code BoardColumnResolver}: a stateless
 * component whose caller supplies already-loaded inputs, so it can never be the reason a report costs a
 * query per issue.
 * <p>
 * Two conventions run through the whole thing and are worth stating once:
 * <ul>
 *   <li><strong>Everything is measured at the end of a day</strong>, or at the close if the sprint
 *       stopped earlier. "Remaining on Tuesday" is what was still open when Tuesday finished.</li>
 *   <li><strong>Open means {@code resolution IS NULL}</strong> (ADR-0004), read here through
 *       {@code resolvedAt} (ADR-0011) — never a status name, and never a status category.</li>
 * </ul>
 */
@Component
public class SprintReportProjection {

    /**
     * @param window  the sprint's span; all three of its fields are required
     * @param members every membership row of the sprint — including the ones already removed, which the
     *                report needs and the board does not
     */
    public SprintProjection project(SprintWindow window, List<SprintMemberFact> members) {
        List<SprintMemberFact> withinWindow = members.stream()
            .filter(member -> wasMemberDuring(member, window))
            .toList();

        List<SprintMemberFact> committed = withinWindow.stream()
            .filter(member -> wasMemberAt(member, window.startedAt()))
            .toList();

        List<SprintMemberFact> removed = new ArrayList<>();
        List<SprintMemberFact> completed = new ArrayList<>();
        List<SprintMemberFact> incomplete = new ArrayList<>();

        for (SprintMemberFact member : withinWindow) {
            if (leftBefore(member, window.closedAt())) {
                removed.add(member);
            } else if (resolvedBy(member, window.closedAt())) {
                completed.add(member);
            } else {
                incomplete.add(member);
            }
        }

        return new SprintProjection(
            burndown(window, withinWindow, totalPoints(committed)),
            List.copyOf(completed),
            List.copyOf(incomplete),
            List.copyOf(removed),
            committed.size(),
            totalPoints(committed),
            completed.size(),
            totalPoints(completed)
        );
    }

    /**
     * One point per calendar day of the sprint's planned window. Days the sprint never reached carry a
     * null {@code remaining} but keep their ideal and scope, so an early close shortens the line without
     * shortening the axis.
     */
    private List<BurndownPoint> burndown(
        SprintWindow window,
        List<SprintMemberFact> members,
        double committedPoints
    ) {
        List<LocalDate> days = window.startedAt().toLocalDate()
            .datesUntil(window.endDate().plusDays(1))
            .toList();

        List<BurndownPoint> points = new ArrayList<>(days.size());

        for (int index = 0; index < days.size(); index++) {
            LocalDate day = days.get(index);
            LocalDateTime endOfDay = day.plusDays(1).atStartOfDay();
            // A day still in progress — the last one of a running sprint — is measured as far as the
            // sprint has actually got, rather than being projected to a midnight that has not happened.
            LocalDateTime measuredAt = endOfDay.isAfter(window.closedAt()) ? window.closedAt() : endOfDay;
            boolean reached = !day.atStartOfDay().isAfter(window.closedAt());

            List<SprintMemberFact> onThatDay = members.stream()
                .filter(member -> wasMemberAt(member, measuredAt))
                .toList();

            Double remaining = reached
                ? totalPoints(onThatDay.stream().filter(member -> !resolvedBy(member, measuredAt)).toList())
                : null;

            points.add(new BurndownPoint(day, remaining, ideal(committedPoints, index, days.size()), totalPoints(onThatDay)));
        }

        return points;
    }

    /**
     * The straight line from the committed total down to zero on the end date. Weekends are not
     * special-cased: a five-day line across a week containing a weekend is what the team agreed to, and
     * a stepped "ideal" would only encode one team's working calendar as if it were everyone's.
     * <p>
     * A single-day sprint has one bucket, measured at its end — where the ideal is zero.
     */
    private double ideal(double committedPoints, int index, int days) {
        if (days <= 1) {
            return 0.0;
        }

        return committedPoints * (days - 1 - index) / (days - 1);
    }

    /** Was this row a live membership at {@code moment}? */
    private boolean wasMemberAt(SprintMemberFact member, LocalDateTime moment) {
        boolean joined = !member.addedAt().isAfter(moment);
        boolean stillThere = member.removedAt() == null || member.removedAt().isAfter(moment);

        return joined && stillThere;
    }

    /**
     * Did this row ever count as part of the sprint? A membership that had already ended before the
     * sprint started belongs to the planning that preceded it, not to the sprint, and appears in no
     * bucket and on no day.
     */
    private boolean wasMemberDuring(SprintMemberFact member, SprintWindow window) {
        boolean joinedInTime = !member.addedAt().isAfter(window.closedAt());
        boolean leftAfterTheStart = member.removedAt() == null || member.removedAt().isAfter(window.startedAt());

        return joinedInTime && leftAfterTheStart;
    }

    private boolean leftBefore(SprintMemberFact member, LocalDateTime moment) {
        return member.removedAt() != null && !member.removedAt().isAfter(moment);
    }

    private boolean resolvedBy(SprintMemberFact member, LocalDateTime moment) {
        return member.resolvedAt() != null && !member.resolvedAt().isAfter(moment);
    }

    private double totalPoints(List<SprintMemberFact> members) {
        return members.stream().mapToDouble(SprintMemberFact::points).sum();
    }

}
